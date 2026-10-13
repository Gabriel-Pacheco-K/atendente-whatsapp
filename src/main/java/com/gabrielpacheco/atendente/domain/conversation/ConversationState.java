package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.nlu.Expectation;

/**
 * Em que passo a conversa está. Cada estado guarda o que o cliente viu (as opções numeradas), para que "2"
 * signifique exatamente o que foi mostrado, mesmo que o catálogo mude no meio do caminho.
 */
public sealed interface ConversationState permits Idle, ChoosingService, ChoosingProfessional, ChoosingDate,
        ChoosingSlot, AskingName, ConfirmingBooking, ChoosingBookingToCancel, ConfirmingCancellation {

    Expectation expectation();

    /** O nome do estado nos logs, nos bastidores e no JSON gravado. */
    String code();

    /** Os passos de um agendamento em andamento, em que "cancelar" é desistir. */
    default boolean inBookingFlow() {
        return switch (this) {
            case ChoosingService s -> true;
            case ChoosingProfessional s -> true;
            case ChoosingDate s -> true;
            case ChoosingSlot s -> true;
            case AskingName s -> true;
            case ConfirmingBooking s -> true;
            case Idle s -> false;
            case ChoosingBookingToCancel s -> false;
            case ConfirmingCancellation s -> false;
        };
    }
}
