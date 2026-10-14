package com.gabrielpacheco.atendente.domain.catalog;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

public record WorkingPeriod(DayOfWeek day, LocalTime start, LocalTime end) {

    public WorkingPeriod {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("O expediente precisa começar antes de terminar: " + start + "–" + end);
        }
    }

    /** Um atendimento de {@code duration} que começa em {@code time} termina dentro deste turno. */
    public boolean fits(LocalTime time, Duration duration) {
        return !time.isBefore(start) && Duration.between(time, end).compareTo(duration) >= 0;
    }
}
