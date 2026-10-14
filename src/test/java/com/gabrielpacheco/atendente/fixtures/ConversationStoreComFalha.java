package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.out.ConversationStore;
import com.gabrielpacheco.atendente.domain.conversation.Conversation;

import java.util.concurrent.atomic.AtomicReference;

/** A trava de conversa de verdade, que o teste pode mandar falhar uma vez, como um banco que caiu no meio do turno. */
public final class ConversationStoreComFalha implements ConversationStore {

    private final ConversationStore real;
    private final AtomicReference<RuntimeException> proximaFalha = new AtomicReference<>();

    public ConversationStoreComFalha(ConversationStore real) {
        this.real = real;
    }

    public void falharNaProximaTrava(RuntimeException falha) {
        proximaFalha.set(falha);
    }

    public void voltarAoNormal() {
        proximaFalha.set(null);
    }

    @Override
    public Conversation lock(long customerId) {
        RuntimeException falha = proximaFalha.getAndSet(null);
        if (falha != null) {
            throw falha;
        }
        return real.lock(customerId);
    }

    @Override
    public void save(Conversation conversation) {
        real.save(conversation);
    }
}
