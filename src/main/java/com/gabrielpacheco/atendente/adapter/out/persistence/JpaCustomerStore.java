package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.application.port.out.CustomerStore;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Clientes pelo JPA, menos o upsert: o {@code ON CONFLICT} do PostgreSQL resolve a corrida de duas primeiras
 * mensagens simultâneas do mesmo cliente, sem violar a chave única.
 */
@Component
class JpaCustomerStore implements CustomerStore {

    private final CustomerRepository repository;
    private final JdbcClient jdbc;
    private final Clock clock;

    JpaCustomerStore(CustomerRepository repository, JdbcClient jdbc, Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Customer createWeb(String phone) {
        CustomerEntity created = new CustomerEntity(Channel.WEB, UUID.randomUUID().toString(), phone, clock.instant());
        return repository.save(created).toDomain();
    }

    @Override
    public Optional<Customer> findWeb(UUID sessionId) {
        return repository.findByChannelAndExternalId(Channel.WEB, sessionId.toString()).map(CustomerEntity::toDomain);
    }

    /**
     * Cria o cliente na primeira mensagem; nas seguintes, só renova {@code last_seen_at}, base da retenção. O UPDATE
     * vem antes porque todo INSERT, mesmo o que cai no ON CONFLICT, gasta um número da sequência de ids.
     */
    @Override
    public Customer upsert(CustomerRef ref) {
        OffsetDateTime now = Timestamps.of(clock.instant());
        return jdbc.sql("""
                        UPDATE customers SET last_seen_at = :now
                        WHERE channel = :channel AND external_id = :externalId
                        RETURNING id, channel, external_id, phone, name""")
                .param("now", now)
                .param("channel", ref.channel().name())
                .param("externalId", ref.externalId())
                .query(JpaCustomerStore::customer)
                .optional()
                .orElseGet(() -> insert(ref, now));
    }

    @Override
    public void saveName(long customerId, String name) {
        jdbc.sql("UPDATE customers SET name = :name WHERE id = :id")
                .param("name", name)
                .param("id", customerId)
                .update();
    }

    private Customer insert(CustomerRef ref, OffsetDateTime now) {
        return jdbc.sql("""
                        INSERT INTO customers (channel, external_id, phone, created_at, last_seen_at)
                        VALUES (:channel, :externalId, :phone, :now, :now)
                        ON CONFLICT (channel, external_id) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
                        RETURNING id, channel, external_id, phone, name""")
                .param("channel", ref.channel().name())
                .param("externalId", ref.externalId())
                .param("phone", ref.phone())
                .param("now", now)
                .query(JpaCustomerStore::customer)
                .single();
    }

    private static Customer customer(ResultSet row, int rowNumber) throws SQLException {
        return new Customer(row.getLong("id"), Channel.valueOf(row.getString("channel")), row.getString("external_id"),
                row.getString("phone"), row.getString("name"));
    }
}
