package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.Objects;

/** {@code cancelAttempted} marca um cancelamento já pedido à agenda, cujo resultado ficou desconhecido. */
public record ConfirmingCancellation(BookingRef booking, boolean cancelAttempted) implements ConversationState {

    public ConfirmingCancellation {
        Objects.requireNonNull(booking, "booking");
    }

    public ConfirmingCancellation(BookingRef booking) {
        this(booking, false);
    }

    @Override
    public Expectation expectation() {
        return Expectation.YES_NO;
    }

    @Override
    public String code() {
        return "CONFIRMING_CANCELLATION";
    }
}
