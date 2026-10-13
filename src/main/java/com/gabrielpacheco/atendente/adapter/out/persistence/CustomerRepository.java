package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.domain.conversation.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByChannelAndExternalId(Channel channel, String externalId);
}
