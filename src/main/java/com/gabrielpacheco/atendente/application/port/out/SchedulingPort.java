package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A agenda de verdade (o agendamento-api). Toda falha chega como uma {@link SchedulingException}.
 */
public interface SchedulingPort {

    List<Professional> professionals();

    /** Os serviços vêm sem apelidos; quem junta os do business.yml é o {@code CatalogProvider}. */
    List<ServiceType> services();

    List<LocalTime> freeSlots(long professionalId, long serviceId, LocalDate date);

    /** Nunca é repetido: um resultado desconhecido vira {@link UnknownBookingOutcomeException}. */
    AppointmentInfo book(NewAppointment appointment);

    AppointmentInfo find(long appointmentId);

    AppointmentInfo cancel(long appointmentId, String reason);

    /** Todos os agendamentos do profissional no dia, em qualquer status. Usado na reconciliação. */
    List<AppointmentInfo> dayAgenda(long professionalId, LocalDate date);

    /** Esta mesma agenda, mas sem repetir uma consulta que falhou se a repetição não couber em {@code deadline}. */
    SchedulingPort within(Deadline deadline);
}
