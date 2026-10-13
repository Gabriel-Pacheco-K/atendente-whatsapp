package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.out.ConversationStore;
import com.gabrielpacheco.atendente.domain.conversation.Conversation;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Idle;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Devolve cópias, como um banco: o que não passa por {@link #save} não fica gravado. */
public final class InMemoryConversationStore implements ConversationStore {

    private final Map<Long, Conversation> conversas = new HashMap<>();
    private final Clock relogio;

    public InMemoryConversationStore(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public synchronized Conversation lock(long customerId) {
        return copia(conversas.computeIfAbsent(customerId,
                id -> new Conversation(id, Idle.EMPTY, 0, relogio.instant())));
    }

    @Override
    public synchronized void save(Conversation conversation) {
        conversas.put(conversation.customerId(), copia(conversation));
    }

    public synchronized ConversationState estado(long customerId) {
        Conversation conversa = conversas.get(customerId);
        return conversa == null ? Idle.EMPTY : conversa.state();
    }

    private static Conversation copia(Conversation c) {
        return new Conversation(c.customerId(), c.state(), c.misunderstandings(), c.updatedAt());
    }
}
