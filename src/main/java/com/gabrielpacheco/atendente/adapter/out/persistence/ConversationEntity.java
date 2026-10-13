package com.gabrielpacheco.atendente.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A linha da conversa: o estado vai como JSONB, escrito e lido pelo {@link ConversationStateJson}. */
@Entity
@Table(name = "conversations")
class ConversationEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String state;

    @Column(nullable = false)
    private short misunderstandings;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    void update(String state, int misunderstandings, Instant updatedAt) {
        this.state = state;
        this.misunderstandings = (short) misunderstandings;
        this.updatedAt = updatedAt;
    }

    Long customerId() {
        return customerId;
    }

    String state() {
        return state;
    }

    int misunderstandings() {
        return misunderstandings;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
