package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "tanto faz", "qualquer um", "quem tiver": o cliente aceita qualquer profissional. */
final class AnyProfessionalExtractor {

    private static final Pattern ANY_PROFESSIONAL = Pattern.compile(
            "\\b(?:tanto faz|qualquer uma?|quem tiver|indiferente|qualquer(?!\\s+(?:dia|data|hora|horario)))\\b");

    boolean extract(Spans spans) {
        Matcher matcher = ANY_PROFESSIONAL.matcher(spans.text());
        if (!matcher.find()) {
            return false;
        }
        spans.consume(matcher.start(), matcher.end());
        return true;
    }
}
