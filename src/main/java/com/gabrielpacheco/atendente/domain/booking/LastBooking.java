package com.gabrielpacheco.atendente.domain.booking;

import java.time.Duration;
import java.time.Instant;

/** O agendamento recém-criado, para responder "já está confirmado" a um "sim" repetido. */
public record LastBooking(BookingRef booking, Instant createdAt) {

    public boolean isRecent(Instant now, Duration window) {
        return !createdAt.plus(window).isBefore(now);
    }
}
