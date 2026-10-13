package com.gabrielpacheco.atendente.domain.conversation;

import java.time.Duration;
import java.time.Instant;

/** O estado da conversa de um cliente e quantas mensagens seguidas não foram entendidas. */
public final class Conversation {

    private final long customerId;
    private ConversationState state;
    private int misunderstandings;
    private Instant updatedAt;

    public Conversation(long customerId, ConversationState state, int misunderstandings, Instant updatedAt) {
        if (misunderstandings < 0) {
            throw new IllegalArgumentException("O contador de mensagens não entendidas não pode ser negativo");
        }
        this.customerId = customerId;
        this.state = state;
        this.misunderstandings = misunderstandings;
        this.updatedAt = updatedAt;
    }

    /** Uma conversa parada no meio de um passo perde a validade; no início ela nunca expira. */
    public boolean isExpired(Instant now, Duration ttl) {
        return !(state instanceof Idle) && updatedAt.plus(ttl).isBefore(now);
    }

    public void moveTo(ConversationState next, Instant now) {
        this.state = next;
        this.updatedAt = now;
    }

    public int registerMisunderstanding() {
        return ++misunderstandings;
    }

    public void clearMisunderstandings() {
        misunderstandings = 0;
    }

    public long customerId() {
        return customerId;
    }

    public ConversationState state() {
        return state;
    }

    public int misunderstandings() {
        return misunderstandings;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
