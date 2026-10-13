package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.Objects;

public record ConfirmingCancellation(BookingRef booking) implements ConversationState {

    public ConfirmingCancellation {
        Objects.requireNonNull(booking, "booking");
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
