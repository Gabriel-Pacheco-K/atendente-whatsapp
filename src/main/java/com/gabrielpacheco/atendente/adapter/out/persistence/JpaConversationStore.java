package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.application.port.out.ConversationStore;
import com.gabrielpacheco.atendente.domain.conversation.Conversation;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * A trava por cliente: a linha da conversa é lida com {@code FOR UPDATE} e fica presa até o fim da transação do
 * turno. É ela que serializa um "sim" repetido, mesmo vindo de vários pedidos ao mesmo tempo.
 */
@Component
class JpaConversationStore implements ConversationStore {

    private final ConversationRepository repository;
    private final ConversationStateJson stateJson;
    private final JdbcClient jdbc;
    private final Clock clock;

    JpaConversationStore(ConversationRepository repository, ConversationStateJson stateJson, JdbcClient jdbc,
                         Clock clock) {
        this.repository = repository;
        this.stateJson = stateJson;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Conversation lock(long customerId) {
        jdbc.sql("""
                        INSERT INTO conversations (customer_id, state, misunderstandings, updated_at)
                        VALUES (:customerId, CAST(:idle AS jsonb), 0, :now)
                        ON CONFLICT (customer_id) DO NOTHING""")
                .param("customerId", customerId)
                .param("idle", stateJson.write(Idle.EMPTY))
                .param("now", Timestamps.of(clock.instant()))
                .update();
        ConversationEntity row = repository.findForUpdate(customerId).orElseThrow();
        return new Conversation(customerId, stateJson.read(row.state()), row.misunderstandings(), row.updatedAt());
    }

    /** A linha já está no contexto de persistência desde a trava; o UPDATE sai no commit. */
    @Override
    public void save(Conversation conversation) {
        ConversationEntity row = repository.findById(conversation.customerId()).orElseThrow();
        row.update(stateJson.write(conversation.state()), conversation.misunderstandings(), conversation.updatedAt());
    }
}
