package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import java.time.Duration;

/**
 * O que o adaptador precisa para chamar o Claude, montado a partir de {@code app.ai.claude}. O {@code toString}
 * nunca mostra a chave.
 *
 * @param baseUrl vazio em produção; os testes apontam para o WireMock
 */
public record ClaudeSettings(String apiKey, String model, String effort, int maxTokens, Duration timeout,
                             String baseUrl) {

    @Override
    public String toString() {
        String maskedKey = apiKey == null || apiKey.isBlank() ? "(vazio)" : "***";
        return "ClaudeSettings[apiKey=" + maskedKey + ", model=" + model + ", effort=" + effort + ", maxTokens="
                + maxTokens + ", timeout=" + timeout + ", baseUrl=" + baseUrl + "]";
    }
}
