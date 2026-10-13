package com.gabrielpacheco.atendente.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {

    /** SELECT ... FOR UPDATE: quem chega depois espera até o commit, ou até o lock_timeout da conexão. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.customerId = :customerId")
    Optional<ConversationEntity> findForUpdate(@Param("customerId") long customerId);
}
