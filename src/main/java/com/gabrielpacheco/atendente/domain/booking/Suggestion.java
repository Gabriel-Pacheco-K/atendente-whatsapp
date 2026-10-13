package com.gabrielpacheco.atendente.domain.booking;

import java.time.LocalDate;
import java.util.List;

public sealed interface Suggestion permits Suggestion.ExactMatch, Suggestion.Alternatives, Suggestion.NoSlots {

    /** O horário pedido está livre. */
    record ExactMatch(SlotOption slot) implements Suggestion {
    }

    record Alternatives(List<SlotOption> options, Note note) implements Suggestion {

        public Alternatives {
            options = List.copyOf(options);
        }
    }

    record NoSlots(LocalDate searchedUntil) implements Suggestion {
    }
}
