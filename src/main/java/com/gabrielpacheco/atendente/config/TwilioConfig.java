package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.in.whatsapp.TwilioSignatureVerifier;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.TwilioMessageSender;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * O WhatsApp pela Twilio, para quando a conta de desenvolvedor da Meta ainda não está liberada. Ligado com
 * {@code TWILIO_ENABLED=true}; sem as credenciais, a aplicação não sobe e diz quais variáveis faltam. Ligado, o envio
 * das respostas (e do aviso ao dono) passa a sair pela Twilio.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.twilio.enabled", havingValue = "true")
public class TwilioConfig {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String webhookUrl;

    TwilioConfig(@Value("${app.twilio.account-sid:}") String accountSid,
                 @Value("${app.twilio.auth-token:}") String authToken,
                 @Value("${app.twilio.from-number:}") String fromNumber,
                 @Value("${app.twilio.webhook-url:}") String webhookUrl) {
        List<String> missing = new ArrayList<>();
        if (accountSid.isBlank()) missing.add("TWILIO_ACCOUNT_SID");
        if (authToken.isBlank()) missing.add("TWILIO_AUTH_TOKEN");
        if (fromNumber.isBlank()) missing.add("TWILIO_WHATSAPP_FROM");
        if (webhookUrl.isBlank()) missing.add("TWILIO_WEBHOOK_URL");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("A Twilio está ligada (TWILIO_ENABLED=true), mas falta: "
                    + String.join(", ", missing) + ".");
        }
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.webhookUrl = webhookUrl;
    }

    @Bean
    TwilioSignatureVerifier twilioSignatureVerifier() {
        return new TwilioSignatureVerifier(authToken, webhookUrl);
    }

    @Bean
    @Primary
    MessageSender twilioMessageSender(RestClient.Builder builder,
                                      @Value("${app.twilio.api-url:https://api.twilio.com}") String apiUrl) {
        String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        RestClient client = builder.baseUrl(apiUrl)
                .requestFactory(OutboundHttp.requestFactory(Duration.ofSeconds(2), Duration.ofSeconds(10)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
        return new TwilioMessageSender(client, accountSid, fromNumber);
    }
}
