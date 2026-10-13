package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;

import java.util.List;
import java.util.Objects;

/** Os profissionais vão de 1 a n, e a opção n+1 é "Tanto faz". */
public record ChoosingProfessional(BookingDraft draft, List<Long> offeredProfessionalIds)
        implements ConversationState {

    public static final int MAX_PROFESSIONALS = 9;

    public ChoosingProfessional {
        Objects.requireNonNull(draft, "draft");
        offeredProfessionalIds = List.copyOf(offeredProfessionalIds);
        if (offeredProfessionalIds.isEmpty() || offeredProfessionalIds.size() > MAX_PROFESSIONALS) {
            throw new IllegalArgumentException(
                    "A escolha de profissional mostra de 1 a " + MAX_PROFESSIONALS + " profissionais");
        }
    }

    public int anyProfessionalOption() {
        return offeredProfessionalIds.size() + 1;
    }

    @Override
    public Expectation expectation() {
        return Expectation.OPTION;
    }

    @Override
    public String code() {
        return "CHOOSING_PROFESSIONAL";
    }
}
