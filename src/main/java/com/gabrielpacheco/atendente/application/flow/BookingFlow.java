package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.conversation.AskingName;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingService;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * O que cada passo do agendamento aceita. Uma resposta vazia quer dizer que a mensagem não serve para o passo,
 * e o roteador tenta as digressões (uma pergunta de preço, um "oi") antes de dizer que não entendeu.
 */
@Component
public class BookingFlow {

    private final BookingGaps gaps;
    private final BookingConfirmation confirmation;
    private final BusinessProfile business;

    public BookingFlow(BookingGaps gaps, BookingConfirmation confirmation, BusinessProfile business) {
        this.gaps = gaps;
        this.confirmation = confirmation;
        this.business = business;
    }

    public StepResult start(TurnContext t, BookingDraft draft) {
        if (!BookingGaps.isUsable(t.catalog())) {
            return StepResult.of(t.idle(), Texts.schedulingOffline(business.humanContactPhone()));
        }
        return confirmation.limitReached(t).orElseGet(() -> gaps.advance(t, draft, ""));
    }

    public Optional<StepResult> onChoosingService(TurnContext t, ChoosingService state) {
        Integer option = t.entities().option();
        if (option != null && option <= state.offeredServiceIds().size()) {
            return advance(t, state.draft().withService(state.offeredServiceIds().get(option - 1)));
        }
        if (t.entities().hasBookingData()) {
            return advance(t, state.draft().merge(t.entities()));
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(state, Texts.denyInFlow()));
        }
        return Optional.empty();
    }

    public Optional<StepResult> onChoosingProfessional(TurnContext t, ChoosingProfessional state) {
        Integer option = t.entities().option();
        if (option != null && option <= state.offeredProfessionalIds().size()) {
            return advance(t, state.draft().withProfessional(state.offeredProfessionalIds().get(option - 1)));
        }
        if (option != null && option == state.anyProfessionalOption()) {
            return advance(t, state.draft().withAnyProfessional());
        }
        if (t.entities().hasBookingData()) {
            return advance(t, state.draft().merge(t.entities()));
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(state, Texts.noProfessionalPreference()));
        }
        return Optional.empty();
    }

    public Optional<StepResult> onChoosingDate(TurnContext t, ChoosingDate state) {
        if (t.entities().hasBookingData()) {
            return advance(t, state.draft().merge(t.entities()));
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(state, Texts.denyInFlow()));
        }
        return Optional.empty();
    }

    /** Um número escolhe a opção; um horário igual ao de uma opção também; outro horário é conferido de novo. */
    public Optional<StepResult> onChoosingSlot(TurnContext t, ChoosingSlot state) {
        Entities found = t.entities();
        Integer option = found.option();
        if (option != null && option <= state.options().size()) {
            return advance(t, state.draft().withChosen(state.options().get(option - 1)));
        }
        Optional<SlotOption> sameTime = offeredAtRequestedTime(state, found);
        if (sameTime.isPresent()) {
            return advance(t, state.draft().withChosen(sameTime.get()));
        }
        if (found.hasBookingData()) {
            return advance(t, state.draft().merge(found));
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(state, Texts.denyInFlow()));
        }
        return Optional.empty();
    }

    /**
     * Primeiro a resposta à sugestão de nome; depois uma correção do agendamento, que o interpretador só entrega
     * com uma pista clara; por último o nome digitado, que não vale junto com outro pedido ("tchau", "menu").
     */
    public Optional<StepResult> onAskingName(TurnContext t, AskingName state) {
        Entities found = t.entities();
        String suggested = state.suggestedName();
        if (suggested != null) {
            boolean plainYes = t.intent() == Intent.AFFIRM && !found.yesNoConflict() && !found.hasBookingData();
            if (plainYes || Objects.equals(found.option(), 1)) {
                return Optional.of(gaps.summarizeWithName(t, state.draft(), suggested));
            }
            if (Objects.equals(found.option(), 2)) {
                return Optional.of(StepResult.of(new AskingName(state.draft(), null), Texts.askName()));
            }
        }
        if (found.hasBookingData()) {
            return advance(t, state.draft().merge(found));
        }
        boolean onlyTheName = t.intent() == Intent.UNKNOWN || t.intent() == Intent.GREETING;
        if (found.personName() != null && onlyTheName) {
            return Optional.of(gaps.summarizeWithName(t, state.draft(), found.personName()));
        }
        return Optional.empty();
    }

    /**
     * "sim" agenda; "não" volta para a escolha do dia; dados novos ("sim, mas às 16h") ou outro nome ("meu nome é
     * Ana") corrigem o resumo. Um "sim" que só repete os dados do resumo ("sim, amanhã às 15h") continua um sim.
     */
    public Optional<StepResult> onConfirmingBooking(TurnContext t, ConfirmingBooking state) {
        Entities found = t.entities();
        if (found.yesNoConflict()) {
            return Optional.of(StepResult.of(state, Texts.yesOrNo()));
        }
        if (found.personName() != null) {
            return Optional.of(gaps.summarizeWithName(t, state.draft(), found.personName()));
        }
        if (found.hasBookingData()) {
            BookingDraft merged = state.draft().merge(found);
            if (t.intent() == Intent.AFFIRM && merged.equals(state.draft())) {
                return Optional.of(confirmation.confirm(t, state));
            }
            return advance(t, merged);
        }
        if (t.intent() == Intent.DENY) {
            return Optional.of(StepResult.of(new ChoosingDate(state.draft().withoutDateAndTime()), Texts.declined()));
        }
        if (t.intent() == Intent.AFFIRM) {
            return Optional.of(confirmation.confirm(t, state));
        }
        return Optional.empty();
    }

    private Optional<StepResult> advance(TurnContext t, BookingDraft draft) {
        return Optional.of(gaps.advance(t, draft, ""));
    }

    private static Optional<SlotOption> offeredAtRequestedTime(ChoosingSlot state, Entities found) {
        if (!(found.time() instanceof TimePreference.Exact exact) || found.date() != null
                || found.serviceId() != null) {
            return Optional.empty();
        }
        return state.options().stream()
                .filter(slot -> slot.startAt().toLocalTime().equals(exact.time()))
                .filter(slot -> found.professionalId() == null || slot.professionalId() == found.professionalId())
                .findFirst();
    }
}
