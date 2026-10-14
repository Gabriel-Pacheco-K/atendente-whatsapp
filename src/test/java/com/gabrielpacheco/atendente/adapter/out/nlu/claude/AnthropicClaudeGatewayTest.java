package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Optional;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/** O SDK de verdade contra um WireMock no lugar da API da Anthropic: nenhum teste chama a API paga. */
@DisplayName("Chamada ao Claude pelo SDK")
class AnthropicClaudeGatewayTest {

    private static final String MESSAGES = "/v1/messages";
    private static final String CHAVE = "sk-ant-chave-de-teste";
    private static final String MODELO = "claude-haiku-5-5";
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final String EXTRACAO = """
            {\\"intent\\":\\"BOOK\\",\\"confidence\\":0.9,\\"service\\":\\"Barba\\",\\"professional\\":null,\
            \\"anyProfessional\\":false,\\"date\\":\\"2026-10-16\\",\\"time\\":null,\\"partOfDay\\":\\"MORNING\\",\
            \\"option\\":null,\\"faqId\\":null}""";

    @RegisterExtension
    static final WireMockExtension ANTHROPIC = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AnthropicClient cliente;
    private ClaudeGateway claude;
    private CapturaDeLogs logs;

    @BeforeEach
    void criarCliente() {
        ClaudeSettings settings = new ClaudeSettings(CHAVE, MODELO, "low", 2048, TIMEOUT, ANTHROPIC.baseUrl());
        ClaudeAdapterConfig config = new ClaudeAdapterConfig();
        cliente = config.anthropicClient(settings);
        claude = config.claudeGateway(cliente, settings);
        logs = CapturaDeLogs.de(AnthropicClaudeGateway.class);
    }

    @AfterEach
    void fecharCliente() {
        logs.close();
        cliente.close();
    }

    @Test
    @DisplayName("Lê a extração do bloco de texto e pula o bloco de raciocínio que vem antes")
    void deveLerAExtracaoPulandoORaciocinio() {
        responder(200, mensagem("end_turn", """
                {"type":"thinking","thinking":"","signature":"sig"},""" + textoComExtracao()));

        ClaudeResult resultado = claude.extract("sistema", "<mensagem>barba sexta de manhã</mensagem>");

        assertThat(resultado).isEqualTo(new ClaudeResult.Success(new ClaudeExtraction(Intent.BOOK, 0.9,
                Optional.of("Barba"), Optional.empty(), false, Optional.of("2026-10-16"), Optional.empty(),
                ClaudePartOfDay.MORNING, Optional.empty(), Optional.empty())));
    }

    @Test
    @DisplayName("Pede saída estruturada com effort low, com a chave e o modelo, sem temperature nem thinking")
    void deveMandarARequisicaoEsperada() throws Exception {
        responder(200, mensagem("end_turn", textoComExtracao()));

        claude.extract("instruções do sistema", "<mensagem>oi</mensagem>");

        ANTHROPIC.verify(exactly(1), postRequestedFor(urlEqualTo(MESSAGES)).withHeader("x-api-key", equalTo(CHAVE)));
        String enviado = ANTHROPIC.getAllServeEvents().get(0).getRequest().getBodyAsString();
        JsonNode corpo = new ObjectMapper().readTree(enviado);
        assertThat(corpo.path("model").asText()).isEqualTo(MODELO);
        assertThat(corpo.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(corpo.path("system").toString()).contains("instruções do sistema");
        assertThat(corpo.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(corpo.path("messages").get(0).toString()).contains("<mensagem>oi</mensagem>");
        assertThat(corpo.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(corpo.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(corpo.path("output_config").path("format").path("schema").path("properties").fieldNames())
                .toIterable().contains("intent", "service", "partOfDay").doesNotContain("name", "phone");
        assertThat(corpo.has("temperature")).isFalse();
        assertThat(corpo.has("thinking")).isFalse();
    }

    @Test
    @DisplayName("Registra só a contagem de tokens, nunca o conteúdo")
    void deveRegistrarSoOsTokens() {
        responder(200, mensagem("end_turn", textoComExtracao()));

        claude.extract("sistema", "<mensagem>me liga no [número]</mensagem>");

        assertThat(logs.tudoQueFoiEscrito())
                .containsExactly("Claude usou 10 tokens de entrada e 20 de saída")
                .noneMatch(linha -> linha.contains("Barba") || linha.contains("mensagem"));
    }

    @Test
    @DisplayName("Uma recusa não é utilizável, e o log diz só a categoria")
    void recusaNaoDeveSerUtilizavel() {
        responder(200, """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5","content":[],
                 "stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber","explanation":"x"},
                 "usage":{"input_tokens":10,"output_tokens":0}}""");

        assertThat(claude.extract("sistema", "usuário")).isEqualTo(new ClaudeResult.NotUsable("refusal"));
        assertThat(logs.tudoQueFoiEscrito()).contains("Claude recusou a mensagem (categoria cyber)");
    }

    @Test
    @DisplayName("Parar no limite de tokens não é utilizável")
    void limiteDeTokensNaoDeveSerUtilizavel() {
        responder(200, mensagem("max_tokens", """
                {"type":"thinking","thinking":"","signature":"sig"}"""));

        assertThat(claude.extract("sistema", "usuário")).isEqualTo(new ClaudeResult.NotUsable("max_tokens"));
    }

    @Test
    @DisplayName("Resposta sem bloco de texto não é utilizável")
    void semTextoNaoDeveSerUtilizavel() {
        responder(200, mensagem("end_turn", """
                {"type":"thinking","thinking":"","signature":"sig"}"""));

        assertThat(claude.extract("sistema", "usuário")).isEqualTo(new ClaudeResult.NotUsable("sem_conteudo"));
    }

    @Test
    @DisplayName("Campos ausentes na resposta viram valores neutros, sem derrubar a leitura")
    void camposAusentesDevemVirarValoresNeutros() {
        responder(200, mensagem("end_turn", """
                {"type":"text","text":"{\\"confidence\\":0.4,\\"partOfDay\\":null}"}"""));

        assertThat(claude.extract("sistema", "usuário")).isEqualTo(new ClaudeResult.Success(new ClaudeExtraction(
                Intent.UNKNOWN, 0.4, Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty(),
                ClaudePartOfDay.NONE, Optional.empty(), Optional.empty())));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {500, 529})
    @DisplayName("Erro do servidor falha na primeira tentativa, sem repetir a chamada")
    void erroDoServidorDeveFalharSemRepetir(int status) {
        responder(status, """
                {"type":"error","error":{"type":"api_error","message":"falhou"}}""");

        ClaudeResult resultado = claude.extract("sistema", "usuário");

        assertThat(resultado).isInstanceOfSatisfying(ClaudeResult.Failed.class,
                falha -> assertThat(falha.reason()).isEqualTo("http_" + status));
        ANTHROPIC.verify(exactly(1), postRequestedFor(urlEqualTo(MESSAGES)));
    }

    @Test
    @DisplayName("Limite de taxa tem motivo próprio")
    void limiteDeTaxaDeveTerMotivoProprio() {
        responder(429, """
                {"type":"error","error":{"type":"rate_limit_error","message":"devagar"}}""");

        assertThat(claude.extract("sistema", "usuário")).isInstanceOfSatisfying(ClaudeResult.Failed.class,
                falha -> assertThat(falha.reason()).isEqualTo("rate_limit"));
    }

    @Test
    @DisplayName("Resposta mais lenta que o timeout falha como erro de rede")
    void respostaLentaDeveFalharPorTimeout() {
        ANTHROPIC.stubFor(post(urlEqualTo(MESSAGES))
                .willReturn(json(200, mensagem("end_turn", textoComExtracao())).withFixedDelay(2_000)));

        ClaudeResult resultado = claude.extract("sistema", "usuário");

        assertThat(resultado).isInstanceOfSatisfying(ClaudeResult.Failed.class, falha -> {
            assertThat(falha.reason()).isEqualTo("io");
            assertThat(falha.cause()).isInstanceOf(AnthropicIoException.class);
        });
    }

    @Test
    @DisplayName("Texto que não é o JSON do esquema falha como saída inválida")
    void textoForaDoEsquemaDeveFalhar() {
        responder(200, mensagem("end_turn", """
                {"type":"text","text":"Claro! Você quer agendar uma barba."}"""));

        assertThat(claude.extract("sistema", "usuário")).isInstanceOfSatisfying(ClaudeResult.Failed.class,
                falha -> assertThat(falha.reason()).isEqualTo("saida_invalida"));
    }

    private static void responder(int status, String corpo) {
        ANTHROPIC.stubFor(post(urlEqualTo(MESSAGES)).willReturn(json(status, corpo)));
    }

    private static String textoComExtracao() {
        return """
                {"type":"text","text":"%s"}""".formatted(EXTRACAO);
    }

    private static String mensagem(String stopReason, String blocos) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5","content":[%s],
                 "stop_reason":"%s","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":20}}"""
                .formatted(blocos, stopReason);
    }
}
