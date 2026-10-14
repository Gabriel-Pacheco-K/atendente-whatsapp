package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "tanto faz", "qualquer um", "quem tiver": o cliente aceita qualquer profissional. Quando o passo pergunta o dia ou
 * pede um sim, "tanto faz" responde a pergunta do passo; ali, só a menção clara a um profissional vale.
 */
final class AnyProfessionalExtractor {

    private static final Pattern ANY_PROFESSIONAL = Pattern.compile("\\b(?:tanto faz|qualquer uma?|quem tiver"
            + "|indiferente|sem preferencia|qualquer(?!\\s+(?:dia|data|hora|horario)))\\b");
    private static final Pattern CLEARLY_A_PROFESSIONAL = Pattern.compile(
            "\\b(?:com qualquer uma?|qualquer (?:barbeiro|barbeira|profissional)|(?:com )?quem tiver)\\b");

    boolean extract(Spans spans, Expectation expectation) {
        boolean answersAnotherQuestion = expectation == Expectation.DATE || expectation == Expectation.YES_NO;
        Matcher matcher = (answersAnotherQuestion ? CLEARLY_A_PROFESSIONAL : ANY_PROFESSIONAL).matcher(spans.text());
        if (!matcher.find()) {
            return false;
        }
        spans.consume(matcher.start(), matcher.end());
        return true;
    }
}
