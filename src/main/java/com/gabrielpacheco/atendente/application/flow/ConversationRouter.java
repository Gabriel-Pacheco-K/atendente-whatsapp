package com.gabrielpacheco.atendente.application.flow;

import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.conversation.AskingName;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingBookingToCancel;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingService;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingCancellation;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decide o que fazer com cada mensagem. A ordem importa: expiração, a conferência de um agendamento incerto,
 * pedidos que valem em qualquer passo (humano, menu, desistência), perguntas, o passo atual, as outras digressões
 * (um "oi", um "obrigado") e, por fim, o "não entendi". Uma pergunta é respondida antes do passo mesmo quando cita um
 * serviço ou um dia: "quanto custa a barba?" no resumo não troca o serviço. Já "barba" na escolha do serviço não tem
 * pista de pergunta e é o serviço.
 */
@Component
public class ConversationRouter {

    private static final List<String> MENU_OPTIONS =
            List.of("Agendar", "Meus agendamentos", "Preços", "Endereço e horário");
    private static final Set<Intent> MAY_CARRY_BOOKING_DATA = Set.of(Intent.GREETING, Intent.AFFIRM, Intent.UNKNOWN);
    private static final Set<Intent> QUESTIONS = Set.of(Intent.PRICES, Intent.OPENING_HOURS, Intent.ADDRESS, Intent.FAQ);

    /** Respostas que só fazem sentido para a pergunta anterior: depois de muito tempo, ninguém sabe a que se referem. */
    private static final Pattern BLIND_ANSWER = Pattern.compile(
            "^(?:(?:a|o|opcao|numero)\\s+)?(?:\\d{1,2}|primeir[oa]|segund[oa]|terceir[oa]|quart[oa]|quint[oa]"
                    + "|sext[oa]|ultim[oa])(?:\\s+opcao)?$");

    private final BookingFlow booking;
    private final BookingConfirmation confirmation;
    private final CancellationFlow cancellation;
    private final InfoResponder info;
    private final BusinessProfile business;
    private final ConversationSettings settings;

    public ConversationRouter(BookingFlow booking, BookingConfirmation confirmation, CancellationFlow cancellation,
                              InfoResponder info, BusinessProfile business, ConversationSettings settings) {
        this.booking = booking;
        this.confirmation = confirmation;
        this.cancellation = cancellation;
        this.info = info;
        this.business = business;
        this.settings = settings;
    }

    public StepResult route(TurnContext t) {
        return t.expired() ? routeExpired(t) : routeInState(t);
    }

    /** As opções mostradas no passo, para o interpretador reconhecer "a segunda" ou o texto de um botão. */
    public List<String> offeredLabels(ConversationState state, Catalog catalog) {
        return switch (state) {
            case Idle idle -> MENU_OPTIONS;
            case ChoosingService s -> services(s, catalog).stream().map(ServiceType::name).toList();
            case ChoosingProfessional s -> Texts.professionalOptions(professionals(s, catalog));
            case ChoosingDate s -> List.of();
            case ChoosingSlot s -> s.options().stream().map(slot -> Texts.slotLabel(slot, true)).toList();
            case AskingName s -> s.suggestedName() == null ? List.of() : Texts.suggestedNameOptions(s.suggestedName());
            case ConfirmingBooking s -> List.of();
            case ChoosingBookingToCancel s -> s.options().stream().map(Texts::bookingLabel).toList();
            case ConfirmingCancellation s -> List.of();
        };
    }

    /** Áudio, foto ou figurinha: pede texto, sem contar como mensagem não entendida e sem reabrir passo expirado. */
    public StepResult unsupported(TurnContext t) {
        if (!t.expired()) {
            return StepResult.of(t.state(), Texts.onlyText()).withNote(StepResult.UNSUPPORTED);
        }
        return recoverExpired(t).orElseGet(() -> StepResult.of(Idle.EMPTY,
                Texts.onlyText().prefixedWith(Texts.restarted())).withNote(StepResult.UNSUPPORTED));
    }

    private StepResult routeExpired(TurnContext t) {
        Optional<StepResult> recovered = recoverExpired(t);
        if (recovered.isPresent()) {
            return recovered.get();
        }
        if (isBlindAnswer(t)) {
            return StepResult.of(Idle.EMPTY, Texts.restartedForSafety(), menu()).withNote(StepResult.SAFETY_RESTART);
        }
        return routeInState(t).prefixed(Texts.restarted());
    }

    /** Uma tentativa incerta não pode se perder com a expiração: se ela foi gravada, o cliente fica sabendo. */
    private Optional<StepResult> recoverExpired(TurnContext t) {
        return t.conversation().state() instanceof ConfirmingBooking stale && stale.pendingAttempt() != null
                ? confirmation.recoverExpired(t, stale)
                : Optional.empty();
    }

    private static boolean isBlindAnswer(TurnContext t) {
        Intent intent = t.intent();
        return intent == Intent.AFFIRM || intent == Intent.DENY
                || BLIND_ANSWER.matcher(TextNormalizer.normalize(t.message().text()).text()).matches();
    }

    private StepResult routeInState(TurnContext t) {
        ConversationState state = t.state();
        Intent intent = t.intent();
        if (state instanceof ConfirmingBooking pending && pending.pendingAttempt() != null && !isPlainYes(t)) {
            Optional<StepResult> settled = confirmation.settlePending(t, pending);
            if (settled.isPresent()) {
                return settled.get();
            }
        }
        if (intent == Intent.HUMAN) {
            return StepResult.of(Idle.EMPTY, Texts.human(business.humanContactPhone(), business.humanContactHours()));
        }
        if (intent == Intent.RESTART) {
            return StepResult.of(Idle.EMPTY, menu());
        }
        if (intent == Intent.CANCEL_BOOKING && state.inBookingFlow()) {
            StepResult desist = StepResult.of(Idle.EMPTY, Texts.desist());
            return t.entities().rescheduleRequested() ? desist.plus(Texts.rescheduleHint()) : desist;
        }
        boolean midway = !(state instanceof Idle);
        if (midway && QUESTIONS.contains(intent)) {
            Optional<StepResult> answered = digression(state, t);
            if (answered.isPresent()) {
                return answered.get();
            }
        }
        Optional<StepResult> step = handleStep(state, t);
        if (step.isPresent()) {
            return step.get();
        }
        if (midway) {
            Optional<StepResult> digression = digression(state, t);
            if (digression.isPresent()) {
                return digression.get();
            }
        }
        return notUnderstood(t);
    }

    /** Só um "sim" puro vai direto para o POST, que já trata o 409 da tentativa pendente. */
    private static boolean isPlainYes(TurnContext t) {
        return t.intent() == Intent.AFFIRM && !t.entities().hasBookingData();
    }

    private Optional<StepResult> handleStep(ConversationState state, TurnContext t) {
        return switch (state) {
            case Idle idle -> onIdle(t, idle);
            case ChoosingService s -> booking.onChoosingService(t, s);
            case ChoosingProfessional s -> booking.onChoosingProfessional(t, s);
            case ChoosingDate s -> booking.onChoosingDate(t, s);
            case ChoosingSlot s -> booking.onChoosingSlot(t, s);
            case AskingName s -> booking.onAskingName(t, s);
            case ConfirmingBooking s -> booking.onConfirmingBooking(t, s);
            case ChoosingBookingToCancel s -> cancellation.onChoosingBookingToCancel(t, s);
            case ConfirmingCancellation s -> cancellation.onConfirmingCancellation(t, s);
        };
    }

    /**
     * No início, o "sim" repetido de quem acabou de agendar vem primeiro, depois o número do menu e as intenções
     * explícitas. Só então os dados de agenda iniciam um agendamento: "quanto custa a barba?" é preço, não reserva.
     */
    private Optional<StepResult> onIdle(TurnContext t, Idle idle) {
        Intent intent = t.intent();
        Entities found = t.entities();
        if (intent == Intent.AFFIRM && !found.hasBookingData() && idle.lastBooking() != null
                && idle.lastBooking().isRecent(t.now(), settings.duplicateConfirmationWindow())) {
            return Optional.of(StepResult.of(idle, Texts.alreadyConfirmed(idle.lastBooking().booking())));
        }
        if (found.option() != null) {
            return menuOption(t, idle, found.option());
        }
        Optional<StepResult> explicit = switch (intent) {
            case MY_BOOKINGS -> Optional.of(cancellation.myBookings(t));
            case CANCEL_BOOKING -> Optional.of(cancellation.start(t));
            case PRICES, ADDRESS, OPENING_HOURS, FAQ ->
                    info.answer(intent, found, t.catalog()).map(reply -> StepResult.of(idle, reply));
            case THANKS -> Optional.of(StepResult.of(idle, Texts.thanks()));
            case DENY -> Optional.of(StepResult.of(idle, Texts.noWorries()));
            default -> Optional.empty();
        };
        if (explicit.isPresent()) {
            return explicit;
        }
        if (intent == Intent.BOOK || (MAY_CARRY_BOOKING_DATA.contains(intent) && found.hasBookingData())) {
            return Optional.of(booking.start(t, BookingDraft.EMPTY.merge(found)));
        }
        if (intent == Intent.GREETING || intent == Intent.AFFIRM) {
            return Optional.of(StepResult.of(idle, menu()));
        }
        return Optional.empty();
    }

    private Optional<StepResult> menuOption(TurnContext t, Idle idle, int option) {
        return switch (option) {
            case 1 -> Optional.of(booking.start(t, BookingDraft.EMPTY));
            case 2 -> Optional.of(cancellation.myBookings(t));
            case 3 -> Optional.of(StepResult.of(idle, info.prices(t.catalog())));
            case 4 -> Optional.of(StepResult.of(idle, info.addressAndHours()));
            default -> Optional.empty();
        };
    }

    /** Uma pergunta no meio de um passo: responde e repete a pergunta do passo, sem perder o andamento. */
    private Optional<StepResult> digression(ConversationState state, TurnContext t) {
        Intent intent = t.intent();
        Optional<Reply> answer = info.answer(intent, t.entities(), t.catalog());
        if (answer.isPresent()) {
            Reply reprompt = Texts.resumingBooking(reprompt(state, t.catalog(), t.customer()));
            return Optional.of(StepResult.of(state, answer.get(), reprompt));
        }
        if (intent == Intent.GREETING) {
            return Optional.of(StepResult.of(state, reprompt(state, t.catalog(), t.customer())
                    .prefixedWith(Texts.greetingPrefix())));
        }
        if (intent == Intent.THANKS) {
            return Optional.of(StepResult.of(state, reprompt(state, t.catalog(), t.customer())
                    .prefixedWith(Texts.thanksPrefix())));
        }
        if (intent == Intent.MY_BOOKINGS && state.inBookingFlow()) {
            return Optional.of(cancellation.myBookings(t).prefixed(Texts.bookingSetAside()));
        }
        return Optional.empty();
    }

    /** Na segunda mensagem seguida sem entender, oferece o telefone e zera a contagem. */
    private StepResult notUnderstood(TurnContext t) {
        ConversationState state = t.state();
        if (t.conversation().registerMisunderstanding() == 1) {
            return StepResult.notUnderstood(state, Texts.notUnderstood(state.expectation()));
        }
        t.conversation().clearMisunderstandings();
        Reply twice = Texts.notUnderstoodTwice(business.humanContactPhone());
        return state instanceof Idle
                ? StepResult.notUnderstood(state, twice, menu())
                : StepResult.notUnderstood(state, twice);
    }

    /** A pergunta do passo atual, para repetir depois de uma digressão. */
    private Reply reprompt(ConversationState state, Catalog catalog, Customer customer) {
        return switch (state) {
            case Idle idle -> menu();
            case ChoosingService s -> Texts.askService(services(s, catalog), ChoosingService.MAX_OPTIONS);
            case ChoosingProfessional s -> Texts.askProfessional(professionals(s, catalog));
            case ChoosingDate s -> Texts.askDate();
            case ChoosingSlot s -> Texts.chooseSlotAgain(s.options(), s.draft().anyProfessional());
            case AskingName s -> s.suggestedName() == null ? Texts.askName() : Texts.askNameSuggested(s.suggestedName());
            case ConfirmingBooking s -> catalog.service(s.draft().serviceId())
                    .filter(service -> customer.name() != null)
                    .map(service -> Texts.summary(service, s.draft().chosen(), customer.name()))
                    .orElseGet(Texts::yesOrNo);
            case ChoosingBookingToCancel s -> Texts.chooseBookingToCancel(s.options());
            case ConfirmingCancellation s -> Texts.confirmCancel(s.booking());
        };
    }

    private Reply menu() {
        return Texts.menu(business.name());
    }

    private static List<ServiceType> services(ChoosingService state, Catalog catalog) {
        return state.offeredServiceIds().stream().flatMap(id -> catalog.service(id).stream()).toList();
    }

    private static List<Professional> professionals(ChoosingProfessional state, Catalog catalog) {
        return state.offeredProfessionalIds().stream().flatMap(id -> catalog.professional(id).stream()).toList();
    }
}
