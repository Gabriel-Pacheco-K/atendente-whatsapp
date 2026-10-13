package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.LastBooking;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

/** Fora de qualquer fluxo. {@code lastBooking} lembra o agendamento recém-feito, ou é nulo. */
public record Idle(LastBooking lastBooking) implements ConversationState {

    public static final Idle EMPTY = new Idle(null);

    @Override
    public Expectation expectation() {
        return Expectation.NONE;
    }

    @Override
    public String code() {
        return "IDLE";
    }
}
