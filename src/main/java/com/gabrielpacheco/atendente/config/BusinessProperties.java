package com.gabrielpacheco.atendente.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** O business.yml como está escrito; o {@link BusinessConfig} valida e monta o perfil do estabelecimento. */
@ConfigurationProperties("business")
public record BusinessProperties(String name, String address, String humanContactPhone, String humanContactHours,
                                 List<OpeningHours> openingHours, List<Closed> closedDates, List<Service> services,
                                 List<FaqEntry> faq) {

    public BusinessProperties {
        openingHours = orEmpty(openingHours);
        closedDates = orEmpty(closedDates);
        services = orEmpty(services);
        faq = orEmpty(faq);
    }

    public record OpeningHours(Set<DayOfWeek> days, LocalTime opens, LocalTime closes) {
    }

    public record Closed(LocalDate date, String reason) {
    }

    public record Service(String name, List<String> aliases) {
    }

    public record FaqEntry(String id, List<String> keywords, String answer) {
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
