package com.gabrielpacheco.atendente.adapter.out.whatsapp;

import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import com.gabrielpacheco.atendente.domain.privacy.PersonalDataMasker;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/** Envia as respostas pelo WhatsApp da Twilio. Como no envio pela Meta, uma falha é registrada e nunca repetida. */
public class TwilioMessageSender implements MessageSender {

    /** O maior corpo que a Twilio aceita numa mensagem de WhatsApp. */
    static final int MAX_BODY_LENGTH = 1600;

    private static final Logger log = LoggerFactory.getLogger(TwilioMessageSender.class);

    private final RestClient client;
    private final String messagesPath;
    private final String from;

    /**
     * @param client já com a URL base da API da Twilio e a autenticação
     * @param fromNumber o número do WhatsApp na Twilio, como +14155238886 no sandbox
     */
    public TwilioMessageSender(RestClient client, String accountSid, String fromNumber) {
        this.client = client;
        this.messagesPath = "/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        this.from = "whatsapp:" + (fromNumber.startsWith("+") ? fromNumber : "+" + fromNumber);
    }

    @Override
    public void send(String to, Reply reply) {
        String destination = "whatsapp:+" + to.replaceAll("[^0-9]", "");
        List<String> parts = GraphApiMessageSender.split(reply.text(), MAX_BODY_LENGTH);
        for (String part : parts) {
            if (!post(destination, part, to)) {
                return;
            }
        }
    }

    private boolean post(String destination, String body, String to) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", destination);
        form.add("Body", body);
        try {
            client.post().uri(messagesPath)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                log.error("Credenciais da Twilio inválidas: a resposta para {} não foi enviada. Confira "
                        + "TWILIO_ACCOUNT_SID e TWILIO_AUTH_TOKEN", PersonalDataMasker.maskPhone(to));
            } else {
                log.warn("A Twilio recusou a resposta para {} (status {}). No sandbox, o número precisa ter mandado "
                        + "o código join antes", PersonalDataMasker.maskPhone(to), status);
            }
        } catch (ResourceAccessException e) {
            log.warn("A Twilio não respondeu a tempo; a resposta para {} não foi enviada",
                    PersonalDataMasker.maskPhone(to));
        } catch (RestClientException e) {
            log.warn("Falha ao enviar a resposta para {} pela Twilio ({})", PersonalDataMasker.maskPhone(to),
                    e.getClass().getSimpleName());
        }
        return false;
    }
}
