package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.DateIssue;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Lê datas relativas ("amanhã", "sexta que vem", "dia 20") e absolutas ("20/10", "20 de outubro"), sempre a partir
 * de hoje. Com várias datas na frase, vale a última ("não, sexta... sábado").
 */
final class DateExtractor {

    record DateMention(LocalDate date, DateIssue issue) {

        static DateMention of(LocalDate date) {
            return new DateMention(date, null);
        }

        static DateMention problem(DateIssue issue) {
            return new DateMention(null, issue);
        }
    }

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "segunda", DayOfWeek.MONDAY, "terca", DayOfWeek.TUESDAY, "quarta", DayOfWeek.WEDNESDAY,
            "quinta", DayOfWeek.THURSDAY, "sexta", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            entry("janeiro", 1), entry("fevereiro", 2), entry("marco", 3), entry("abril", 4), entry("maio", 5),
            entry("junho", 6), entry("julho", 7), entry("agosto", 8), entry("setembro", 9), entry("outubro", 10),
            entry("novembro", 11), entry("dezembro", 12),
            entry("jan", 1), entry("fev", 2), entry("mar", 3), entry("abr", 4), entry("mai", 5), entry("jun", 6),
            entry("jul", 7), entry("ago", 8), entry("set", 9), entry("out", 10), entry("nov", 11), entry("dez", 12));

    private static final Pattern RELATIVE = Pattern.compile("\\b(?<word>depois de amanha|amanha|hoje)\\b");
    private static final Pattern NUMERIC =
            Pattern.compile("\\b(?<day>\\d{1,2})[/-](?<month>\\d{1,2})(?:[/-](?<year>\\d{4}|\\d{2}))?\\b");
    private static final Pattern WRITTEN = Pattern.compile("\\b(?<day>\\d{1,2})\\s+de\\s+(?<month>"
            + String.join("|", MONTHS.keySet()) + ")\\b(?:\\s+de\\s+(?<year>\\d{4}))?");
    private static final Pattern WEEKDAY = Pattern.compile("\\b(?:(?<next>proxima|proximo)\\s+)?(?<day>"
            + String.join("|", WEEKDAYS.keySet()) + ")(?:\\s+feira)?"
            + "(?:\\s+(?<nextWeek>da semana que vem)|\\s+(?<coming>que vem))?\\b");
    private static final Pattern DAY_OF_MONTH = Pattern.compile("\\bdia\\s+(?<day>\\d{1,2})\\b");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,2}");

    /** Sem ano, uma data que passou há mais de uma semana é do ano que vem; menos que isso, é engano. */
    private static final int RECENT_PAST_DAYS = 7;

    private record Found(int start, DateMention mention) {
    }

    Optional<DateMention> extract(Spans spans, InterpretationContext context) {
        LocalDate today = context.today();
        List<Found> found = new ArrayList<>();
        collect(spans, RELATIVE, match -> DateMention.of(relative(match.group("word"), today)), found);
        collect(spans, NUMERIC, match -> numeric(match, today), found);
        collect(spans, WRITTEN, match -> written(match, today), found);
        collect(spans, WEEKDAY, match -> DateMention.of(weekday(match, today)), found);
        collect(spans, DAY_OF_MONTH, match -> dayOfMonth(Integer.parseInt(match.group("day")), today), found);
        if (found.isEmpty() && context.expectation() == Expectation.DATE) {
            bareDayOfMonth(spans, today).ifPresent(found::add);
        }
        return found.stream()
                .max(Comparator.comparingInt(Found::start))
                .map(last -> withinReach(last.mention(), today, context.maxDaysAhead()));
    }

    private static void collect(Spans spans, Pattern pattern, Function<MatchResult, DateMention> reader,
                                List<Found> found) {
        Matcher matcher = pattern.matcher(spans.text());
        while (matcher.find()) {
            found.add(new Found(matcher.start(), reader.apply(matcher)));
            spans.consume(matcher.start(), matcher.end());
        }
    }

    private static LocalDate relative(String word, LocalDate today) {
        return switch (word) {
            case "hoje" -> today;
            case "amanha" -> today.plusDays(1);
            default -> today.plusDays(2);
        };
    }

    private static DateMention numeric(MatchResult match, LocalDate today) {
        return resolve(Integer.parseInt(match.group("day")), Integer.parseInt(match.group("month")),
                fullYear(match.group("year")), today);
    }

    private static DateMention written(MatchResult match, LocalDate today) {
        return resolve(Integer.parseInt(match.group("day")), MONTHS.get(match.group("month")),
                fullYear(match.group("year")), today);
    }

    private static Integer fullYear(String year) {
        if (year == null) {
            return null;
        }
        int value = Integer.parseInt(year);
        return year.length() == 2 ? 2000 + value : value;
    }

    private static DateMention resolve(int day, int month, Integer explicitYear, LocalDate today) {
        int year = explicitYear == null ? today.getYear() : explicitYear;
        if (!isValid(year, month, day)) {
            return DateMention.problem(DateIssue.INVALID);
        }
        LocalDate date = LocalDate.of(year, month, day);
        if (explicitYear == null && date.isBefore(today.minusDays(RECENT_PAST_DAYS))) {
            return isValid(year + 1, month, day)
                    ? DateMention.of(date.plusYears(1))
                    : DateMention.problem(DateIssue.INVALID);
        }
        return DateMention.of(date);
    }

    private static LocalDate weekday(MatchResult match, LocalDate today) {
        DayOfWeek day = WEEKDAYS.get(match.group("day"));
        if (match.group("nextWeek") != null) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(day));
        }
        boolean strictlyAfterToday = match.group("next") != null || match.group("coming") != null;
        return today.with(strictlyAfterToday ? TemporalAdjusters.next(day) : TemporalAdjusters.nextOrSame(day));
    }

    /** "dia 20": neste mês se ainda não passou, senão no próximo. */
    private static DateMention dayOfMonth(int day, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        if (day < today.getDayOfMonth()) {
            month = month.plusMonths(1);
        }
        return month.isValidDay(day) ? DateMention.of(month.atDay(day)) : DateMention.problem(DateIssue.INVALID);
    }

    /** Quando o atendente perguntou o dia, um "20" sozinho é o dia 20. */
    private static Optional<Found> bareDayOfMonth(Spans spans, LocalDate today) {
        String remaining = spans.remaining();
        if (!WHOLE_NUMBER.matcher(remaining).matches()) {
            return Optional.empty();
        }
        int day = Integer.parseInt(remaining);
        if (day < 1 || day > 31) {
            return Optional.empty();
        }
        spans.consumeRemaining();
        return Optional.of(new Found(0, dayOfMonth(day, today)));
    }

    private static DateMention withinReach(DateMention mention, LocalDate today, int maxDaysAhead) {
        if (mention.date() == null) {
            return mention;
        }
        if (mention.date().isBefore(today)) {
            return DateMention.problem(DateIssue.PAST);
        }
        if (mention.date().isAfter(today.plusDays(maxDaysAhead))) {
            return DateMention.problem(DateIssue.TOO_FAR);
        }
        return mention;
    }

    private static boolean isValid(int year, int month, int day) {
        return month >= 1 && month <= 12 && day >= 1 && YearMonth.of(year, month).isValidDay(day);
    }
}
