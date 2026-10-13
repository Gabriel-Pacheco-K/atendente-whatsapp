package com.gabrielpacheco.atendente.domain.catalog;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record Professional(long id, String name, List<WorkingPeriod> periods) {

    public Professional {
        periods = List.copyOf(periods);
    }

    public String firstName() {
        return name.strip().split("\\s+")[0];
    }

    public Set<DayOfWeek> workingDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        periods.forEach(period -> days.add(period.day()));
        return Collections.unmodifiableSet(days);
    }
}
