package com.gabrielpacheco.atendente.domain.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Esconde telefones antes de qualquer log ou envio para fora. */
public final class PersonalDataMasker {

    private static final Pattern PHONE_LIKE = Pattern.compile("\\+?\\d[\\d ()-]{6,}\\d");
    private static final int MIN_PHONE_DIGITS = 9;
    private static final String HIDDEN = "****";

    private PersonalDataMasker() {
    }

    /** "+55 48 9****-0000" para celulares brasileiros; "****" e os 4 últimos dígitos nos demais casos. */
    public static String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) {
            return "+55 " + digits.substring(2, 4) + " " + digits.charAt(4) + HIDDEN + "-" + lastFour(digits);
        }
        return HIDDEN + lastFour(digits);
    }

    /** Troca por "[número]" toda sequência com 9 ou mais dígitos; datas e horas ficam intactas. */
    public static String scrubPhones(String text) {
        return PHONE_LIKE.matcher(text).replaceAll(match -> countDigits(match.group()) >= MIN_PHONE_DIGITS
                ? "[número]"
                : Matcher.quoteReplacement(match.group()));
    }

    private static String lastFour(String digits) {
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : "";
    }

    private static long countDigits(String text) {
        return text.chars().filter(Character::isDigit).count();
    }
}
