package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.conversation.Conversation;

public interface ConversationStore {

    /**
     * Devolve a conversa travada até o fim da transação, criando-a se faltar. Mensagens do mesmo cliente são
     * processadas uma de cada vez.
     */
    Conversation lock(long customerId);

    void save(Conversation conversation);
}
