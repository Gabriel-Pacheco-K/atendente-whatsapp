package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * O cliente do SDK e o interpretador do Claude, criados só quando {@link ClaudeEnabledCondition} vale. Fica neste
 * pacote porque é o único lugar do código que conhece o SDK da Anthropic.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(ClaudeEnabledCondition.class)
public class ClaudeAdapterConfig {

    /** Sem retentativa: o turno do cliente espera, e o motor de regras já é a alternativa. */
    @Bean(destroyMethod = "close")
    AnthropicClient anthropicClient(ClaudeSettings settings) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(settings.apiKey())
                .timeout(settings.timeout())
                .maxRetries(0);
        if (settings.baseUrl() != null && !settings.baseUrl().isBlank()) {
            builder.baseUrl(settings.baseUrl());
        }
        return builder.build();
    }

    @Bean
    ClaudeGateway claudeGateway(AnthropicClient client, ClaudeSettings settings) {
        return new AnthropicClaudeGateway(client, settings.model(), settings.effort(), settings.maxTokens());
    }

    @Bean
    ClaudeInterpreter claudeInterpreter(ClaudeGateway gateway, BusinessProfile business) {
        return new ClaudeInterpreter(gateway, business.name());
    }
}
