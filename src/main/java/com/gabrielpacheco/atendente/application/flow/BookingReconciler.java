package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Descobre se uma tentativa de agendar com resultado desconhecido foi gravada, procurando na agenda do dia um
 * agendamento ativo com o mesmo horário, telefone e nome. Um agendamento que já pertence a outro cliente nunca
 * é devolvido: o telefone da web é digitado, e sem esse filtro alguém poderia tomar posse do horário alheio.
 */
@Component
public class BookingReconciler {

    private static final Logger log = LoggerFactory.getLogger(BookingReconciler.class);
    private static final int ATTEMPTS = 2;

    private final SchedulingPort scheduling;
    private final BookingStore bookings;
    private final Pause pause;
    private final ConversationSettings settings;

    public BookingReconciler(SchedulingPort scheduling, BookingStore bookings, Pause pause,
                             ConversationSettings settings) {
        this.scheduling = scheduling;
        this.bookings = bookings;
        this.pause = pause;
        this.settings = settings;
    }

    public Optional<AppointmentInfo> findOwn(NewAppointment attempted, long customerId, Deadline deadline) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            boolean retry = attempt > 1;
            Duration wait = retry ? settings.reconciliationRetryDelay() : Duration.ZERO;
            if (!deadline.allows(wait.plus(settings.schedulingCallTimeout()))) {
                break;
            }
            if (retry) {
                pause.pause(wait);
            }
            Optional<AppointmentInfo> found = lookUp(attempted, customerId);
            if (found.isPresent()) {
                log.warn("Agendamento #{} reconciliado após resultado incerto", found.get().id());
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<AppointmentInfo> lookUp(NewAppointment attempted, long customerId) {
        List<AppointmentInfo> agenda;
        try {
            agenda = scheduling.dayAgenda(attempted.professionalId(), attempted.startAt().toLocalDate());
        } catch (SchedulingException e) {
            return Optional.empty();
        }
        return agenda.stream()
                .filter(appointment -> isTheAttempt(appointment, attempted))
                .filter(appointment -> !belongsToAnotherCustomer(appointment.id(), customerId))
                .findFirst();
    }

    private static boolean isTheAttempt(AppointmentInfo appointment, NewAppointment attempted) {
        return appointment.status().isActive()
                && appointment.startAt().equals(attempted.startAt())
                && digits(appointment.customerPhone()).equals(digits(attempted.customerPhone()))
                && comparableName(appointment.customerName()).equals(comparableName(attempted.customerName()));
    }

    private boolean belongsToAnotherCustomer(long appointmentId, long customerId) {
        return bookings.exists(appointmentId) && !bookings.belongsTo(appointmentId, customerId);
    }

    private static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static String comparableName(String name) {
        return name == null ? "" : TextNormalizer.fold(name.strip().replaceAll("\\s+", " "));
    }
}
