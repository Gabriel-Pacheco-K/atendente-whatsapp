package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.flow.ConversationRouter;
import com.gabrielpacheco.atendente.application.flow.StepResult;
import com.gabrielpacheco.atendente.application.flow.TurnContext;
import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.in.HandleIncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import com.gabrielpacheco.atendente.application.port.out.ConversationStore;
import com.gabrielpacheco.atendente.application.port.out.CustomerStore;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessage;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessageStore;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.Conversation;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Um turno inteiro numa transação: trava a conversa do cliente, descarta mensagens repetidas, interpreta, decide e
 * grava. Mensagens do mesmo cliente são processadas uma de cada vez, então um "sim" repetido nunca agenda duas vezes.
 */
@Service
public class ConversationService implements HandleIncomingMessage {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    /** Áudio, foto ou figurinha: não há texto para interpretar. */
    private static final Interpretation NO_TEXT =
            new Interpretation(Intent.UNKNOWN, 0, Entities.none(), InterpreterKind.RULES);

    private final CustomerStore customers;
    private final ConversationStore conversations;
    private final ProcessedMessageStore processed;
    private final CatalogProvider catalogProvider;
    private final MessageInterpreter interpreter;
    private final ConversationRouter router;
    private final BusinessProfile business;
    private final ConversationSettings settings;
    private final Clock clock;

    public ConversationService(CustomerStore customers, ConversationStore conversations,
                               ProcessedMessageStore processed, CatalogProvider catalogProvider,
                               MessageInterpreter interpreter, ConversationRouter router, BusinessProfile business,
                               ConversationSettings settings, Clock clock) {
        this.customers = customers;
        this.conversations = conversations;
        this.processed = processed;
        this.catalogProvider = catalogProvider;
        this.interpreter = interpreter;
        this.router = router;
        this.business = business;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ConversationReply handle(IncomingMessage message) {
        long started = System.nanoTime();
        Customer customer = customers.upsert(message.customer());
        Conversation conversation = conversations.lock(customer.id());
        // Conferido sob a trava: uma cópia concorrente espera a original terminar e então enxerga o registro.
        Optional<ProcessedMessage> previous = processed.find(customer.id(), message.messageId());
        if (previous.isPresent()) {
            return replay(previous.get());
        }
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, settings.zone());
        boolean expired = conversation.isExpired(now, settings.expiresAfter());
        if (expired) {
            conversation.clearMisunderstandings();
        }
        String stateBefore = conversation.state().code();

        Catalog catalog = catalogProvider.current();
        boolean textless = message.kind() == MessageKind.UNSUPPORTED;
        ConversationState effective = expired ? Idle.EMPTY : conversation.state();
        Interpretation interpretation = textless ? null
                : interpreter.interpret(message.text(), context(effective, catalog, localNow));
        // O prazo da agenda começa depois da interpretação, que tem o próprio timeout: uma interpretação lenta não
        // pode deixar a agenda sem tempo.
        TurnContext turn = new TurnContext(conversation, customer, message, textless ? NO_TEXT : interpretation,
                catalog, now, localNow, expired, Deadline.after(settings.turnBudget(), clock));
        StepResult result = textless ? router.unsupported(turn) : router.route(turn);

        if (result.customerNameToSave() != null) {
            customers.saveName(customer.id(), result.customerNameToSave());
        }
        if (result.understood()) {
            conversation.clearMisunderstandings();
        }
        conversation.moveTo(result.next(), now);
        ConversationReply reply = new ConversationReply(result.replies(),
                TurnDiagnostics.of(interpretation, catalog, stateBefore, result.next().code(), result.note()), false);
        processed.register(customer.id(), message.messageId(), customer.channel() == Channel.WEB ? reply : null);
        conversations.save(conversation);
        log.info("turno canal={} cliente={} estado={}->{} intencao={} origem={} confianca={} total_ms={}",
                customer.channel(), customer.id(), stateBefore, result.next().code(),
                interpretation == null ? message.kind() : interpretation.intent(),
                interpretation == null ? "-" : interpretation.interpretedBy(), TurnDiagnostics.confidence(interpretation),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return reply;
    }

    /** Marca como processada uma mensagem que falhou de um jeito que reprocessar não resolveria. */
    @Transactional
    public void registerFailed(IncomingMessage message) {
        Customer customer = customers.upsert(message.customer());
        processed.register(customer.id(), message.messageId(), null);
    }

    private InterpretationContext context(ConversationState state, Catalog catalog, LocalDateTime localNow) {
        return new InterpretationContext(localNow.toLocalDate(), localNow.toLocalTime(), catalog, business.faqs(),
                state.expectation(), router.offeredLabels(state, catalog), settings.daysAhead());
    }

    private static ConversationReply replay(ProcessedMessage previous) {
        return previous.reply() != null
                ? previous.reply().asDuplicate()
                : new ConversationReply(List.of(), null, true);
    }
}
