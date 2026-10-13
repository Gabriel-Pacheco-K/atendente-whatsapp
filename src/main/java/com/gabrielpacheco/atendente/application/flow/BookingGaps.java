package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.CatalogProvider;
import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.DayAvailability;
import com.gabrielpacheco.atendente.domain.booking.SlotSuggester;
import com.gabrielpacheco.atendente.domain.booking.Suggestion;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.conversation.AskingName;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingService;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.conversation.PersonName;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Decide a próxima pergunta do agendamento: só o que ainda falta no rascunho, na ordem serviço, profissional, dia,
 * horário e nome. Quando tudo está preenchido, mostra o resumo.
 */
@Component
public class BookingGaps {

    private static final Logger log = LoggerFactory.getLogger(BookingGaps.class);

    private final AvailabilityFinder availability;
    private final CatalogProvider catalogProvider;
    private final BusinessProfile business;
    private final ConversationSettings settings;
    private final SlotSuggester suggester = new SlotSuggester();

    public BookingGaps(AvailabilityFinder availability, CatalogProvider catalogProvider, BusinessProfile business,
                       ConversationSettings settings) {
        this.availability = availability;
        this.catalogProvider = catalogProvider;
        this.business = business;
        this.settings = settings;
    }

    /** O catálogo permite agendar: a agenda respondeu e tem serviços e profissionais. */
    static boolean isUsable(Catalog catalog) {
        return catalog.online() && !catalog.services().isEmpty() && !catalog.professionals().isEmpty();
    }

    /** Vai para a primeira lacuna do rascunho; {@code prefix} vai na frente da primeira resposta. */
    public StepResult advance(TurnContext t, BookingDraft draft, String prefix) {
        Catalog catalog = t.catalog();
        if (!isUsable(catalog)) {
            return StepResult.of(t.state(), Texts.schedulingOffline(business.humanContactPhone()));
        }
        if (t.entities().dateIssue() != null) {
            draft = draft.withDate(null);
        }
        Optional<ServiceType> service = Optional.ofNullable(draft.serviceId()).flatMap(catalog::service);
        if (service.isEmpty()) {
            return askService(catalog, draft).prefixed(prefix);
        }
        if (!draft.anyProfessional() && professionalOf(draft, catalog).isEmpty()) {
            if (catalog.professionals().size() > 1) {
                return askProfessional(catalog, draft).prefixed(prefix);
            }
            draft = draft.withProfessional(catalog.professionals().get(0).id());
        }
        if (draft.date() == null) {
            return StepResult.of(new ChoosingDate(draft), askDate(t)).prefixed(prefix);
        }
        if (draft.chosen() == null) {
            return offerSlots(t, draft, service.get(), prefix);
        }
        return askNameOrSummarize(t, draft, service.get(), prefix);
    }

    /** O cliente disse o nome: grava e mostra o resumo. */
    public StepResult summarizeWithName(TurnContext t, BookingDraft draft, String name) {
        Optional<ServiceType> service = t.catalog().service(draft.serviceId());
        if (service.isEmpty()) {
            return advance(t, draft, "");
        }
        return StepResult.of(new ConfirmingBooking(draft, null), Texts.summary(service.get(), draft.chosen(), name))
                .savingName(name);
    }

    private static StepResult askService(Catalog catalog, BookingDraft draft) {
        List<Long> offered = catalog.services().stream()
                .limit(ChoosingService.MAX_OPTIONS)
                .map(ServiceType::id)
                .toList();
        return StepResult.of(new ChoosingService(draft, offered),
                Texts.askService(catalog.services(), ChoosingService.MAX_OPTIONS));
    }

    private static StepResult askProfessional(Catalog catalog, BookingDraft draft) {
        List<Professional> offered = catalog.professionals().stream()
                .limit(ChoosingProfessional.MAX_PROFESSIONALS)
                .toList();
        return StepResult.of(new ChoosingProfessional(draft, offered.stream().map(Professional::id).toList()),
                Texts.askProfessional(offered));
    }

    private Reply askDate(TurnContext t) {
        if (t.entities().dateIssue() == null) {
            return Texts.askDate();
        }
        return Texts.dateIssue(t.entities().dateIssue(), settings.daysAhead(),
                t.localNow().toLocalDate().plusDays(settings.daysAhead()));
    }

    private StepResult offerSlots(TurnContext t, BookingDraft draft, ServiceType service, String prefix) {
        DayAvailability day;
        try {
            day = availability.find(draft, t.catalog(), t.localNow(), t.deadline());
        } catch (SchedulingContractException e) {
            log.error("A agenda não reconheceu um item do catálogo ao consultar horários; recarregando o catálogo");
            catalogProvider.invalidate();
            return StepResult.of(Idle.EMPTY, Texts.genericError(business.humanContactPhone()));
        } catch (SchedulingException e) {
            return StepResult.of(t.state(), Texts.availabilityUnavailable(business.humanContactPhone()));
        }
        Professional professional = draft.anyProfessional() ? null : professionalOf(draft, t.catalog()).orElseThrow();
        return switch (suggester.suggest(day, draft.time(), settings.maxSlotOptions())) {
            case Suggestion.ExactMatch match -> askNameOrSummarize(t, draft.withChosen(match.slot()), service,
                    prefix + Texts.exactMatch(match.slot(), service));
            case Suggestion.Alternatives alternatives -> StepResult.of(
                            new ChoosingSlot(draft.withDate(day.date()), day.date(), alternatives.options()),
                            Texts.slots(day, alternatives, draft.time(), service, professional))
                    .prefixed(prefix)
                    .withNote(alternatives.note());
            case Suggestion.NoSlots none -> StepResult.of(new ChoosingDate(draft.withoutDateAndTime()),
                    Texts.noSlots(none.searchedUntil())).prefixed(prefix);
        };
    }

    private static StepResult askNameOrSummarize(TurnContext t, BookingDraft draft, ServiceType service,
                                                 String prefix) {
        String name = t.customer().name();
        if (name != null) {
            return StepResult.of(new ConfirmingBooking(draft, null), Texts.summary(service, draft.chosen(), name))
                    .prefixed(prefix);
        }
        String suggested = suggestedName(t);
        Reply ask = suggested == null ? Texts.askName() : Texts.askNameSuggested(suggested);
        return StepResult.of(new AskingName(draft, suggested), ask).prefixed(prefix);
    }

    /** O primeiro nome do perfil do WhatsApp, se ele parecer um nome de verdade. */
    private static String suggestedName(TurnContext t) {
        String profileName = t.message().customer().profileName();
        if (profileName == null || profileName.isBlank()) {
            return null;
        }
        String firstName = profileName.strip().split("\\s+")[0];
        return PersonName.isValid(firstName) ? PersonName.titleCase(firstName) : null;
    }

    private static Optional<Professional> professionalOf(BookingDraft draft, Catalog catalog) {
        return Optional.ofNullable(draft.professionalId()).flatMap(catalog::professional);
    }
}
