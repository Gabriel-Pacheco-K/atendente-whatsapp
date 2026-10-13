package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.PendingAttempt;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

/** O resumo foi mostrado. {@code pendingAttempt} só existe depois de um "sim" com resultado desconhecido. */
public record ConfirmingBooking(BookingDraft draft, PendingAttempt pendingAttempt) implements ConversationState {

    public ConfirmingBooking {
        if (draft == null || draft.chosen() == null) {
            throw new IllegalArgumentException("Só há o que confirmar depois de escolhido o horário");
        }
    }

    @Override
    public Expectation expectation() {
        return Expectation.YES_NO;
    }

    @Override
    public String code() {
        return "CONFIRMING_BOOKING";
    }
}
