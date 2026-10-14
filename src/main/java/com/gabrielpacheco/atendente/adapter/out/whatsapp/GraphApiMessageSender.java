package com.gabrielpacheco.atendente.adapter.out.whatsapp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import com.gabrielpacheco.atendente.domain.privacy.PersonalDataMasker;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Envia as respostas pela Graph API da Meta. Sem retentativa: se a Meta recebeu e só a resposta dela se perdeu,
 * reenviar duplicaria a mensagem no celular do cliente. Uma falha fica no log e o turno continua valendo.
 * Os botões ({@code quickReplies}) não vão: no WhatsApp as opções já seguem numeradas no texto.
 */
public class GraphApiMessageSender implements MessageSender {

    /** O maior corpo de texto que a Graph API aceita numa mensagem. */
    static final int MAX_BODY_LENGTH = 4096;

    private static final Logger log = LoggerFactory.getLogger(GraphApiMessageSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PARAGRAPH = "\n\n";
    private static final int INVALID_TOKEN = 190;
    private static final int RECIPIENT_NOT_ALLOWED = 131030;

    private final RestClient client;
    private final String messagesPath;

    /** @param client já com a URL base da Graph API e o cabeçalho Authorization */
    public GraphApiMessageSender(RestClient client, String graphApiVersion, String phoneNumberId) {
        this.client = client;
        this.messagesPath = "/" + graphApiVersion + "/" + phoneNumberId + "/messages";
    }

    /** {@code to} é o wa_id exatamente como chegou; no Brasil ele pode vir sem o nono dígito, e é assim que vale. */
    @Override
    public void send(String to, Reply reply) {
        for (String part : split(reply.text(), MAX_BODY_LENGTH)) {
            if (!post(to, part)) {
                return;
            }
        }
    }

    record TextMessage(@JsonProperty("messaging_product") String messagingProduct,
                       @JsonProperty("recipient_type") String recipientType, String to, String type, Text text) {

        record Text(@JsonProperty("preview_url") boolean previewUrl, String body) {
        }

        static TextMessage to(String to, String body) {
            return new TextMessage("whatsapp", "individual", to, "text", new Text(false, body));
        }
    }

    private boolean post(String to, String body) {
        try {
            client.post()
                    .uri(messagesPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TextMessage.to(to, body))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            reportRejection(e, PersonalDataMasker.maskPhone(to));
        } catch (ResourceAccessException e) {
            log.warn("A Graph API não respondeu a tempo; a resposta para {} não foi enviada",
                    PersonalDataMasker.maskPhone(to));
        } catch (RestClientException e) {
            log.warn("Falha ao enviar a resposta para {} pela Graph API ({})", PersonalDataMasker.maskPhone(to),
                    e.getClass().getSimpleName());
        }
        return false;
    }

    /** Registra só o status e o código de erro da Meta: a mensagem de erro dela pode citar o destinatário. */
    private static void reportRejection(RestClientResponseException e, String maskedTo) {
        int status = e.getStatusCode().value();
        Integer code = errorCode(e);
        if (status == 401 || Objects.equals(code, INVALID_TOKEN)) {
            log.error("Token do WhatsApp expirado ou inválido: a resposta para {} não foi enviada. "
                    + "Gere outro token e atualize WHATSAPP_ACCESS_TOKEN", maskedTo);
        } else if (Objects.equals(code, RECIPIENT_NOT_ALLOWED)) {
            log.warn("O número de teste da Meta não aceita mandar para {}: adicione o número em WhatsApp > API Setup",
                    maskedTo);
        } else if (status >= 500) {
            log.warn("Graph API indisponível (status {}): a resposta para {} não foi enviada", status, maskedTo);
        } else {
            log.warn("Graph API recusou a resposta para {} (status {}, código {})", maskedTo, status, code);
        }
    }

    private static Integer errorCode(RestClientResponseException e) {
        try {
            JsonNode code = JSON.readTree(e.getResponseBodyAsString()).path("error").path("code");
            return code.isInt() ? code.asInt() : null;
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    /**
     * Divide por parágrafo um texto maior que {@code max}, juntando parágrafos enquanto couberem. Um parágrafo que
     * sozinho passa do limite é cortado, sem partir um emoji ao meio.
     */
    static List<String> split(String text, int max) {
        if (text.length() <= max) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split(PARAGRAPH)) {
            for (String piece : cut(paragraph, max)) {
                if (!current.isEmpty() && current.length() + PARAGRAPH.length() + piece.length() > max) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                current.append(current.isEmpty() ? "" : PARAGRAPH).append(piece);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static List<String> cut(String paragraph, int max) {
        List<String> pieces = new ArrayList<>();
        String rest = paragraph;
        while (rest.length() > max) {
            int end = Character.isHighSurrogate(rest.charAt(max - 1)) ? max - 1 : max;
            pieces.add(rest.substring(0, end));
            rest = rest.substring(end);
        }
        pieces.add(rest);
        return pieces;
    }
}
