package com.gabrielpacheco.atendente.domain.business;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record OpeningPeriod(Set<DayOfWeek> days, LocalTime opens, LocalTime closes) {

    public OpeningPeriod {
        days = Set.copyOf(days);
    }
}
