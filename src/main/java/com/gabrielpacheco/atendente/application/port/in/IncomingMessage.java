package com.gabrielpacheco.atendente.application.port.in;

import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;

import java.time.Instant;

/**
 * Uma mensagem recebida. {@code messageId} é obrigatório nos dois canais: o wamid no WhatsApp e o
 * clientMessageId (UUID) na web. É ele que torna o reenvio seguro.
 */
public record IncomingMessage(CustomerRef customer, MessageKind kind, String text, String messageId, Instant sentAt) {
}
