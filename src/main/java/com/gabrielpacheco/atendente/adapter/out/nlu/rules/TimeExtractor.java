package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;

/**
 * Lê horários ("15h", "15:30", "3 da tarde", "às 4", "meio-dia") e períodos ("de manhã", "depois do almoço").
 * Um horário exato vale mais que um período; entre vários do mesmo tipo, vale o último.
 */
final class TimeExtractor {

    private static final Map<String, Integer> HOUR_WORDS = Map.ofEntries(
            entry("um", 1), entry("uma", 1), entry("dois", 2), entry("duas", 2), entry("tres", 3), entry("quatro", 4),
            entry("cinco", 5), entry("seis", 6), entry("sete", 7), entry("oito", 8), entry("nove", 9),
            entry("dez", 10), entry("onze", 11), entry("doze", 12));

    private static final Pattern NOON = Pattern.compile("\\bmeio dia(?<half> e meia)?\\b");

    /**
     * Hora em dígitos ou por extenso, com minutos opcionais (":30", "h30", "e meia") e período opcional ("da tarde").
     * Sem "às", sem minutos, sem "h", sem "e meia" e sem período, um número sozinho não é hora. O número de uma data
     * também não: nem o mês de "20/10", nem o dia de "dia 20", nem o "20" de "15h 20/10".
     */
    private static final Pattern CLOCK = Pattern.compile(
            "(?<as>\\bas\\s+)?\\b(?<![/:-])(?<!\\bdia\\s)(?<hour>\\d{1,2}|" + longestFirst(HOUR_WORDS.keySet())
                    + ")(?![/-]\\d)"
                    + "(?:(?<colon>:(?<colonMinutes>\\d{2})(?:hs?|hrs?)?)|\\s*(?<suffix>h|hs|hrs?|horas?)(?<suffixMinutes>\\d{2})?)?"
                    + "(?:\\s+e\\s+(?<half>meia|[0-5]\\d))?"
                    + "(?:\\s+d[ae]\\s+(?<period>manha|tarde|noite))?\\b");
    private static final String HALF_HOUR = "meia";

    private static final Map<Pattern, PartOfDay> PERIODS = Map.of(
            Pattern.compile("\\b(?:de|pela) manha\\b|\\bcedo\\b"), PartOfDay.MORNING,
            Pattern.compile("\\b(?:a|de|pela) tarde\\b|\\bdepois do almoco\\b"), PartOfDay.AFTERNOON,
            Pattern.compile("\\b(?:a|de|pela) noite\\b|\\bfim do dia\\b|\\bfinal do dia\\b"), PartOfDay.EVENING);

    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,2}");
    private static final int FIRST_HOUR_AS_NUMBER = 7;
    private static final int LAST_HOUR_AS_NUMBER = 22;
    /** "às 4" é 16:00, porque ninguém marca corte às 4 da manhã; de "às 8" em diante, a hora vale como está. */
    private static final int LAST_HOUR_READ_AS_AFTERNOON = 7;

    Optional<TimePreference> extract(Spans spans, InterpretationContext context) {
        Optional<TimePreference> exact = numberAsHour(spans, context).or(() -> lastExactTime(spans));
        Optional<TimePreference> period = lastPeriod(spans);
        return exact.isPresent() ? exact : period;
    }

    /** Em "qual destes horários?", um "15" que não é número de opção é a hora. */
    private static Optional<TimePreference> numberAsHour(Spans spans, InterpretationContext context) {
        String remaining = spans.remaining();
        if (context.expectation() != Expectation.OPTION || !WHOLE_NUMBER.matcher(remaining).matches()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(remaining);
        if (number <= context.offeredOptions().size() || number < FIRST_HOUR_AS_NUMBER || number > LAST_HOUR_AS_NUMBER) {
            return Optional.empty();
        }
        spans.consumeRemaining();
        return Optional.of(new TimePreference.Exact(LocalTime.of(number, 0)));
    }

    private static Optional<TimePreference> lastExactTime(Spans spans) {
        LocalTime last = null;
        int lastStart = -1;
        Matcher noon = NOON.matcher(spans.text());
        while (noon.find()) {
            last = LocalTime.of(12, noon.group("half") == null ? 0 : 30);
            lastStart = noon.start();
            spans.consume(noon.start(), noon.end());
        }
        Matcher clock = CLOCK.matcher(spans.text());
        while (clock.find()) {
            Optional<LocalTime> time = toTime(clock);
            if (time.isPresent() && clock.start() > lastStart) {
                last = time.get();
                lastStart = clock.start();
            }
            time.ifPresent(found -> spans.consume(clock.start(), clock.end()));
        }
        return Optional.ofNullable(last).map(TimePreference.Exact::new);
    }

    private static Optional<LocalTime> toTime(MatchResult match) {
        // "às 4" e "4 e meia" são o jeito de falar a hora; "4h" e "4:30" dizem que é hora, mas não que é de tarde.
        boolean spoken = match.group("as") != null || HALF_HOUR.equals(match.group("half"));
        String period = match.group("period");
        if (!spoken && period == null && match.group("colon") == null && match.group("suffix") == null) {
            return Optional.empty();
        }
        String hourText = match.group("hour");
        int hour = HOUR_WORDS.getOrDefault(hourText, -1);
        if (hour < 0) {
            hour = Integer.parseInt(hourText);
        }
        if (period != null) {
            boolean laterInTheDay = period.equals("tarde") || period.equals("noite");
            hour = laterInTheDay && hour >= 1 && hour <= 11 ? hour + 12 : hour;
        } else if (spoken && hour >= 1 && hour <= LAST_HOUR_READ_AS_AFTERNOON) {
            hour += 12;
        }
        int minute = minutes(match);
        if (hour > 23 || minute > 59) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hour, minute));
    }

    private static int minutes(MatchResult match) {
        return Stream.of("colonMinutes", "suffixMinutes", "half")
                .map(match::group)
                .filter(Objects::nonNull)
                .findFirst()
                .map(value -> value.equals(HALF_HOUR) ? 30 : Integer.parseInt(value))
                .orElse(0);
    }

    /** "uma" antes de "um" na alternância, senão "uma" casaria só pela metade. */
    private static String longestFirst(Set<String> words) {
        return words.stream().sorted(Comparator.comparingInt(String::length).reversed()).collect(Collectors.joining("|"));
    }

    private static Optional<TimePreference> lastPeriod(Spans spans) {
        PartOfDay last = null;
        int lastStart = -1;
        for (Map.Entry<Pattern, PartOfDay> period : PERIODS.entrySet()) {
            Matcher matcher = period.getKey().matcher(spans.text());
            while (matcher.find()) {
                if (matcher.start() > lastStart) {
                    last = period.getValue();
                    lastStart = matcher.start();
                }
                spans.consume(matcher.start(), matcher.end());
            }
        }
        return Optional.ofNullable(last).map(TimePreference.Period::new);
    }
}
