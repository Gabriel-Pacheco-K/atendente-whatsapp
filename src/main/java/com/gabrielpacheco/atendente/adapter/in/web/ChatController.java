package com.gabrielpacheco.atendente.adapter.in.web;

import com.gabrielpacheco.atendente.adapter.in.http.ClientIpResolver;
import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.adapter.in.web.ChatDtos.BusinessResponse;
import com.gabrielpacheco.atendente.adapter.in.web.ChatDtos.CreateSessionRequest;
import com.gabrielpacheco.atendente.adapter.in.web.ChatDtos.CreateSessionResponse;
import com.gabrielpacheco.atendente.adapter.in.web.ChatDtos.SendMessageRequest;
import com.gabrielpacheco.atendente.adapter.in.web.ChatDtos.SendMessageResponse;
import com.gabrielpacheco.atendente.application.port.in.ChatSession;
import com.gabrielpacheco.atendente.application.port.in.ChatSessions;
import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.in.HandleIncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.business.OpeningHoursText;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import com.gabrielpacheco.atendente.domain.reply.Texts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * O chat web de demonstração. A sessão é o cliente: o id é sorteado pelo servidor e cada mensagem traz um
 * clientMessageId, que torna o reenvio seguro. Um reenvio devolve o mesmo corpo, com {@code X-Idempotent-Replay}.
 */
@RestController
@RequestMapping("/api/chat")
@ConditionalOnProperty(name = "app.web.enabled", havingValue = "true", matchIfMissing = true)
class ChatController {

    static final String IDEMPOTENT_REPLAY = "X-Idempotent-Replay";
    private static final List<String> SUGGESTIONS =
            List.of("Quero agendar um corte", "Quanto custa a barba?", "Onde fica?");

    private final ChatSessions sessions;
    private final HandleIncomingMessage conversation;
    private final BusinessProfile business;
    private final ChatRateLimits limits;
    private final InterpreterInfo interpreter;
    private final Clock clock;
    private final boolean showDiagnostics;

    ChatController(ChatSessions sessions, HandleIncomingMessage conversation, BusinessProfile business,
                   ChatRateLimits limits, InterpreterInfo interpreter, Clock clock,
                   @Value("${app.web.show-diagnostics}") boolean showDiagnostics) {
        this.sessions = sessions;
        this.conversation = conversation;
        this.business = business;
        this.limits = limits;
        this.interpreter = interpreter;
        this.clock = clock;
        this.showDiagnostics = showDiagnostics;
    }

    @PostMapping("/sessions")
    ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest body,
                                                        HttpServletRequest request) {
        check(limits.sessionsPerIp(), ClientIpResolver.resolve(request), "Muitas sessões");
        ChatSession session = sessions.start(body.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateSessionResponse(session.sessionId(),
                business.name(), Texts.menu(business.name()), SUGGESTIONS));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    ResponseEntity<SendMessageResponse> send(@PathVariable UUID sessionId, @Valid @RequestBody SendMessageRequest body,
                                             HttpServletRequest request) {
        CustomerRef customer = sessions.find(sessionId).orElseThrow(SessionNotFoundException::new);
        check(limits.messagesPerSession(), sessionId.toString(), "Muitas mensagens");
        check(limits.requestsPerIp(), ClientIpResolver.resolve(request), "Muitas mensagens");
        ConversationReply reply = conversation.handle(new IncomingMessage(customer, MessageKind.TEXT, body.text(),
                body.clientMessageId().toString(), clock.instant()));
        SendMessageResponse response = new SendMessageResponse(reply.replies(),
                showDiagnostics ? reply.diagnostics() : null);
        return reply.duplicate()
                ? ResponseEntity.ok().header(IDEMPOTENT_REPLAY, "true").body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping("/business")
    BusinessResponse business() {
        return new BusinessResponse(business.name(), business.address(),
                OpeningHoursText.describe(business.openingHours()), interpreter.mode());
    }

    private static void check(RateLimiter limiter, String key, String title) {
        RateLimiter.Decision decision = limiter.tryAcquire(key);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(title, decision.retryAfterSeconds());
        }
    }
}
