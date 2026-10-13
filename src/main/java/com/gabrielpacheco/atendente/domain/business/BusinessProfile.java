package com.gabrielpacheco.atendente.domain.business;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** O que o atendente sabe do estabelecimento sem perguntar ao agendamento-api. */
public record BusinessProfile(String name, String address, String humanContactPhone, String humanContactHours,
                              List<OpeningPeriod> openingHours, List<ClosedDate> closedDates, List<Faq> faqs,
                              Map<String, List<String>> serviceAliases) {

    public BusinessProfile {
        openingHours = List.copyOf(openingHours);
        closedDates = List.copyOf(closedDates);
        faqs = List.copyOf(faqs);
        serviceAliases = Map.copyOf(serviceAliases);
    }

    /** Se o dia da semana tem expediente; feriados ficam em {@link #closedOn(LocalDate)}. */
    public boolean opensOn(LocalDate date) {
        return opensOn(date.getDayOfWeek());
    }

    public boolean opensOn(DayOfWeek day) {
        return openingHours.stream().anyMatch(period -> period.days().contains(day));
    }

    public Optional<ClosedDate> closedOn(LocalDate date) {
        return closedDates.stream().filter(closed -> closed.date().equals(date)).findFirst();
    }
}
