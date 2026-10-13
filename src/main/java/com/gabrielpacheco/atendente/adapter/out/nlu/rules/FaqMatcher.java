package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.business.Faq;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Escolhe a pergunta frequente com mais palavras-chave no texto; no empate, vale a ordem do arquivo. */
final class FaqMatcher {

    record FaqHit(String faqId, int keywords) {
    }

    Optional<FaqHit> match(String correctedText, List<Faq> faqs) {
        FaqHit best = null;
        for (Faq faq : faqs) {
            int keywords = (int) faq.keywords().stream()
                    .map(keyword -> TextNormalizer.normalize(keyword).text())
                    .filter(keyword -> !keyword.isEmpty() && containsPhrase(correctedText, keyword))
                    .count();
            if (keywords > 0 && (best == null || keywords > best.keywords())) {
                best = new FaqHit(faq.id(), keywords);
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean containsPhrase(String text, String phrase) {
        return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(text).find();
    }
}
