package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.application.port.in.ConversationReply;

import java.time.Instant;

/** {@code reply} é nulo no WhatsApp (a resposta já foi enviada) e depois que a resposta da web expira. */
public record ProcessedMessage(Instant processedAt, ConversationReply reply) {
}
