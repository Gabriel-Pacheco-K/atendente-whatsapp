package com.gabrielpacheco.atendente.domain.catalog;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Comparator;
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

    /** Os turnos do dia da semana, do mais cedo para o mais tarde. */
    public List<WorkingPeriod> periodsOn(DayOfWeek day) {
        return periods.stream()
                .filter(period -> period.day() == day)
                .sorted(Comparator.comparing(WorkingPeriod::start))
                .toList();
    }
}
