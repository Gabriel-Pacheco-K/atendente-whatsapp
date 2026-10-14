package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.fasterxml.jackson.databind.ObjectReader;
import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.ReceiveWhatsAppMessage;
import com.gabrielpacheco.atendente.application.port.in.RetryLaterException;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

/**
 * O webhook da WhatsApp Cloud API. A assinatura é conferida sobre os bytes crus, antes de qualquer parse; só depois o
 * corpo vira objeto, com limites de profundidade e de tamanho. Cada mensagem é processada na hora, e o 200 diz à Meta
 * que não precisa reenviar. Banco fora ou conversa travada dão 503, e a Meta reenvia: a idempotência por wamid torna
 * o reenvio seguro.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "true")
public class WhatsAppWebhookController {

    static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String EVENT_RECEIVED = "EVENT_RECEIVED";

    private final WebhookVerification verification;
    private final WebhookSignatureVerifier signatures;
    private final ObjectReader payloadReader;
    private final WhatsAppMessageExtractor extractor;
    private final RateLimiter perSender;
    private final ReceiveWhatsAppMessage inbox;
    private final Clock clock;

    WhatsAppWebhookController(WebhookVerification verification, WebhookSignatureVerifier signatures,
                              ObjectReader webhookReader, WhatsAppMessageExtractor extractor,
                              RateLimiter whatsAppRateLimiter, ReceiveWhatsAppMessage inbox, Clock clock) {
        this.verification = verification;
        this.signatures = signatures;
        this.payloadReader = webhookReader.forType(WhatsAppPayload.class);
        this.extractor = extractor;
        this.perSender = whatsAppRateLimiter;
        this.inbox = inbox;
        this.clock = clock;
    }

    /** A verificação do painel da Meta, ao cadastrar a URL do webhook. */
    @GetMapping
    ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                  @RequestParam(name = "hub.verify_token", required = false) String token,
                                  @RequestParam(name = "hub.challenge", required = false) String challenge) {
        return verification.challengeToEcho(mode, token, challenge)
                .map(echo -> ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(echo))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping
    ResponseEntity<String> receive(@RequestBody(required = false) byte[] body,
                                   @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature) {
        byte[] raw = body == null ? new byte[0] : body;
        if (!signatures.isValid(raw, signature)) {
            log.warn("Webhook do WhatsApp recusado: assinatura ausente ou inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        parse(raw).ifPresent(payload -> extractor.extract(payload, clock.instant()).forEach(this::deliver));
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(EVENT_RECEIVED);
    }

    /** O turno foi desfeito por inteiro; a Meta vai mandar a mesma mensagem de novo. */
    @ExceptionHandler(RetryLaterException.class)
    ResponseEntity<Void> retryLater() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /** Assinado, mas ilegível ou fora dos limites: reenviar não mudaria nada, então é descartado com 200. */
    private Optional<WhatsAppPayload> parse(byte[] raw) {
        try {
            return Optional.ofNullable(payloadReader.readValue(raw));
        } catch (IOException e) {
            // A mensagem da exceção pode trazer um trecho do corpo, isto é, texto do cliente.
            log.warn("Webhook do WhatsApp com payload ilegível descartado ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void deliver(ExtractedMessage message) {
        switch (perSender.tryAcquire(message.from()).outcome()) {
            case ALLOWED -> inbox.receive(new IncomingMessage(
                    new CustomerRef(Channel.WHATSAPP, message.from(), "+" + message.from(), message.profileName()),
                    message.kind(), message.text(), message.id(), message.sentAt()));
            case DENIED_NOTIFY -> inbox.warnRateLimited(message.from());
            case DENIED -> log.debug("Mensagem do WhatsApp acima do limite descartada");
        }
    }
}
