package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.gabrielpacheco.atendente.domain.business.Faq;
import com.gabrielpacheco.atendente.domain.catalog.CatalogMatcher;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Confere a saída do Claude contra o catálogo e o contexto. O que não existir, ou estiver fora da faixa, vira null:
 * serviços e profissionais só entram pelo nome do catálogo, nunca por um id vindo do modelo.
 */
final class ClaudeExtractionMapper {

    private static final DateTimeFormatter HOUR_AND_MINUTE = DateTimeFormatter.ofPattern("H:mm");

    private ClaudeExtractionMapper() {
    }

    static Interpretation toInterpretation(ClaudeExtraction extraction, InterpretationContext context) {
        CatalogMatcher catalog = new CatalogMatcher(context.catalog());
        Long serviceId = extraction.service().flatMap(catalog::serviceByName).map(ServiceType::id).orElse(null);
        Long professionalId = extraction.professional().flatMap(catalog::professionalByName)
                .map(Professional::id).orElse(null);
        Entities entities = new Entities(serviceId, professionalId,
                extraction.anyProfessional() && professionalId == null, date(extraction, context), null,
                time(extraction), option(extraction, context), null, faqId(extraction, context), false, false);
        return new Interpretation(extraction.intent(), clamp(extraction.confidence()), entities,
                InterpreterKind.CLAUDE);
    }

    private static LocalDate date(ClaudeExtraction extraction, InterpretationContext context) {
        LocalDate lastBookable = context.today().plusDays(context.maxDaysAhead());
        return extraction.date()
                .flatMap(ClaudeExtractionMapper::parseDate)
                .filter(date -> !date.isBefore(context.today()) && !date.isAfter(lastBookable))
                .orElse(null);
    }

    private static TimePreference time(ClaudeExtraction extraction) {
        Optional<TimePreference> exact = extraction.time()
                .flatMap(ClaudeExtractionMapper::parseTime)
                .map(TimePreference.Exact::new);
        return exact.orElseGet(() -> switch (extraction.partOfDay()) {
            case NONE -> null;
            case MORNING -> new TimePreference.Period(PartOfDay.MORNING);
            case AFTERNOON -> new TimePreference.Period(PartOfDay.AFTERNOON);
            case EVENING -> new TimePreference.Period(PartOfDay.EVENING);
        });
    }

    private static Integer option(ClaudeExtraction extraction, InterpretationContext context) {
        return extraction.option()
                .filter(option -> option >= 1 && option <= context.offeredOptions().size())
                .orElse(null);
    }

    private static String faqId(ClaudeExtraction extraction, InterpretationContext context) {
        return extraction.faqId()
                .filter(id -> context.faqs().stream().map(Faq::id).anyMatch(id::equals))
                .orElse(null);
    }

    private static double clamp(double confidence) {
        return Double.isNaN(confidence) ? 0 : Math.max(0, Math.min(1, confidence));
    }

    private static Optional<LocalDate> parseDate(String text) {
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalTime> parseTime(String text) {
        try {
            return Optional.of(LocalTime.parse(text, HOUR_AND_MINUTE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
