package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.in.http.BodySizeLimitFilter;
import com.gabrielpacheco.atendente.adapter.in.http.ProblemResponses;
import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WebhookSignatureVerifier;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WebhookVerification;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppMessageExtractor;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.GraphApiMessageSender;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.LoggingMessageSender;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * O canal WhatsApp. O webhook só existe com {@code app.whatsapp.enabled=true}; ligado sem os segredos, a aplicação não
 * sobe, e a mensagem cita as variáveis de ambiente que faltam, nunca os valores. As respostas vão para o log até o
 * envio pela Graph API ser ligado.
 */
@Configuration(proxyBeanMethods = false)
public class WhatsAppConfig {

    /** Um token curto pode ser adivinhado por tentativa e erro. */
    static final int MIN_VERIFY_TOKEN_LENGTH = 32;

    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.sending.enabled", havingValue = "true")
    MessageSender graphApiMessageSender(RestClient.Builder builder, AppProperties app) {
        return graphApiMessageSender(builder, app.whatsapp());
    }

    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.sending.enabled", havingValue = "false", matchIfMissing = true)
    MessageSender loggingMessageSender() {
        return new LoggingMessageSender();
    }

    public static GraphApiMessageSender graphApiMessageSender(RestClient.Builder builder,
                                                              AppProperties.WhatsApp whatsapp) {
        AppProperties.Sending sending = whatsapp.sending();
        if (isBlank(sending.accessToken())) {
            throw new IllegalStateException("O envio pelo WhatsApp está ligado (WHATSAPP_SENDING_ENABLED=true), "
                    + "mas falta WHATSAPP_ACCESS_TOKEN.");
        }
        RestClient client = builder.baseUrl(sending.graphUrl())
                .requestFactory(OutboundHttp.requestFactory(sending.connectTimeout(), sending.readTimeout()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + sending.accessToken())
                .build();
        return new GraphApiMessageSender(client, sending.graphApiVersion(), whatsapp.phoneNumberId());
    }

    /** O webhook e o que ele usa; o controller também só é registrado com {@code app.whatsapp.enabled=true}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "true")
    static class Webhook {

        /** As propriedades já conferidas: os beans do webhook dependem delas, então nada sobe sem os segredos. */
        @Bean
        AppProperties.WhatsApp checkedWhatsAppProperties(AppProperties app) {
            return requireSecrets(app.whatsapp());
        }

        @Bean
        WebhookVerification webhookVerification(AppProperties.WhatsApp checked) {
            return new WebhookVerification(checked.verifyToken());
        }

        @Bean
        WebhookSignatureVerifier webhookSignatureVerifier(AppProperties.WhatsApp checked) {
            return new WebhookSignatureVerifier(checked.appSecret());
        }

        @Bean
        WhatsAppMessageExtractor whatsAppMessageExtractor(AppProperties.WhatsApp checked) {
            return new WhatsAppMessageExtractor(checked.phoneNumberId(), checked.maxMessagesPerPayload(),
                    checked.maxMessageAge());
        }

        /** Mensagens por número de WhatsApp (wa_id) por minuto. */
        @Bean
        RateLimiter whatsAppRateLimiter(AppProperties.WhatsApp checked, Clock clock) {
            return new RateLimiter(checked.messagesPerMinute(), Duration.ofMinutes(1), clock);
        }

        @Bean
        FilterRegistrationBean<BodySizeLimitFilter> webhookBodySizeLimitFilter(AppProperties.WhatsApp checked,
                                                                               ProblemResponses problems) {
            FilterRegistrationBean<BodySizeLimitFilter> registration = new FilterRegistrationBean<>(
                    new BodySizeLimitFilter(checked.maxBody().toBytes(), problems));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            registration.addUrlPatterns("/webhook/*");
            return registration;
        }
    }

    static AppProperties.WhatsApp requireSecrets(AppProperties.WhatsApp whatsapp) {
        List<String> problems = new ArrayList<>();
        if (isBlank(whatsapp.verifyToken())) {
            problems.add("falta WHATSAPP_VERIFY_TOKEN");
        } else if (whatsapp.verifyToken().length() < MIN_VERIFY_TOKEN_LENGTH) {
            problems.add("WHATSAPP_VERIFY_TOKEN precisa ter pelo menos " + MIN_VERIFY_TOKEN_LENGTH
                    + " caracteres (gere um com: openssl rand -hex 32)");
        }
        if (isBlank(whatsapp.appSecret())) {
            problems.add("falta WHATSAPP_APP_SECRET");
        }
        if (isBlank(whatsapp.phoneNumberId())) {
            problems.add("falta WHATSAPP_PHONE_NUMBER_ID");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("O webhook do WhatsApp está ligado (WHATSAPP_ENABLED=true), mas "
                    + String.join("; ", problems) + ".");
        }
        return whatsapp;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
