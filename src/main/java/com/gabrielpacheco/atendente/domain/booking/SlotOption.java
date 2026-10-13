package com.gabrielpacheco.atendente.domain.booking;

import java.time.LocalDateTime;

/** Um horário livre com um profissional, como foi oferecido ao cliente. */
public record SlotOption(LocalDateTime startAt, long professionalId, String professionalName) {
}
