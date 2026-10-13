package com.gabrielpacheco.atendente.domain.catalog;

import com.gabrielpacheco.atendente.domain.text.EditDistance;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reconhece serviços e profissionais num texto já normalizado. A frase mais longa vence:
 * "corte e barba" fica com "Corte + barba", e não com "Corte masculino".
 */
public final class CatalogMatcher {

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private final Catalog catalog;
    private final List<Phrase<ServiceType>> servicePhrases;
    private final List<Phrase<Professional>> professionalPhrases;

    public CatalogMatcher(Catalog catalog) {
        this.catalog = catalog;
        this.servicePhrases = longestFirst(catalog.services().stream()
                .flatMap(service -> Stream.concat(Stream.of(service.name()), service.aliases().stream())
                        .map(alias -> Phrase.of(service, alias))));
        this.professionalPhrases = longestFirst(catalog.professionals().stream()
                .flatMap(professional -> Stream.of(
                        Phrase.of(professional, professional.name()),
                        Phrase.of(professional, professional.firstName()))));
    }

    public Optional<Hit<ServiceType>> matchService(String normalized) {
        return firstMatch(servicePhrases, normalized);
    }

    /** Nome completo ou primeiro nome; o primeiro nome tolera erros de digitação ("pedor", "joaoo"). */
    public Optional<Hit<Professional>> matchProfessional(String normalized) {
        return firstMatch(professionalPhrases, normalized).or(() -> firstNameWithTypo(normalized));
    }

    public Optional<ServiceType> serviceByName(String anyCase) {
        String wanted = TextNormalizer.normalize(anyCase).text();
        return catalog.services().stream()
                .filter(service -> TextNormalizer.normalize(service.name()).text().equals(wanted))
                .findFirst();
    }

    private Optional<Hit<Professional>> firstNameWithTypo(String normalized) {
        Matcher word = WORD.matcher(normalized);
        while (word.find()) {
            for (Professional professional : catalog.professionals()) {
                String firstName = TextNormalizer.normalize(professional.firstName()).text();
                if (EditDistance.matchesWithTypos(word.group(), firstName)) {
                    return Optional.of(new Hit<>(professional, word.start(), word.end()));
                }
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<Hit<T>> firstMatch(List<Phrase<T>> phrases, String normalized) {
        for (Phrase<T> phrase : phrases) {
            Matcher matcher = phrase.pattern().matcher(normalized);
            if (matcher.find()) {
                return Optional.of(new Hit<>(phrase.value(), matcher.start(), matcher.end()));
            }
        }
        return Optional.empty();
    }

    private static <T> List<Phrase<T>> longestFirst(Stream<Phrase<T>> phrases) {
        return phrases
                .filter(phrase -> !phrase.text().isEmpty())
                .sorted(Comparator.comparingInt((Phrase<T> phrase) -> phrase.words())
                        .thenComparingInt(phrase -> phrase.text().length())
                        .reversed())
                .toList();
    }

    private record Phrase<T>(T value, String text, Pattern pattern) {

        static <T> Phrase<T> of(T value, String raw) {
            String text = TextNormalizer.normalize(raw).text();
            return new Phrase<>(value, text, Pattern.compile("\\b" + Pattern.quote(text) + "\\b"));
        }

        int words() {
            return text.split(" ").length;
        }
    }
}
