package com.gabrielpacheco.atendente.domain.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Um agendamento criado por este atendente, guardado localmente porque o agendamento-api não busca por telefone. */
public record Booking(long appointmentId, long customerId, long serviceId, String serviceName,
                      long professionalId, String professionalName, BigDecimal price, LocalDateTime startAt,
                      LocalDateTime endAt, BookingStatus status) {

    public BookingRef ref() {
        return new BookingRef(appointmentId, serviceName, professionalName, startAt);
    }
}
