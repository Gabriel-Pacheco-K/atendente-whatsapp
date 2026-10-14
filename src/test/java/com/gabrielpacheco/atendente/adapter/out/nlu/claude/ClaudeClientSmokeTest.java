package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.OutputConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sem rede: o SDK confere na criação do cliente se o Jackson do Spring Boot é compatível com ele. Se uma atualização
 * do Boot trouxer uma versão recusada, este teste quebra antes de qualquer chamada de verdade.
 */
@DisplayName("SDK da Anthropic junto com o Spring Boot")
class ClaudeClientSmokeTest {

    private static final ClaudeSettings SETTINGS = new ClaudeSettings("sk-ant-sem-uso", "claude-haiku-5-5", "low",
            2048, Duration.ofSeconds(6), "http://localhost:1");

    @Test
    @DisplayName("O cliente sobe com o Jackson do Boot e a requisição leva o esquema e o effort low")
    void clienteDeveSubirEARequisicaoDeveLevarEsquemaEEffort() {
        AnthropicClient client = new ClaudeAdapterConfig().anthropicClient(SETTINGS);
        try {
            AnthropicClaudeGateway gateway = new AnthropicClaudeGateway(client, SETTINGS.model(), SETTINGS.effort(),
                    SETTINGS.maxTokens());

            OutputConfig outputConfig = gateway.params("sistema", "usuário").rawParams().outputConfig().orElseThrow();

            assertThat(outputConfig.effort()).contains(OutputConfig.Effort.LOW);
            assertThat(outputConfig.format()).isPresent();
        } finally {
            client.close();
        }
    }
}
