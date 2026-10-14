package com.gabrielpacheco.atendente.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.adapter.in.http.ProblemResponses;
import com.gabrielpacheco.atendente.adapter.in.web.InterpreterInfo;
import com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppWebhookController;
import com.gabrielpacheco.atendente.adapter.out.nlu.HybridInterpreter;
import com.gabrielpacheco.atendente.adapter.out.nlu.claude.ClaudeAdapterConfig;
import com.gabrielpacheco.atendente.adapter.out.nlu.claude.ClaudeInterpreter;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.GraphApiMessageSender;
import com.gabrielpacheco.atendente.adapter.out.whatsapp.LoggingMessageSender;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.ReceiveWhatsAppMessage;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.application.port.out.MessageSender;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
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
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static ch.qos.logback.classic.Level.INFO;
import static org.assertj.core.api.Assertions.assertThat;

/** Que beans existem conforme a configuração, a partir do application.yml de verdade. */
@DisplayName("Ligação da configuração")
class ConfigurationWiringTest {

    private static final String TOKEN = "token-de-verificacao-com-mais-de-32-caracteres";
    private static final String SEGREDO = "segredo-que-nunca-aparece";
    private static final String NUMERO = "123456789012345";

    private static final ReceiveWhatsAppMessage NINGUEM_RECEBE = new ReceiveWhatsAppMessage() {
        @Override
        public void receive(IncomingMessage message) {
        }

        @Override
        public void warnRateLimited(String waId) {
        }
    };

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class))
            .withUserConfiguration(Propriedades.class, ClockConfig.class, JacksonConfig.class, WhatsAppConfig.class,
                    WhatsAppWebhookController.class)
            .withBean(ProblemResponses.class, () -> new ProblemResponses(new ObjectMapper()))
            .withBean(ReceiveWhatsAppMessage.class, () -> NINGUEM_RECEBE)
            // Valores explícitos, para uma variável de ambiente da máquina não mudar o resultado.
            .withPropertyValues("app.whatsapp.enabled=false", "app.whatsapp.verify-token=",
                    "app.whatsapp.app-secret=", "app.whatsapp.phone-number-id=", "app.whatsapp.sending.enabled=false",
                    "app.whatsapp.sending.access-token=");

    @EnableConfigurationProperties(AppProperties.class)
    static class Propriedades {
    }

    @Nested
    @DisplayName("WhatsApp")
    class WhatsApp {

        @Test
        @DisplayName("Desligado (o padrão), o webhook não existe e as respostas só vão para o log")
        void desligadoNaoDeveTerWebhook() {
            contexto.run(ctx -> {
                assertThat(ctx).hasNotFailed().doesNotHaveBean(WhatsAppWebhookController.class);
                assertThat(ctx.getBean(MessageSender.class)).isInstanceOf(LoggingMessageSender.class);
            });
        }

        @Test
        @DisplayName("Ligado com os três segredos, o webhook sobe")
        void ligadoComOsSegredosDeveSubir() {
            contexto.withPropertyValues(ligado(TOKEN, SEGREDO, NUMERO))
                    .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(WhatsAppWebhookController.class));
        }

        @Test
        @DisplayName("Ligado sem o app secret não sobe, e a mensagem cita WHATSAPP_APP_SECRET sem mostrar valores")
        void semAppSecretNaoDeveSubir() {
            contexto.withPropertyValues(ligado(TOKEN, "", NUMERO)).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx.getStartupFailure()).rootCause()
                        .hasMessageContaining("WHATSAPP_APP_SECRET")
                        .hasMessageNotContaining(TOKEN)
                        .hasMessageNotContaining(NUMERO);
            });
        }

        @Test
        @DisplayName("Ligado sem nada, a mensagem lista as três variáveis de uma vez")
        void semNenhumSegredoDeveListarTodos() {
            contexto.withPropertyValues("app.whatsapp.enabled=true").run(ctx ->
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContainingAll(
                            "WHATSAPP_VERIFY_TOKEN", "WHATSAPP_APP_SECRET", "WHATSAPP_PHONE_NUMBER_ID"));
        }

        @Test
        @DisplayName("Verify token com menos de 32 caracteres não sobe")
        void verifyTokenCurtoNaoDeveSubir() {
            contexto.withPropertyValues(ligado("curto-demais", SEGREDO, NUMERO)).run(ctx ->
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .hasMessageContainingAll("WHATSAPP_VERIFY_TOKEN", "32 caracteres")
                            .hasMessageNotContaining("curto-demais")
                            .hasMessageNotContaining(SEGREDO));
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
            contexto.withPropertyValues(ligado(TOKEN, SEGREDO, NUMERO))
                    .withPropertyValues("app.whatsapp.sending.enabled=true", "app.whatsapp.sending.access-token=EAAG")
                    .run(ctx -> assertThat(ctx.getBean(MessageSender.class)).isInstanceOf(GraphApiMessageSender.class));
        }

        private static String[] ligado(String verifyToken, String appSecret, String phoneNumberId) {
            return new String[] {"app.whatsapp.enabled=true", "app.whatsapp.verify-token=" + verifyToken,
                    "app.whatsapp.app-secret=" + appSecret, "app.whatsapp.phone-number-id=" + phoneNumberId};
        }
    }

    @Nested
    @DisplayName("Interpretação")
    class Interpretacao {

        private final ApplicationContextRunner contexto = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Propriedades.class, InterpreterConfig.class, ClaudeAdapterConfig.class)
                .withBean(BusinessProfile.class, Barbearia::perfil)
                // Explícito: uma ANTHROPIC_API_KEY na máquina não pode mudar o resultado.
                .withPropertyValues("app.ai.claude.api-key=", "app.ai.claude.enabled=true",
                        "app.ai.claude.base-url=http://localhost:1");

        @Test
        @DisplayName("Sem chave, só o motor de regras: nenhum bean do SDK da Anthropic, custo zero")
        void semChaveNaoDeveTerNadaDaAnthropic() {
            try (CapturaDeLogs logs = CapturaDeLogs.de(InterpreterConfig.class)) {
                contexto.run(ctx -> {
                    assertThat(ctx).hasNotFailed().doesNotHaveBean(ClaudeInterpreter.class);
                    assertThat(ctx.getBean(MessageInterpreter.class)).isInstanceOf(HybridInterpreter.class);
                    assertThat(ctx.getBean(InterpreterInfo.class).mode()).isEqualTo("RULES");
                    assertThat(tiposDosBeans(ctx)).noneMatch(tipo -> tipo.startsWith("com.anthropic"));
                });
                assertThat(logs.mensagens(INFO)).contains("Interpretação de mensagens: motor de regras (custo zero)");
            }
        }

        @Test
        @DisplayName("Com chave, o Claude entra atrás do interpretador híbrido, que continua sendo o principal")
        void comChaveDeveTerOClaudeAtrasDoHibrido() {
            try (CapturaDeLogs logs = CapturaDeLogs.de(InterpreterConfig.class)) {
                contexto.withPropertyValues("app.ai.claude.api-key=sk-ant-chave-de-teste").run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(ClaudeInterpreter.class);
                    assertThat(ctx.getBean(MessageInterpreter.class)).isInstanceOf(HybridInterpreter.class);
                    assertThat(ctx.getBean(InterpreterInfo.class).mode()).isEqualTo("RULES_WITH_CLAUDE");
                    assertThat(tiposDosBeans(ctx)).anyMatch(tipo -> tipo.startsWith("com.anthropic"));
                });
                assertThat(logs.mensagens(INFO)).contains("Interpretação: motor de regras + Claude "
                        + "(claude-haiku-5-5, effort low) só quando as regras têm dúvida");
            }
        }

        @Test
        @DisplayName("Com chave, mas CLAUDE_ENABLED=false, o Claude fica desligado")
        void comChaveEDesligadoNaoDeveTerOClaude() {
            contexto.withPropertyValues("app.ai.claude.api-key=sk-ant-chave-de-teste", "app.ai.claude.enabled=false")
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed().doesNotHaveBean(ClaudeInterpreter.class);
                        assertThat(tiposDosBeans(ctx)).noneMatch(tipo -> tipo.startsWith("com.anthropic"));
                    });
        }

        @Test
        @DisplayName("Endereço do Claude em http fora de localhost impede a subida: um redirecionamento levaria a chave")
        void enderecoSemHttpsNaoDeveSubir() {
            contexto.withPropertyValues("app.ai.claude.base-url=http://proxy.exemplo.com").run(ctx ->
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("ai.claude.base-url"));
            contexto.withPropertyValues("app.ai.claude.base-url=https://proxy.exemplo.com")
                    .run(ctx -> assertThat(ctx).hasNotFailed());
        }

        @Test
        @DisplayName("Um effort que a API não aceita impede a subida, em vez de fazer toda chamada falhar")
        void effortInvalidoNaoDeveSubir() {
            contexto.withPropertyValues("app.ai.claude.effort=baixo").run(ctx ->
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("ai.claude.effort"));
        }

        private static List<String> tiposDosBeans(ApplicationContext ctx) {
            return Arrays.stream(ctx.getBeanDefinitionNames())
                    .map(ctx::getType)
                    .filter(Objects::nonNull)
                    .map(Class::getName)
                    .toList();
        }
    }
}
