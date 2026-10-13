package com.gabrielpacheco.atendente.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Um log de configuração, ou uma exceção que imprima as propriedades, nunca pode mostrar um segredo. */
@DisplayName("Propriedades com segredo")
class PropertiesToStringMasksSecretsTest {

    private static final AppProperties.Sending ENVIO = new AppProperties.Sending(true, "token-da-graph-api",
            "https://graph.facebook.com", "v23.0", Duration.ofSeconds(2), Duration.ofSeconds(5));

    @Test
    @DisplayName("WhatsApp esconde o verify token e o app secret")
    void whatsAppDeveEsconderOsSegredos() {
        AppProperties.WhatsApp whatsApp = new AppProperties.WhatsApp(true, "verify-token-secreto",
                "app-secret-secreto", "123456789012345", 30, Duration.ofHours(24), 50, DataSize.ofKilobytes(256),
                ENVIO);

        assertThat(whatsApp.toString())
                .doesNotContain("verify-token-secreto", "app-secret-secreto", "token-da-graph-api")
                .contains("***", "123456789012345");
    }

    @Test
    @DisplayName("Envio esconde o token de acesso")
    void envioDeveEsconderOToken() {
        assertThat(ENVIO.toString()).doesNotContain("token-da-graph-api").contains("***", "v23.0");
    }

    @Test
    @DisplayName("Claude esconde a chave da API")
    void claudeDeveEsconderAChave() {
        AppProperties.Claude claude = new AppProperties.Claude(true, "sk-ant-chave-secreta", "claude-haiku-5-5",
                "low", 2048, Duration.ofSeconds(6), "", 0.75);

        assertThat(claude.toString()).doesNotContain("sk-ant-chave-secreta").contains("***", "claude-haiku-5-5");
    }

    @Test
    @DisplayName("Segredo vazio aparece como vazio, para dar para ver que falta configurar")
    void segredoVazioDeveAparecerComoVazio() {
        AppProperties.Claude semChave = new AppProperties.Claude(true, "", "claude-haiku-5-5", "low", 2048,
                Duration.ofSeconds(6), "", 0.75);

        assertThat(semChave.toString()).contains("apiKey=(vazio)");
    }
}
