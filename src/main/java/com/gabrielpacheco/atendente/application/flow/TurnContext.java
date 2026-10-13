package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.conversation.Conversation;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;

import java.time.Instant;
import java.time.LocalDateTime;

/** Tudo o que um passo da conversa precisa saber sobre o turno atual. */
public record TurnContext(Conversation conversation, Customer customer, IncomingMessage message,
                          Interpretation interpretation, Catalog catalog, Instant now, LocalDateTime localNow,
                          boolean expired, Deadline deadline) {

    /** O estado que vale neste turno: uma conversa expirada recomeça do início. */
    public ConversationState state() {
        return expired ? Idle.EMPTY : conversation.state();
    }

    /** O Idle para onde voltar, mantendo o último agendamento quando a conversa já está no início. */
    public Idle idle() {
        return state() instanceof Idle idle ? idle : Idle.EMPTY;
    }

    public Intent intent() {
        return interpretation.intent();
    }

    public Entities entities() {
        return interpretation.entities();
    }
}
