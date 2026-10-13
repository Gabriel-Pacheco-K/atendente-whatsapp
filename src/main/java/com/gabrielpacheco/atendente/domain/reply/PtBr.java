package com.gabrielpacheco.atendente.domain.reply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
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

    /** "quarta-feira, 14/10" ou "sábado, 17/10". */
    public static String longDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        String weekday = isWeekend(day) ? dayName(day) : dayName(day) + "-feira";
        return weekday + ", " + String.format(Locale.ROOT, "%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    /** "na quarta-feira, 14/10" ou "no sábado, 17/10". */
    public static String onDate(LocalDate date) {
        return (isWeekend(date.getDayOfWeek()) ? "no " : "na ") + longDate(date);
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
