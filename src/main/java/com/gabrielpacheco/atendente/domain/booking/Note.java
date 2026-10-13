package com.gabrielpacheco.atendente.domain.booking;

/** Por que as opções oferecidas não são exatamente o que o cliente pediu. */
public enum Note {
    NONE, REQUESTED_TAKEN, PERIOD_FULL, MOVED_TO_NEXT_DAY, OUTSIDE_HOURS, TOO_SOON
}
