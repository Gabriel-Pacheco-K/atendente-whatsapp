package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.List;

/** O cliente escolhe pelo número da própria lista; o id do agendamento nunca vem do texto. */
public record ChoosingBookingToCancel(List<BookingRef> options) implements ConversationState {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 5;

    public ChoosingBookingToCancel {
        options = List.copyOf(options);
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(
                    "A escolha do que cancelar mostra de " + MIN_OPTIONS + " a " + MAX_OPTIONS + " agendamentos");
        }
    }

    @Override
    public Expectation expectation() {
        return Expectation.OPTION;
    }

    @Override
    public String code() {
        return "CHOOSING_BOOKING_TO_CANCEL";
    }
}
