package com.gabrielpacheco.atendente.application.flow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * O prazo do turno. O turno segura a trava da conversa e uma conexão do banco, então cada chamada à agenda só
 * começa se ainda couber inteira no tempo que resta.
 */
public final class Deadline {

    private final Clock clock;
    private final Instant expiresAt;

    private Deadline(Clock clock, Instant expiresAt) {
        this.clock = clock;
        this.expiresAt = expiresAt;
    }

    public static Deadline after(Duration budget, Clock clock) {
        return new Deadline(clock, clock.instant().plus(budget));
    }

    /** Se sobra mais tempo do que {@code worstCase}, o quanto a próxima chamada pode levar. */
    public boolean allows(Duration worstCase) {
        return Duration.between(clock.instant(), expiresAt).compareTo(worstCase) > 0;
    }
}
