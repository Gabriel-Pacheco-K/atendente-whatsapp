package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

/** O que veio de uma chamada ao Claude. Os motivos são curtos e nunca trazem o conteúdo da conversa. */
public sealed interface ClaudeResult {

    record Success(ClaudeExtraction extraction) implements ClaudeResult {
    }

    /** O Claude respondeu, mas sem uma interpretação: recusa, limite de tokens ou nenhum conteúdo. */
    record NotUsable(String reason) implements ClaudeResult {
    }

    /** A chamada falhou: rede, timeout, erro HTTP ou saída fora do esquema. */
    record Failed(String reason, Exception cause) implements ClaudeResult {
    }
}
