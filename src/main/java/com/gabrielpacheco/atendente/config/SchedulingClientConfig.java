package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class SchedulingClientConfig {

    /** Espera curta antes de repetir uma consulta que falhou por rede ou por um 502/503/504. */
    static final Duration GET_RETRY_DELAY = Duration.ofMillis(200);

    @Bean
    AgendamentoApiClient agendamentoApiClient(RestClient.Builder builder, AppProperties app) {
        return create(builder, app.schedulingApi());
    }

    /** Os timeouts são curtos de propósito: a chamada acontece com a conversa do cliente travada. */
    public static AgendamentoApiClient create(RestClient.Builder builder, AppProperties.SchedulingApi api) {
        RestClient client = builder.baseUrl(api.baseUrl())
                .requestFactory(OutboundHttp.requestFactory(api.connectTimeout(), api.readTimeout()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "atendente-whatsapp/1.0")
                .build();
        return new AgendamentoApiClient(client, api.readTimeout(), GET_RETRY_DELAY);
    }
}
