package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.List;
import java.util.Objects;

public record ChoosingService(BookingDraft draft, List<Long> offeredServiceIds) implements ConversationState {

    public static final int MAX_OPTIONS = 10;

    public ChoosingService {
        Objects.requireNonNull(draft, "draft");
        offeredServiceIds = List.copyOf(offeredServiceIds);
        if (offeredServiceIds.isEmpty() || offeredServiceIds.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A escolha de serviço mostra de 1 a " + MAX_OPTIONS + " opções");
        }
    }

    @Override
    public Expectation expectation() {
        return Expectation.OPTION;
    }

    @Override
    public String code() {
        return "CHOOSING_SERVICE";
    }
}
