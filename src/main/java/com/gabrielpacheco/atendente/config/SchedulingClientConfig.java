package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.out.scheduling.AgendamentoApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class SchedulingClientConfig {

    /** Espera curta antes de repetir uma consulta que falhou por rede ou por um 502/503/504. */
    static final Duration GET_RETRY_DELAY = Duration.ofMillis(200);

    @Bean
    AgendamentoApiClient agendamentoApiClient(RestClient.Builder builder, AppProperties app) {
        return create(builder, app.schedulingApi());
    }

    /**
     * HTTP/1.1, sem seguir redirecionamentos (a URL vem só da configuração) e com timeouts explícitos: a chamada
     * acontece com a conversa do cliente travada.
     */
    public static AgendamentoApiClient create(RestClient.Builder builder, AppProperties.SchedulingApi api) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(api.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(api.readTimeout());
        RestClient client = builder.baseUrl(api.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "atendente-whatsapp/1.0")
                .build();
        return new AgendamentoApiClient(client, api.readTimeout(), GET_RETRY_DELAY);
    }
}
