package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.out.whatsapp.GraphApiMessageSender;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.LoggingMessageSender;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Que beans existem conforme a configuração, a partir do application.yml de verdade. */
@DisplayName("Ligação da configuração")
class ConfigurationWiringTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class))
            .withUserConfiguration(Propriedades.class, WhatsAppConfig.class)
            // Valores explícitos, para uma variável de ambiente da máquina não mudar o resultado.
            .withPropertyValues("app.whatsapp.sending.enabled=false", "app.whatsapp.sending.access-token=");

    @EnableConfigurationProperties(AppProperties.class)
    static class Propriedades {
    }

    @Nested
    @DisplayName("WhatsApp")
    class WhatsApp {

        @Test
        @DisplayName("Com o envio desligado (o padrão), as respostas só vão para o log")
        void envioDesligadoDeveSoRegistrarNoLog() {
            contexto.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx.getBean(MessageSender.class)).isInstanceOf(LoggingMessageSender.class);
            });
        }

        @Test
        @DisplayName("Envio ligado sem o token de acesso não sobe")
        void envioSemTokenNaoDeveSubir() {
            contexto.withPropertyValues("app.whatsapp.sending.enabled=true").run(ctx ->
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("WHATSAPP_ACCESS_TOKEN"));
        }

        @Test
        @DisplayName("Envio ligado com o token manda as respostas pela Graph API")
        void envioLigadoDeveUsarAGraphApi() {
            contexto.withPropertyValues("app.whatsapp.sending.enabled=true", "app.whatsapp.sending.access-token=EAAG")
                    .run(ctx -> assertThat(ctx.getBean(MessageSender.class)).isInstanceOf(GraphApiMessageSender.class));
        }
    }
}
