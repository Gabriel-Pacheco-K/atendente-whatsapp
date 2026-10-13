package com.gabrielpacheco.atendente.domain.reply;

import java.util.List;

/** Um balão de resposta. {@code quickReplies} vira botões na web; no WhatsApp as opções já vêm numeradas no texto. */
public record Reply(String text, List<String> quickReplies) {

    public Reply {
        quickReplies = List.copyOf(quickReplies);
    }

    public static Reply of(String text) {
        return new Reply(text, List.of());
    }

    public Reply prefixedWith(String prefix) {
        return new Reply(prefix + text, quickReplies);
    }
}
