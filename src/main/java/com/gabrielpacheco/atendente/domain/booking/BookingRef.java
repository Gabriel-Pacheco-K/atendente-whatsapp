package com.gabrielpacheco.atendente.domain.booking;

import java.time.LocalDateTime;

/** O suficiente de um agendamento para mostrá-lo ao cliente e cancelá-lo pelo id. */
public record BookingRef(long appointmentId, String serviceName, String professionalName, LocalDateTime startAt) {
}
