package com.gabrielpacheco.atendente.domain.booking;

import java.time.Instant;
import java.time.LocalDateTime;

/** Uma tentativa de agendar cujo resultado ficou desconhecido (timeout): pode ter sido gravada. */
public record PendingAttempt(long professionalId, LocalDateTime startAt, Instant attemptedAt) {

    public boolean isFor(SlotOption slot) {
        return professionalId == slot.professionalId() && startAt.equals(slot.startAt());
    }
}
