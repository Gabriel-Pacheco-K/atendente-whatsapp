package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

/** {@code suggestedName} é o primeiro nome do perfil do WhatsApp, oferecido como opção 1; pode ser nulo. */
public record AskingName(BookingDraft draft, String suggestedName) implements ConversationState {

    public AskingName {
        if (draft == null || draft.chosen() == null) {
            throw new IllegalArgumentException("O nome só é pedido depois de escolhido o horário");
        }
    }

    @Override
    public Expectation expectation() {
        return Expectation.NAME;
    }

    @Override
    public String code() {
        return "ASKING_NAME";
    }
}
