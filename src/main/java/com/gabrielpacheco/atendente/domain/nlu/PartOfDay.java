package com.gabrielpacheco.atendente.domain.nlu;

import java.time.LocalTime;

/** Manhã [00:00, 12:00), tarde [12:00, 18:00) e noite [18:00, 24:00). */
public enum PartOfDay {
    MORNING, AFTERNOON, EVENING;

    private static final LocalTime EVENING_STARTS = LocalTime.of(18, 0);

    public boolean contains(LocalTime time) {
        return switch (this) {
            case MORNING -> time.isBefore(LocalTime.NOON);
            case AFTERNOON -> !time.isBefore(LocalTime.NOON) && time.isBefore(EVENING_STARTS);
            case EVENING -> !time.isBefore(EVENING_STARTS);
        };
    }
}
