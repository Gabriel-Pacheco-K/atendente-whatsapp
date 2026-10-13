package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "customers")
class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 60)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected CustomerEntity() {
    }

    CustomerEntity(Channel channel, String externalId, String phone, Instant now) {
        this.channel = channel;
        this.externalId = externalId;
        this.phone = phone;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    Customer toDomain() {
        return new Customer(id, channel, externalId, phone, name);
    }
}
