package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.application.port.in.ConversationReply;

import java.util.Optional;

/** Idempotência por cliente, nunca global: o mesmo id em outra sessão é outra mensagem. */
public interface ProcessedMessageStore {

    Optional<ProcessedMessage> find(long customerId, String messageId);

    void register(long customerId, String messageId, ConversationReply replyOrNull);
}
