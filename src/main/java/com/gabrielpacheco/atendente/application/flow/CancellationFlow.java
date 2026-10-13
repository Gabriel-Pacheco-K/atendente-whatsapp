package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.SlotTakenException;
import com.gabrielpacheco.atendente.application.port.out.UnknownBookingOutcomeException;
import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingBookingToCancel;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingCancellation;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lista e cancela os agendamentos do próprio cliente. Antes de mostrar, confere cada um na agenda: o que a
 * barbearia cancelou ou concluiu some da lista. O cliente escolhe pelo número; o id nunca vem do texto.
 */
@Component
public class CancellationFlow {

    private static final Logger log = LoggerFactory.getLogger(CancellationFlow.class);
    private static final int MAX_LISTED = ChoosingBookingToCancel.MAX_OPTIONS;

    private final SchedulingPort scheduling;
    private final BookingStore bookings;
    private final BusinessProfile business;
    private final ConversationSettings settings;

    public CancellationFlow(SchedulingPort scheduling, BookingStore bookings, BusinessProfile business,
                            ConversationSettings settings) {
        this.scheduling = scheduling;
        this.bookings = bookings;
        this.business = business;
        this.settings = settings;
    }

    public StepResult start(TurnContext t) {
        Optional<List<BookingRef>> upcoming = refreshedUpcoming(t);
        if (upcoming.isEmpty()) {
            return StepResult.of(t.idle(), Texts.schedulingOffline(phone()));
        }
        List<BookingRef> refs = upcoming.get();
        if (refs.isEmpty()) {
            return StepResult.of(t.idle(), Texts.noBookings());
        }
        StepResult result = refs.size() == 1
                ? StepResult.of(new ConfirmingCancellation(refs.get(0)), Texts.confirmCancel(refs.get(0)))
                : StepResult.of(new ChoosingBookingToCancel(refs), Texts.chooseBookingToCancel(refs));
        return t.entities().rescheduleRequested() ? result.plus(Texts.rescheduleHint()) : result;
    }

    public StepResult myBookings(TurnContext t) {
        Optional<List<BookingRef>> upcoming = refreshedUpcoming(t);
        if (upcoming.isEmpty()) {
            return StepResult.of(t.idle(), Texts.schedulingOffline(phone()));
        }
        List<BookingRef> refs = upcoming.get();
        return StepResult.of(t.idle(), refs.isEmpty() ? Texts.noBookings() : Texts.myBookings(refs));
    }

    public Optional<StepResult> onChoosingBookingToCancel(TurnContext t, ChoosingBookingToCancel state) {
        Integer option = t.entities().option();
        if (option != null && option <= state.options().size()) {
            BookingRef chosen = state.options().get(option - 1);
            return Optional.of(StepResult.of(new ConfirmingCancellation(chosen), Texts.confirmCancel(chosen)));
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(Idle.EMPTY, Texts.cancelKept()));
        }
        return Optional.empty();
    }

    public Optional<StepResult> onConfirmingCancellation(TurnContext t, ConfirmingCancellation state) {
        if (t.entities().yesNoConflict()) {
            return Optional.of(StepResult.of(state, Texts.yesOrNo()));
        }
        if (t.intent() == Intent.AFFIRM) {
            return Optional.of(cancel(t, state));
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(Idle.EMPTY, Texts.cancelKept()));
        }
        return Optional.empty();
    }

    private StepResult cancel(TurnContext t, ConfirmingCancellation state) {
        long id = state.booking().appointmentId();
        if (!bookings.belongsTo(id, t.customer().id())) {
            log.error("Pedido de cancelamento do agendamento #{}, que não é do cliente #{}", id, t.customer().id());
            return StepResult.of(Idle.EMPTY, Texts.genericError(phone()));
        }
        String reason = "Cancelado pelo cliente no atendente virtual (" + t.customer().channel().label() + ")";
        try {
            scheduling.cancel(id, reason);
            return cancelled(id);
        } catch (SchedulingException e) {
            return afterFailedCancel(t, state, id, e);
        }
    }

    private StepResult afterFailedCancel(TurnContext t, ConfirmingCancellation state, long id,
                                         SchedulingException failure) {
        return switch (failure) {
            case BookingRejectedException e -> isCancelledNow(t, id)
                    ? alreadyCancelled(id)
                    : StepResult.of(Idle.EMPTY, Texts.cancelRejected(e.detail(), phone()));
            case UnknownBookingOutcomeException e -> isCancelledNow(t, id)
                    ? cancelled(id)
                    : StepResult.of(state, Texts.schedulingOffline(phone()));
            case AppointmentNotFoundException e -> alreadyCancelled(id);
            case SchedulingUnavailableException e -> StepResult.of(state, Texts.schedulingOffline(phone()));
            case SlotTakenException e -> unexpectedCancelFailure(e);
            case SchedulingContractException e -> unexpectedCancelFailure(e);
        };
    }

    private StepResult cancelled(long id) {
        bookings.updateStatus(id, BookingStatus.CANCELLED);
        return StepResult.of(Idle.EMPTY, Texts.cancelled());
    }

    private StepResult alreadyCancelled(long id) {
        bookings.updateStatus(id, BookingStatus.CANCELLED);
        return StepResult.of(Idle.EMPTY, Texts.alreadyCancelled());
    }

    private StepResult unexpectedCancelFailure(SchedulingException failure) {
        log.error("Resposta inesperada da agenda ao cancelar ({})", failure.getClass().getSimpleName());
        return StepResult.of(Idle.EMPTY, Texts.genericError(phone()));
    }

    /** Confere na agenda depois de uma falha no cancelamento; sem resposta, não dá para afirmar nada. */
    private boolean isCancelledNow(TurnContext t, long id) {
        if (!t.deadline().allows(settings.schedulingCallTimeout())) {
            return false;
        }
        try {
            return scheduling.within(t.deadline()).find(id).status() == BookingStatus.CANCELLED;
        } catch (SchedulingException e) {
            return false;
        }
    }

    /**
     * Os próximos agendamentos ativos, conferidos na agenda. Vazio quando a agenda está fora do ar: melhor avisar
     * que mostrar uma lista que pode estar errada.
     */
    private Optional<List<BookingRef>> refreshedUpcoming(TurnContext t) {
        if (!t.catalog().online()) {
            return Optional.empty();
        }
        List<Booking> upcoming = bookings.upcoming(t.customer().id(), t.localNow()).stream()
                .limit(MAX_LISTED)
                .toList();
        if (upcoming.isEmpty()) {
            return Optional.of(List.of());
        }
        if (!t.deadline().allows(settings.schedulingCallTimeout())) {
            return Optional.empty();
        }
        List<Optional<AppointmentInfo>> current;
        try {
            current = findAll(upcoming, scheduling.within(t.deadline()));
        } catch (SchedulingException e) {
            return Optional.empty();
        }
        List<BookingRef> stillActive = new ArrayList<>();
        for (int i = 0; i < upcoming.size(); i++) {
            Booking booking = upcoming.get(i);
            if (isStillActive(booking, current.get(i))) {
                stillActive.add(booking.ref());
            }
        }
        return Optional.of(stillActive);
    }

    /** Atualiza o status local com o da agenda; um status que não conhecemos nunca é gravado. */
    private boolean isStillActive(Booking booking, Optional<AppointmentInfo> current) {
        if (current.isEmpty()) {
            bookings.updateStatus(booking.appointmentId(), BookingStatus.CANCELLED);
            return false;
        }
        BookingStatus status = current.get().status();
        if (status == BookingStatus.UNKNOWN) {
            log.warn("Status desconhecido na agenda para o agendamento #{}; o status local foi mantido",
                    booking.appointmentId());
            return false;
        }
        if (status != booking.status()) {
            bookings.updateStatus(booking.appointmentId(), status);
        }
        return status.isActive();
    }

    /** Uma consulta por agendamento, em paralelo; vazio quando a agenda não o encontra mais. */
    private static List<Optional<AppointmentInfo>> findAll(List<Booking> upcoming, SchedulingPort agenda) {
        return ParallelCalls.each(upcoming, booking -> {
            try {
                return Optional.of(agenda.find(booking.appointmentId()));
            } catch (AppointmentNotFoundException e) {
                return Optional.empty();
            }
        });
    }

    private String phone() {
        return business.humanContactPhone();
    }
}
