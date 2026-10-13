package com.gabrielpacheco.atendente.domain.catalog;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingPeriod(DayOfWeek day, LocalTime start, LocalTime end) {

    public WorkingPeriod {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("O expediente precisa começar antes de terminar: " + start + "–" + end);
        }
    }
}
