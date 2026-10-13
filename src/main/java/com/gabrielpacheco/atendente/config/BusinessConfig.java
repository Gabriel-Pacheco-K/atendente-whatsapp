package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.business.ClosedDate;
import com.gabrielpacheco.atendente.domain.business.Faq;
import com.gabrielpacheco.atendente.domain.business.OpeningPeriod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class BusinessConfig {

    @Bean
    BusinessProfile businessProfile(BusinessProperties properties) {
        return toProfile(properties);
    }

    /** Um business.yml com erro impede a subida, com uma mensagem que diz o que corrigir. */
    static BusinessProfile toProfile(BusinessProperties properties) {
        List<String> problems = problemsIn(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("O business.yml tem problemas: " + String.join("; ", problems));
        }
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        properties.services().forEach(service ->
                aliases.put(service.name(), service.aliases() == null ? List.of() : service.aliases()));
        return new BusinessProfile(properties.name(), properties.address(), properties.humanContactPhone(),
                properties.humanContactHours(),
                properties.openingHours().stream()
                        .map(hours -> new OpeningPeriod(hours.days(), hours.opens(), hours.closes()))
                        .toList(),
                properties.closedDates().stream().map(closed -> new ClosedDate(closed.date(), closed.reason())).toList(),
                properties.faq().stream()
                        .map(entry -> new Faq(entry.id(), entry.keywords() == null ? List.of() : entry.keywords(),
                                entry.answer()))
                        .toList(),
                aliases);
    }

    private static List<String> problemsIn(BusinessProperties properties) {
        List<String> problems = new ArrayList<>();
        requirePresent(properties.name(), "business.name", problems);
        requirePresent(properties.address(), "business.address", problems);
        requirePresent(properties.humanContactPhone(), "business.human-contact-phone", problems);
        for (BusinessProperties.OpeningHours hours : properties.openingHours()) {
            if (hours.days() == null || hours.days().isEmpty() || hours.opens() == null || hours.closes() == null) {
                problems.add("cada item de business.opening-hours precisa de days, opens e closes");
            } else if (!hours.opens().isBefore(hours.closes())) {
                problems.add("em business.opening-hours, " + hours.opens() + " precisa ser antes de " + hours.closes());
            }
        }
        for (BusinessProperties.Closed closed : properties.closedDates()) {
            if (closed.date() == null) {
                problems.add("cada item de business.closed-dates precisa de date (entre aspas, como \"2026-12-25\")");
            }
        }
        for (BusinessProperties.Service service : properties.services()) {
            if (service.name() == null || service.name().isBlank()) {
                problems.add("cada item de business.services precisa de name");
            }
        }
        Set<String> faqIds = new HashSet<>();
        for (BusinessProperties.FaqEntry entry : properties.faq()) {
            if (entry.id() == null || entry.id().isBlank() || entry.answer() == null || entry.answer().isBlank()) {
                problems.add("cada item de business.faq precisa de id e answer");
            } else if (!faqIds.add(entry.id())) {
                problems.add("o id '" + entry.id() + "' aparece mais de uma vez em business.faq");
            }
        }
        return problems;
    }

    private static void requirePresent(String value, String property, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(property + " é obrigatório");
        }
    }
}
