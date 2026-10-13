package com.gabrielpacheco.atendente.domain.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Abreviações comuns de chat. "ter" e "qua" ficam de fora de propósito: podem ser o verbo e o pronome.
 */
final class Abbreviations {

    private static final Map<String, String> BY_TOKEN = Map.ofEntries(
            entry("vc", "voce"), entry("vcs", "voces"),
            entry("q", "que"), entry("oq", "o que"), entry("pq", "porque"),
            entry("hj", "hoje"), entry("amn", "amanha"), entry("amnh", "amanha"), entry("dps", "depois"),
            entry("td", "tudo"), entry("tb", "tambem"), entry("tbm", "tambem"), entry("msm", "mesmo"),
            entry("agr", "agora"), entry("blz", "beleza"),
            entry("obg", "obrigado"), entry("obgd", "obrigado"), entry("brigado", "obrigado"), entry("vlw", "valeu"),
            entry("qro", "quero"), entry("qr", "quero"), entry("kero", "quero"),
            entry("pra", "para"), entry("pro", "para"), entry("p", "para"), entry("c", "com"),
            entry("qnd", "quando"), entry("qdo", "quando"),
            entry("qto", "quanto"), entry("qt", "quanto"), entry("qnt", "quanto"),
            entry("hr", "hora"), entry("hrs", "hora"), entry("hs", "hora"),
            entry("sab", "sabado"), entry("seg", "segunda"), entry("qui", "quinta"), entry("sex", "sexta"),
            entry("dom", "domingo"),
            entry("eae", "e ai"), entry("eai", "e ai"), entry("iae", "e ai"));

    /** "s" e "n" só são sim e não quando são a mensagem inteira. */
    private static final Map<String, String> WHOLE_MESSAGE = Map.of("s", "sim", "n", "nao");

    private Abbreviations() {
    }

    static List<String> expand(List<String> tokens) {
        if (tokens.size() == 1 && WHOLE_MESSAGE.containsKey(tokens.get(0))) {
            return List.of(WHOLE_MESSAGE.get(tokens.get(0)));
        }
        List<String> expanded = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            expanded.addAll(List.of(BY_TOKEN.getOrDefault(token, token).split(" ")));
        }
        return expanded;
    }
}
