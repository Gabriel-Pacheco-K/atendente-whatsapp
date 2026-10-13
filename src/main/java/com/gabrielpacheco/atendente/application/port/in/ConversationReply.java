package com.gabrielpacheco.atendente.application.port.in;

import com.gabrielpacheco.atendente.domain.reply.Reply;

import java.util.List;

/** As respostas de um turno. {@code duplicate} indica uma mensagem que já tinha sido processada. */
public record ConversationReply(List<Reply> replies, Diagnostics diagnostics, boolean duplicate) {

    public ConversationReply {
        replies = List.copyOf(replies);
    }

    public ConversationReply asDuplicate() {
        return new ConversationReply(replies, diagnostics, true);
    }
}
