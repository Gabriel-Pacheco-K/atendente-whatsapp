package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ChoosingSlot(BookingDraft draft, LocalDate date, List<SlotOption> options) implements ConversationState {

    public static final int MAX_OPTIONS = 6;

    public ChoosingSlot {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(date, "date");
        options = List.copyOf(options);
        if (options.isEmpty() || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A lista de horários tem de 1 a " + MAX_OPTIONS + " opções");
        }
    }

    @Override
    public Expectation expectation() {
        return Expectation.OPTION;
    }

    @Override
    public String code() {
        return "CHOOSING_SLOT";
    }
}
