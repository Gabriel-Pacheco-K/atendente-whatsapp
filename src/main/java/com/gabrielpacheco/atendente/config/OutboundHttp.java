package com.gabrielpacheco.atendente.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * O cliente HTTP das chamadas para fora: o do JDK, em HTTP/1.1, sem seguir redirecionamentos (as URLs vêm só da
 * configuração) e com os timeouts dados. É escolhido de propósito: o SDK da Anthropic traz o HttpComponents, e sem
 * isto o Spring passaria a usá-lo com os timeouts padrão dele.
 */
final class OutboundHttp {

    private OutboundHttp() {
    }

    static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
