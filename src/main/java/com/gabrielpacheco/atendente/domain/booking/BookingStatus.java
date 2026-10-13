package com.gabrielpacheco.atendente.domain.booking;

import java.util.Arrays;

/** Os status do agendamento-api; um valor que ele passe a usar e que não conhecemos vira {@code UNKNOWN}. */
public enum BookingStatus {
    SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, UNKNOWN;

    /** Ocupa o horário na agenda do profissional. */
    public boolean isActive() {
        return this == SCHEDULED || this == CONFIRMED;
    }

    public static BookingStatus parse(String status) {
        return Arrays.stream(values())
                .filter(value -> value != UNKNOWN && value.name().equals(status))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
