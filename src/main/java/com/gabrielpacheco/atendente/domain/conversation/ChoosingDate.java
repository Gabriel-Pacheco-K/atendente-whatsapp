package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.Objects;

public record ChoosingDate(BookingDraft draft) implements ConversationState {

    public ChoosingDate {
        Objects.requireNonNull(draft, "draft");
    }

    @Override
    public Expectation expectation() {
        return Expectation.DATE;
    }

    @Override
    public String code() {
        return "CHOOSING_DATE";
    }
}
