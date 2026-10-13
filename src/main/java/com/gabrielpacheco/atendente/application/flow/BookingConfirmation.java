package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
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

    public BookingConfirmation(BookingGaps gaps, BookingReconciler reconciler, SchedulingPort scheduling,
                               BookingStore bookings, TransactionEvents transactionEvents, BusinessProfile business,
                               ConversationSettings settings) {
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
        PendingAttempt pending = stale.pendingAttempt();
        Customer customer = t.customer();
        if (pending == null || customer.name() == null) {
            return Optional.empty();
        }
        NewAppointment attempt = new NewAppointment(pending.professionalId(), stale.draft().serviceId(),
                pending.startAt(), customer.name(), customer.phone());
        return reconciler.findOwn(attempt, customer.id(), t.deadline())
                .map(info -> booked(t, stale.draft(), info, Texts.foundAfterRestart(info.ref()))
                        .withNote(StepResult.RECONCILED));
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
                    ? reconcile(t, draft, attempt).orElseGet(() -> slotTaken(t, draft))
                    : slotTaken(t, draft);
            case UnknownBookingOutcomeException e -> reconcile(t, draft, attempt).orElseGet(() -> StepResult.of(
                    new ConfirmingBooking(draft, new PendingAttempt(attempt.professionalId(), attempt.startAt(),
                            t.now())),
                    Texts.bookingUncertain()));
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

    private Optional<StepResult> reconcile(TurnContext t, BookingDraft draft, NewAppointment attempt) {
        return reconciler.findOwn(attempt, t.customer().id(), t.deadline())
                .map(info -> booked(t, draft, info, bookedReply(info)).withNote(StepResult.RECONCILED));
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
            BookingStatus status = info.status() == BookingStatus.UNKNOWN ? BookingStatus.SCHEDULED : info.status();
            bookings.save(new Booking(id, customerId, draft.serviceId(), info.serviceName(), info.professionalId(),
                    info.professionalName(), info.price(), info.startAt(), info.endAt(), status));
            transactionEvents.onRollback(() -> log.error("Agendamento #{} criado no agendamento-api, mas a gravação"
                    + " local foi desfeita; conciliar manualmente", id));
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
