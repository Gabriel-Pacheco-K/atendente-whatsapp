package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * O WhatsApp pela Twilio: a mesma conversa do webhook da Meta, com a mensagem chegando como formulário. A assinatura
 * é conferida antes de tudo. A resposta vazia em TwiML diz à Twilio que deu certo; as respostas do atendente saem
 * pela API de mensagens, como no outro canal.
 */
@RestController
@RequestMapping("/webhook/twilio")
@ConditionalOnProperty(name = "app.twilio.enabled", havingValue = "true")
public class TwilioWebhookController {

    static final String SIGNATURE_HEADER = "X-Twilio-Signature";
    static final String EMPTY_TWIML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>";
    private static final String PREFIX = "whatsapp:+";
    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookController.class);

    private final TwilioSignatureVerifier signatures;
    private final RateLimiter perSender;
    private final ReceiveWhatsAppMessage inbox;
    private final Clock clock;

    /** Mensagens por número por minuto, o mesmo limite do webhook da Meta. */
    static final int MESSAGES_PER_MINUTE = 30;

    public TwilioWebhookController(TwilioSignatureVerifier signatures, ReceiveWhatsAppMessage inbox, Clock clock) {
        this.signatures = signatures;
        this.perSender = new RateLimiter(MESSAGES_PER_MINUTE, Duration.ofMinutes(1), clock);
        this.inbox = inbox;
        this.clock = clock;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> receive(@RequestParam Map<String, String> params,
                                   @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature) {
        if (!signatures.isValid(params, signature)) {
            log.warn("Webhook da Twilio recusado: assinatura ausente ou inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String from = params.getOrDefault("From", "");
        String id = params.get("MessageSid");
        if (!from.startsWith(PREFIX) || id == null || id.isBlank()) {
            log.warn("Webhook da Twilio sem remetente do WhatsApp ou sem MessageSid; descartado");
            return twiml();
        }
        String waId = from.substring(PREFIX.length()).replaceAll("[^0-9]", "");
        String text = params.getOrDefault("Body", "").strip();
        MessageKind kind = text.isEmpty() ? MessageKind.UNSUPPORTED : MessageKind.TEXT;
        switch (perSender.tryAcquire(waId).outcome()) {
            case ALLOWED -> inbox.receive(new IncomingMessage(
                    new CustomerRef(Channel.WHATSAPP, waId, "+" + waId, params.get("ProfileName")),
                    kind, text, id, clock.instant()));
            case DENIED_NOTIFY -> inbox.warnRateLimited(waId);
            case DENIED -> log.debug("Mensagem da Twilio acima do limite descartada");
        }
        return twiml();
    }

    /** O turno foi desfeito; com 503 a Twilio tenta de novo, e a idempotência pelo MessageSid torna isso seguro. */
    @ExceptionHandler(RetryLaterException.class)
    ResponseEntity<Void> retryLater() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private static ResponseEntity<String> twiml() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(EMPTY_TWIML);
    }
}
