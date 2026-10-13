package com.gabrielpacheco.atendente.domain.booking;

import com.gabrielpacheco.atendente.domain.booking.Suggestion.Alternatives;
import com.gabrielpacheco.atendente.domain.booking.Suggestion.ExactMatch;
import com.gabrielpacheco.atendente.domain.booking.Suggestion.NoSlots;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Escolhe quais horários livres oferecer: o pedido, os mais próximos dele ou uma amostra espalhada pelo dia.
 * Com "tanto faz", cada horário vai para quem recebeu menos opções até ali, então as sugestões se alternam.
 */
public final class SlotSuggester {

    private static final int NEAREST = 3;

    public Suggestion suggest(DayAvailability day, TimePreference preference, int max) {
        if (!day.hasFreeSlots()) {
            return new NoSlots(day.searchedUntil());
        }
        List<LocalTime> times = day.slots().stream()
                .flatMap(slots -> slots.free().stream())
                .distinct()
                .sorted()
                .toList();
        Note fallbackNote = day.movedFromRequestedDay() ? Note.MOVED_TO_NEXT_DAY : Note.NONE;
        return switch (preference) {
            case null -> new Alternatives(assign(day, spread(times, max)), fallbackNote);
            case TimePreference.Exact exact -> exact(day, times, exact.time(), max);
            case TimePreference.Period period -> period(day, times, period.part(), max);
        };
    }

    private static Suggestion exact(DayAvailability day, List<LocalTime> times, LocalTime wanted, int max) {
        if (!day.movedFromRequestedDay() && times.contains(wanted)) {
            return new ExactMatch(assign(day, List.of(wanted)).get(0));
        }
        List<LocalTime> nearest = times.stream()
                .sorted(Comparator.comparing((LocalTime time) -> Duration.between(time, wanted).abs())
                        .thenComparing(Comparator.naturalOrder()))
                .limit(Math.min(NEAREST, max))
                .sorted()
                .toList();
        Note note = day.movedFromRequestedDay() ? Note.MOVED_TO_NEXT_DAY : whyUnavailable(day, wanted);
        return new Alternatives(assign(day, nearest), note);
    }

    /** Só é "já tem cliente" quando o horário está no expediente e ainda dá tempo de marcar. */
    private static Note whyUnavailable(DayAvailability day, LocalTime wanted) {
        if (day.date().atTime(wanted).isBefore(day.bookableFrom())) {
            return Note.TOO_SOON;
        }
        boolean someoneWorks = day.slots().stream()
                .anyMatch(slots -> slots.professional().worksAt(day.date().getDayOfWeek(), wanted));
        return someoneWorks ? Note.REQUESTED_TAKEN : Note.OUTSIDE_HOURS;
    }

    private static Suggestion period(DayAvailability day, List<LocalTime> times, PartOfDay part, int max) {
        List<LocalTime> inPeriod = times.stream().filter(part::contains).toList();
        if (day.movedFromRequestedDay()) {
            return new Alternatives(assign(day, spread(inPeriod.isEmpty() ? times : inPeriod, max)),
                    Note.MOVED_TO_NEXT_DAY);
        }
        return inPeriod.isEmpty()
                ? new Alternatives(assign(day, spread(times, max)), Note.PERIOD_FULL)
                : new Alternatives(assign(day, spread(inPeriod, max)), Note.NONE);
    }

    /** Até {@code max} horários nos índices round(i·(n−1)/(max−1)): o primeiro, o último e os do meio. */
    private static List<LocalTime> spread(List<LocalTime> times, int max) {
        int n = times.size();
        if (n <= max) {
            return times;
        }
        return IntStream.range(0, max)
                .mapToObj(i -> times.get((int) Math.round(i * (n - 1) / (double) (max - 1))))
                .toList();
    }

    /**
     * Para cada horário, fica com quem recebeu menos opções nesta lista; no empate, quem tem mais horários livres
     * no dia; depois, a ordem do catálogo.
     */
    private static List<SlotOption> assign(DayAvailability day, List<LocalTime> times) {
        Map<Long, Integer> given = new HashMap<>();
        List<ProfessionalDaySlots> candidates = day.slots();
        List<SlotOption> options = new ArrayList<>();
        for (LocalTime time : times) {
            ProfessionalDaySlots chosen = candidates.stream()
                    .filter(slots -> slots.free().contains(time))
                    .min(Comparator.comparingInt((ProfessionalDaySlots slots) ->
                                    given.getOrDefault(slots.professional().id(), 0))
                            .thenComparing(slots -> slots.free().size(), Comparator.reverseOrder())
                            .thenComparingInt(candidates::indexOf))
                    .orElseThrow();
            given.merge(chosen.professional().id(), 1, Integer::sum);
            options.add(new SlotOption(day.date().atTime(time), chosen.professional().id(),
                    chosen.professional().name()));
        }
        return options;
    }
}
