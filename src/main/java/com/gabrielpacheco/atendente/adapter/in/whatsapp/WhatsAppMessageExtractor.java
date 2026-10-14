package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppPayload.Change;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppPayload.Contact;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppPayload.Entry;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppPayload.InboundMessage;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppPayload.Value;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tira do payload as mensagens que o atendente deve responder. Fica de fora o que não é para ele: outro produto da
 * Meta, outro campo, outro número da mesma conta (phone_number_id), avisos de entrega, reações, eventos de sistema e
 * mensagens com mais de 24 h, que só chegariam num reenvio muito atrasado ou num replay.
 */
public class WhatsAppMessageExtractor {

    static final int MAX_TEXT_LENGTH = 1000;

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageExtractor.class);
    private static final String BUSINESS_ACCOUNT = "whatsapp_business_account";
    private static final Set<String> IGNORED_TYPES = Set.of("reaction", "system");

    private final String phoneNumberId;
    private final int maxMessages;
    private final Duration maxAge;

    public WhatsAppMessageExtractor(String phoneNumberId, int maxMessages, Duration maxAge) {
        this.phoneNumberId = phoneNumberId;
        this.maxMessages = maxMessages;
        this.maxAge = maxAge;
    }

    public List<ExtractedMessage> extract(WhatsAppPayload payload, Instant now) {
        if (!BUSINESS_ACCOUNT.equals(payload.object())) {
            log.warn("Webhook ignorado: o object não é de uma conta do WhatsApp Business");
            return List.of();
        }
        List<Received> received = receivedMessages(payload);
        if (received.size() > maxMessages) {
            log.warn("Payload com {} mensagens; só as primeiras {} são processadas", received.size(), maxMessages);
        }
        return received.stream()
                .limit(maxMessages)
                .map(message -> toExtracted(message, now))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Uma mensagem e os contatos que vieram junto, de onde sai o nome do perfil. */
    private record Received(InboundMessage message, List<Contact> contacts) {
    }

    private List<Received> receivedMessages(WhatsAppPayload payload) {
        List<Received> received = new ArrayList<>();
        for (Entry entry : present(payload.entry())) {
            for (Change change : present(entry.changes())) {
                Value value = change.value();
                List<InboundMessage> messages = value == null ? List.of() : present(value.messages());
                if ("messages".equals(change.field()) && !messages.isEmpty() && isForThisNumber(value)) {
                    messages.forEach(message -> received.add(new Received(message, present(value.contacts()))));
                }
            }
        }
        return received;
    }

    private boolean isForThisNumber(Value value) {
        String target = value.metadata() == null ? null : value.metadata().phoneNumberId();
        if (phoneNumberId.equals(target)) {
            return true;
        }
        log.warn("Mensagens para o phone_number_id {} ignoradas: o número configurado é outro", target);
        return false;
    }

    private Optional<ExtractedMessage> toExtracted(Received received, Instant now) {
        InboundMessage message = received.message();
        if (message.from() == null || message.id() == null || IGNORED_TYPES.contains(message.type())) {
            return Optional.empty();
        }
        Optional<Instant> sentAt = sentAt(message.timestamp()).filter(at -> !at.isBefore(now.minus(maxAge)));
        if (sentAt.isEmpty()) {
            log.debug("Mensagem do WhatsApp ignorada: sem horário ou com mais de {}", maxAge);
            return Optional.empty();
        }
        boolean isText = "text".equals(message.type()) && message.text() != null && message.text().body() != null;
        return Optional.of(new ExtractedMessage(message.from(), profileName(received.contacts(), message.from()),
                message.id(), sentAt.get(), isText ? MessageKind.TEXT : MessageKind.UNSUPPORTED,
                isText ? truncate(message.text().body()) : null));
    }

    private static Optional<Instant> sentAt(String epochSeconds) {
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(epochSeconds)));
        } catch (NumberFormatException | DateTimeException e) {
            return Optional.empty();
        }
    }

    private static String profileName(List<Contact> contacts, String from) {
        return contacts.stream()
                .filter(contact -> from.equals(contact.waId()) && contact.profile() != null)
                .map(contact -> contact.profile().name())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Corta em 1000 caracteres sem partir um emoji (par de surrogates) ao meio. */
    private static String truncate(String body) {
        if (body.length() <= MAX_TEXT_LENGTH) {
            return body;
        }
        int end = Character.isHighSurrogate(body.charAt(MAX_TEXT_LENGTH - 1)) ? MAX_TEXT_LENGTH - 1 : MAX_TEXT_LENGTH;
        return body.substring(0, end);
    }

    /** Listas do payload sem os nulos que um JSON como {@code [null]} deixaria. */
    private static <T> List<T> present(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }
}
