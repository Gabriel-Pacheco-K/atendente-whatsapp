package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.OwnerNotifier;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.SlotTakenException;
import com.gabrielpacheco.atendente.application.port.out.TransactionEvents;
import com.gabrielpacheco.atendente.application.port.out.UnknownBookingOutcomeException;
import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.booking.LastBooking;
import com.gabrielpacheco.atendente.domain.booking.PendingAttempt;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * O "sim" do resumo. O POST nunca é repetido: quando o resultado fica incerto, a agenda é conferida antes, e a
 * tentativa fica guardada no estado para o próximo "sim" saber que um 409 pode ser dela mesma.
 */
@Component
public class BookingConfirmation {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmation.class);

    private final BookingGaps gaps;
    private final BookingReconciler reconciler;
    private final SchedulingPort scheduling;
    private final BookingStore bookings;
    private final TransactionEvents transactionEvents;
    private final BusinessProfile business;
    private final ConversationSettings settings;

    private final OwnerNotifier owner;

    public BookingConfirmation(BookingGaps gaps, BookingReconciler reconciler, SchedulingPort scheduling,
                               BookingStore bookings, TransactionEvents transactionEvents, BusinessProfile business,
                               ConversationSettings settings, OwnerNotifier owner) {
        this.owner = owner;
        this.gaps = gaps;
        this.reconciler = reconciler;
        this.scheduling = scheduling;
        this.bookings = bookings;
        this.transactionEvents = transactionEvents;
        this.business = business;
        this.settings = settings;
    }

    public StepResult confirm(TurnContext t, ConfirmingBooking state) {
        BookingDraft draft = state.draft();
        Customer customer = t.customer();
        if (customer.name() == null || t.catalog().service(draft.serviceId()).isEmpty()) {
            return gaps.advance(t, draft, "");
        }
        Optional<StepResult> limit = limitReached(t);
        if (limit.isPresent()) {
            return limit.get();
        }
        SlotOption chosen = draft.chosen();
        NewAppointment attempt = new NewAppointment(chosen.professionalId(), draft.serviceId(), chosen.startAt(),
                customer.name(), customer.phone());
        try {
            AppointmentInfo info = scheduling.book(attempt);
            return booked(t, draft, info, bookedReply(info));
        } catch (SchedulingException e) {
            return afterFailedBooking(t, state, attempt, e);
        }
    }

    /**
     * A conversa expirou com uma tentativa incerta pendente. Como a tentativa é do próprio cliente, vale conferir
     * na agenda antes de recomeçar: se ela foi gravada, o cliente fica sabendo e o agendamento não fica órfão.
     */
    public Optional<StepResult> recoverExpired(TurnContext t, ConfirmingBooking stale) {
        if (checkPending(t, stale) instanceof Reconciliation.Found found) {
            AppointmentInfo info = found.appointment();
            return Optional.of(booked(t, stale.draft(), info, Texts.foundAfterRestart(info.ref()))
                    .withNote(StepResult.RECONCILED));
        }
        return Optional.empty();
    }

    /**
     * Depois de uma tentativa incerta, o cliente mandou outra coisa que não um "sim". Antes de seguir, a agenda diz se
     * a tentativa foi gravada: um "não" ou uma desistência não podem deixar um agendamento órfão nem ouvir "não
     * marquei" sem conferir. Vazio quando a tentativa não está na agenda: aí a mensagem segue o caminho normal.
     */
    public Optional<StepResult> settlePending(TurnContext t, ConfirmingBooking state) {
        return switch (checkPending(t, state)) {
            case Reconciliation.Found found -> Optional.of(booked(t, state.draft(), found.appointment(),
                    Texts.pendingFound(found.appointment().ref())).withNote(StepResult.RECONCILED));
            case Reconciliation.Unknown unknown ->
                    Optional.of(StepResult.of(state, Texts.pendingUnchecked(business.humanContactPhone())));
            case Reconciliation.NotFound notFound -> Optional.empty();
        };
    }

    /** Vale no início do agendamento e de novo no "sim", porque o cliente pode ter agendado por outra conversa. */
    public Optional<StepResult> limitReached(TurnContext t) {
        int active = bookings.countActiveUpcoming(t.customer().id(), t.localNow());
        if (active < settings.maxActivePerCustomer()) {
            return Optional.empty();
        }
        return Optional.of(StepResult.of(Idle.EMPTY, Texts.limitReached(active, business.humanContactPhone())));
    }

    private StepResult afterFailedBooking(TurnContext t, ConfirmingBooking state, NewAppointment attempt,
                                          SchedulingException failure) {
        BookingDraft draft = state.draft();
        return switch (failure) {
            case SlotTakenException e -> isRetryOfPending(state)
                    ? afterConflictWithPending(t, state, attempt)
                    : slotTaken(t, draft);
            case UnknownBookingOutcomeException e -> afterUncertainBooking(t, draft, attempt);
            case BookingRejectedException e -> StepResult.of(new ChoosingDate(draft.withoutDateAndTime()),
                    Texts.bookingRejected(e.detail()));
            case SchedulingUnavailableException e -> StepResult.of(state,
                    Texts.schedulingUnavailableConfirm(business.humanContactPhone()));
            case SchedulingContractException e -> unexpectedFailure(e);
            case AppointmentNotFoundException e -> unexpectedFailure(e);
        };
    }

    /**
     * Um 409 só pode ser a própria tentativa anterior quando o cliente repete o "sim" para o mesmo profissional e
     * horário que ficaram pendentes. Sem isso, o 409 é de outra pessoa.
     */
    private static boolean isRetryOfPending(ConfirmingBooking state) {
        return state.pendingAttempt() != null && state.pendingAttempt().isFor(state.draft().chosen());
    }

    /** O POST pode ter gravado: confere na agenda e, sem a resposta, guarda a tentativa para o próximo "sim". */
    private StepResult afterUncertainBooking(TurnContext t, BookingDraft draft, NewAppointment attempt) {
        if (reconciler.findOwn(attempt, t.customer().id(), t.deadline()) instanceof Reconciliation.Found found) {
            return reconciled(t, draft, found);
        }
        PendingAttempt pending = new PendingAttempt(attempt.professionalId(), attempt.startAt(), t.now());
        return StepResult.of(new ConfirmingBooking(draft, pending), Texts.bookingUncertain());
    }

    /** O horário só é de outra pessoa se a agenda mostrar que a tentativa do cliente não está lá. */
    private StepResult afterConflictWithPending(TurnContext t, ConfirmingBooking state, NewAppointment attempt) {
        return switch (reconciler.findOwn(attempt, t.customer().id(), t.deadline())) {
            case Reconciliation.Found found -> reconciled(t, state.draft(), found);
            case Reconciliation.Unknown unknown -> StepResult.of(state, Texts.bookingUncertain());
            case Reconciliation.NotFound notFound -> slotTaken(t, state.draft());
        };
    }

    private Reconciliation checkPending(TurnContext t, ConfirmingBooking state) {
        PendingAttempt pending = state.pendingAttempt();
        Customer customer = t.customer();
        if (pending == null || customer.name() == null) {
            return Reconciliation.NOT_FOUND;
        }
        NewAppointment attempt = new NewAppointment(pending.professionalId(), state.draft().serviceId(),
                pending.startAt(), customer.name(), customer.phone());
        return reconciler.findOwn(attempt, customer.id(), t.deadline());
    }

    private StepResult reconciled(TurnContext t, BookingDraft draft, Reconciliation.Found found) {
        return booked(t, draft, found.appointment(), bookedReply(found.appointment())).withNote(StepResult.RECONCILED);
    }

    /** Alguém reservou antes: oferece os horários mais próximos do que o cliente tinha escolhido. */
    private StepResult slotTaken(TurnContext t, BookingDraft draft) {
        TimePreference sameTime = new TimePreference.Exact(draft.chosen().startAt().toLocalTime());
        return gaps.advance(t, draft.withTime(sameTime), Texts.slotTaken());
    }

    private StepResult booked(TurnContext t, BookingDraft draft, AppointmentInfo info, Reply reply) {
        long id = info.id();
        long customerId = t.customer().id();
        boolean known = bookings.exists(id);
        if (known && !bookings.belongsTo(id, customerId)) {
            log.warn("A agenda devolveu o agendamento #{}, que já pertence a outro cliente; tratado como ocupado", id);
            return slotTaken(t, draft);
        }
        if (!known) {
            // Registrado antes de gravar: se a própria gravação falhar, o agendamento já existe na agenda.
            transactionEvents.onRollback(() -> log.error("Agendamento #{} criado no agendamento-api, mas a gravação"
                    + " local foi desfeita; conciliar manualmente", id));
            BookingStatus status = info.status() == BookingStatus.UNKNOWN ? BookingStatus.SCHEDULED : info.status();
            bookings.save(new Booking(id, customerId, draft.serviceId(), info.serviceName(), info.professionalId(),
                    info.professionalName(), info.price(), info.startAt(), info.endAt(), status));
            owner.newBooking(info);
        }
        return StepResult.of(new Idle(new LastBooking(info.ref(), t.now())), reply);
    }

    private Reply bookedReply(AppointmentInfo info) {
        return Texts.booked(info.id(), info.customerName(), business.address());
    }

    private StepResult unexpectedFailure(SchedulingException failure) {
        log.error("Resposta inesperada da agenda ao agendar ({})", failure.getClass().getSimpleName());
        return StepResult.of(Idle.EMPTY, Texts.genericError(business.humanContactPhone()));
    }
}
