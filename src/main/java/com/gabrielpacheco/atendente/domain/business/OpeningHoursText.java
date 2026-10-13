package com.gabrielpacheco.atendente.domain.business;

import com.gabrielpacheco.atendente.domain.reply.PtBr;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Descreve o horário de funcionamento agrupando dias vizinhos com o mesmo expediente:
 * "Terça a sexta: 09:00 às 19:00", "Sábado: 08:00 às 14:00" e, no fim, "Segunda e domingo: fechado".
 */
public final class OpeningHoursText {

    private OpeningHoursText() {
    }

    public static String describe(List<OpeningPeriod> periods) {
        Map<DayOfWeek, String> hours = hoursByDay(periods);
        List<String> lines = new ArrayList<>();
        List<DayOfWeek> group = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            String hoursOfDay = hours.get(day);
            if (!group.isEmpty() && !hours.get(group.get(0)).equals(hoursOfDay)) {
                lines.add(label(group) + ": " + hours.get(group.get(0)));
                group.clear();
            }
            if (hoursOfDay != null) {
                group.add(day);
            }
        }
        if (!group.isEmpty()) {
            lines.add(label(group) + ": " + hours.get(group.get(0)));
        }
        List<String> closed = Arrays.stream(DayOfWeek.values())
                .filter(day -> !hours.containsKey(day))
                .map(PtBr::dayName)
                .toList();
        if (!closed.isEmpty()) {
            lines.add(PtBr.capitalize(PtBr.joinWithAnd(closed)) + ": fechado");
        }
        return String.join("\n", lines);
    }

    private static Map<DayOfWeek, String> hoursByDay(List<OpeningPeriod> periods) {
        Map<DayOfWeek, String> hours = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            String text = periods.stream()
                    .filter(period -> period.days().contains(day))
                    .sorted(Comparator.comparing(OpeningPeriod::opens))
                    .map(period -> PtBr.time(period.opens()) + " às " + PtBr.time(period.closes()))
                    .collect(Collectors.joining(" e "));
            if (!text.isEmpty()) {
                hours.put(day, text);
            }
        }
        return hours;
    }

    private static String label(List<DayOfWeek> group) {
        String first = PtBr.dayName(group.get(0));
        String last = PtBr.dayName(group.get(group.size() - 1));
        String label = switch (group.size()) {
            case 1 -> first;
            case 2 -> first + " e " + last;
            default -> first + " a " + last;
        };
        return PtBr.capitalize(label);
    }
}
