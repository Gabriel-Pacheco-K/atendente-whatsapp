package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Um agendamento como o agendamento-api o devolve (sem o id do serviço, só o nome). */
public record AppointmentInfo(long id, long professionalId, String professionalName, String serviceName,
                              BigDecimal price, String customerName, String customerPhone, LocalDateTime startAt,
                              LocalDateTime endAt, BookingStatus status) {

    public BookingRef ref() {
        return new BookingRef(id, serviceName, professionalName, startAt);
    }
}
