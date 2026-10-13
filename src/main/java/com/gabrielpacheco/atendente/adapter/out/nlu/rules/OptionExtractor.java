package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.text.EditDistance;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Reconhece a escolha de uma das opções mostradas: "2", "opção 2", "o segundo", "a última" ou o próprio texto
 * da opção. No menu inicial, só um dígito sozinho conta como opção.
 */
final class OptionExtractor {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,2}");
    private static final Pattern NUMBERED = Pattern.compile("\\b(?:opcao|numero|a|o)\\s+(?<number>\\d{1,2})\\b");
    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(?<word>primeir[oa]|segund[oa]|terceir[oa]|quart[oa]|quint[oa]|sext[oa]|ultim[oa])(?<suffix>\\s+opcao)?\\b");
    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            entry("primeiro", 1), entry("primeira", 1), entry("segundo", 2), entry("segunda", 2),
            entry("terceiro", 3), entry("terceira", 3), entry("quarto", 4), entry("quarta", 4),
            entry("quinto", 5), entry("quinta", 5), entry("sexto", 6), entry("sexta", 6));
    private static final Set<String> LAST = Set.of("ultimo", "ultima");

    /** Também são dias da semana: só contam como opção em "a segunda" (sozinho) ou "segunda opção". */
    private static final Set<String> WEEKDAY_NAMES = Set.of("segunda", "quarta", "quinta", "sexta");

    Optional<Integer> extract(Spans spans, InterpretationContext context) {
        int count = context.offeredOptions().size();
        if (count == 0 || !acceptsOptions(context.expectation())) {
            return Optional.empty();
        }
        Optional<Integer> option = wholeNumber(spans, count);
        if (context.expectation() == Expectation.NONE || option.isPresent()) {
            return option;
        }
        return labelMatch(spans, context.offeredOptions())
                .or(() -> numbered(spans, count))
                .or(() -> ordinal(spans, count));
    }

    private static boolean acceptsOptions(Expectation expectation) {
        return expectation == Expectation.OPTION || expectation == Expectation.NAME || expectation == Expectation.NONE;
    }

    private static Optional<Integer> wholeNumber(Spans spans, int count) {
        String remaining = spans.remaining();
        if (!WHOLE_NUMBER.matcher(remaining).matches()) {
            return Optional.empty();
        }
        return consumeAll(spans, inRange(Integer.parseInt(remaining), count));
    }

    /** O texto inteiro igual ao rótulo de uma opção, tolerando erros de digitação. */
    private static Optional<Integer> labelMatch(Spans spans, List<String> labels) {
        String remaining = spans.remaining();
        for (int i = 0; i < labels.size(); i++) {
            if (!remaining.isEmpty() && matchesLabel(remaining, labels.get(i))) {
                return consumeAll(spans, Optional.of(i + 1));
            }
        }
        return Optional.empty();
    }

    static boolean matchesLabel(String normalizedText, String label) {
        return EditDistance.matchesWithTypos(normalizedText, TextNormalizer.normalize(label).text());
    }

    private static Optional<Integer> numbered(Spans spans, int count) {
        Matcher matcher = NUMBERED.matcher(spans.text());
        while (matcher.find()) {
            Optional<Integer> option = inRange(Integer.parseInt(matcher.group("number")), count);
            if (option.isPresent()) {
                spans.consume(matcher.start(), matcher.end());
                return option;
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> ordinal(Spans spans, int count) {
        Matcher matcher = ORDINAL.matcher(spans.text());
        while (matcher.find()) {
            String word = matcher.group("word");
            boolean ambiguousWeekday = WEEKDAY_NAMES.contains(word)
                    && matcher.group("suffix") == null
                    && !spans.remaining().equals("a " + word);
            if (ambiguousWeekday) {
                continue;
            }
            Optional<Integer> option = inRange(LAST.contains(word) ? count : ORDINALS.get(word), count);
            if (option.isPresent()) {
                spans.consume(matcher.start(), matcher.end());
                return option;
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> inRange(int option, int count) {
        return option >= 1 && option <= count ? Optional.of(option) : Optional.empty();
    }

    private static Optional<Integer> consumeAll(Spans spans, Optional<Integer> option) {
        option.ifPresent(found -> spans.consumeRemaining());
        return option;
    }
}
