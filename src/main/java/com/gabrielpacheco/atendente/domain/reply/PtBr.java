package com.gabrielpacheco.atendente.domain.reply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Formatação em português feita à mão: o resultado não depende dos dados de locale da JVM
 * (que usam espaço não separável em "R$ 45,00", por exemplo).
 */
public final class PtBr {

    private static final List<String> DAY_NAMES =
            List.of("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo");
    private static final List<String> DAY_PLURALS =
            List.of("segundas", "terças", "quartas", "quintas", "sextas", "sábados", "domingos");
    private static final List<String> DAY_ABBREVIATIONS = List.of("seg", "ter", "qua", "qui", "sex", "sáb", "dom");

    private PtBr() {
    }

    /** "R$ 45,00" e "R$ 1.200,00", com espaço comum. */
    public static String money(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        String plain = rounded.abs().toPlainString();
        String integerPart = plain.substring(0, plain.length() - 3);
        String cents = plain.substring(plain.length() - 2);
        return (rounded.signum() < 0 ? "-" : "") + "R$ " + groupThousands(integerPart) + "," + cents;
    }

    public static String time(LocalTime time) {
        return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }

    /** "segunda", "terça", ..., "sábado", "domingo". */
    public static String dayName(DayOfWeek day) {
        return DAY_NAMES.get(day.ordinal());
    }

    /** "14/10". */
    public static String dayMonth(LocalDate date) {
        return String.format(Locale.ROOT, "%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    /** "quarta-feira, 14/10" ou "sábado, 17/10". */
    public static String longDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        String weekday = isWeekend(day) ? dayName(day) : dayName(day) + "-feira";
        return weekday + ", " + dayMonth(date);
    }

    /** "qua 14/10 15:00", para rótulos curtos. */
    public static String shortDateTime(LocalDateTime dateTime) {
        return DAY_ABBREVIATIONS.get(dateTime.getDayOfWeek().ordinal()) + " " + dayMonth(dateTime.toLocalDate())
                + " " + time(dateTime.toLocalTime());
    }

    /** "na" antes de dia útil e "no" antes de sábado e domingo. */
    public static String preposition(DayOfWeek day) {
        return isWeekend(day) ? "no" : "na";
    }

    /** "na quarta-feira, 14/10" ou "no sábado, 17/10". */
    public static String onDate(LocalDate date) {
        return preposition(date.getDayOfWeek()) + " " + longDate(date);
    }

    /** "na quarta" ou "no sábado". */
    public static String onWeekday(DayOfWeek day) {
        return preposition(day) + " " + dayName(day);
    }

    /** "A quarta-feira, 14/10 já está lotada" ou "O sábado, 17/10 já está lotado". */
    public static String fullDay(LocalDate date) {
        return isWeekend(date.getDayOfWeek())
                ? "O " + longDate(date) + " já está lotado"
                : "A " + longDate(date) + " já está lotada";
    }

    /** "às segundas" ou "aos sábados"; com maiúscula quando abre a frase. */
    public static String weekdayPlural(DayOfWeek day, boolean capitalized) {
        String text = (isWeekend(day) ? "aos " : "às ") + DAY_PLURALS.get(day.ordinal());
        return capitalized ? capitalize(text) : text;
    }

    /** "a", "a e b", "a, b e c". */
    public static String joinWithAnd(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.get(items.size() - 1);
    }

    public static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static String groupThousands(String digits) {
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append('.');
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
