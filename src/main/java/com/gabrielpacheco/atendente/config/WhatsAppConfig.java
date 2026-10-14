package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.out.whatsapp.GraphApiMessageSender;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.LoggingMessageSender;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** O envio das respostas do WhatsApp: vão para o log até o envio pela Graph API ser ligado. */
@Configuration(proxyBeanMethods = false)
public class WhatsAppConfig {

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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
