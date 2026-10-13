package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessage;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessageStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Idempotência por cliente: a chave é (cliente, id da mensagem). Gravada na mesma transação do turno, então um
 * turno desfeito também desfaz o registro, e o reenvio é processado de novo.
 */
@Component
class JdbcProcessedMessageStore implements ProcessedMessageStore {

    private final JdbcClient jdbc;
    private final ReplyJson replyJson;
    private final Clock clock;

    JdbcProcessedMessageStore(JdbcClient jdbc, ReplyJson replyJson, Clock clock) {
        this.jdbc = jdbc;
        this.replyJson = replyJson;
        this.clock = clock;
    }

    @Override
    public Optional<ProcessedMessage> find(long customerId, String messageId) {
        return jdbc.sql("""
                        SELECT processed_at, reply::text AS reply FROM processed_messages
                        WHERE customer_id = :customerId AND message_id = :messageId""")
                .param("customerId", customerId)
                .param("messageId", messageId)
                .query((row, rowNumber) -> {
                    String reply = row.getString("reply");
                    return new ProcessedMessage(row.getObject("processed_at", OffsetDateTime.class).toInstant(),
                            reply == null ? null : replyJson.read(reply));
                })
                .optional();
    }

    @Override
    public void register(long customerId, String messageId, ConversationReply replyOrNull) {
        jdbc.sql("""
                        INSERT INTO processed_messages (customer_id, message_id, reply, processed_at)
                        VALUES (:customerId, :messageId, CAST(:reply AS jsonb), :now)
                        ON CONFLICT DO NOTHING""")
                .param("customerId", customerId)
                .param("messageId", messageId)
                .param("reply", replyOrNull == null ? null : replyJson.write(replyOrNull), Types.VARCHAR)
                .param("now", Timestamps.of(clock.instant()))
                .update();
    }
}
