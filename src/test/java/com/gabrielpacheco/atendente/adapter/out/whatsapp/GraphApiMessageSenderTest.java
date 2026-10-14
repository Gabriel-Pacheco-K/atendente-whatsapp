package com.gabrielpacheco.atendente.adapter.out.whatsapp;

import com.gabrielpacheco.atendente.config.AppProperties;
import com.gabrielpacheco.atendente.config.WhatsAppConfig;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.WARN;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@DisplayName("Envio pela Graph API")
class GraphApiMessageSenderTest {

    private static final String TOKEN = "EAAG-token-secreto-da-graph";
    private static final String PHONE_NUMBER_ID = "123456789012345";
    private static final String ENVIOS = "/v23.0/" + PHONE_NUMBER_ID + "/messages";
    private static final String PARA = "554899990000";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    @RegisterExtension
    static final WireMockExtension GRAPH = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort())
            .build();

    private GraphApiMessageSender envio;
    private CapturaDeLogs logs;

    @BeforeEach
    void criarEnvio() {
        envio = WhatsAppConfig.graphApiMessageSender(RestClient.builder(), whatsApp(GRAPH.baseUrl()));
        logs = CapturaDeLogs.de(GraphApiMessageSender.class);
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(json(200,
                "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.resposta\"}]}")));
    }

    @AfterEach
    void soltarLogs() {
        logs.close();
    }

    @Test
    @DisplayName("Manda o texto para o wa_id como chegou, com o token e no formato da Graph API, sem os botões")
    void deveMandarOTextoNoFormatoDaGraphApi() {
        envio.send(PARA, new Reply("Olá!\n1. Agendar", List.of("Agendar")));

        GRAPH.verify(exactly(1), postRequestedFor(urlEqualTo("/v23.0/123456789012345/messages"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withRequestBody(equalToJson("""
                        {"messaging_product":"whatsapp","recipient_type":"individual","to":"554899990000",
                         "type":"text","text":{"preview_url":false,"body":"Olá!\\n1. Agendar"}}""")));
    }

    @Test
    @DisplayName("Texto acima de 4096 caracteres sai em partes, quebrado entre parágrafos")
    void textoLongoDeveSairEmPartesPorParagrafo() {
        String primeiro = "a".repeat(3000);
        String segundo = "b".repeat(3000);

        envio.send(PARA, Reply.of(primeiro + "\n\n" + segundo));

        assertThat(GRAPH.findAll(postRequestedFor(urlEqualTo(ENVIOS))))
                .extracting(LoggedRequest::getBodyAsString)
                .satisfiesExactly(
                        corpo -> assertThat(corpo).contains("\"body\":\"" + primeiro + "\""),
                        corpo -> assertThat(corpo).contains("\"body\":\"" + segundo + "\""));
    }

    @Test
    @DisplayName("A divisão junta parágrafos enquanto cabem e corta o parágrafo que sozinho passa do limite")
    void divisaoDeveJuntarParagrafosECortarOsGrandes() {
        assertThat(GraphApiMessageSender.split("aa\n\nbb\n\ncc", 6)).containsExactly("aa\n\nbb", "cc");
        assertThat(GraphApiMessageSender.split("abcdefghij", 4)).containsExactly("abcd", "efgh", "ij");
        assertThat(GraphApiMessageSender.split("curto", 4096)).containsExactly("curto");
    }

    @Test
    @DisplayName("Erro 131030 (número fora da lista de teste) vira WARN com a dica do painel")
    void destinatarioNaoPermitidoDeveDarWarnComDica() {
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(json(400, """
                {"error":{"message":"(#131030) Recipient phone number not in allowed list","type":"OAuthException",
                 "code":131030,"fbtrace_id":"AbCdEf"}}""")));

        envio.send(PARA, Reply.of("oi"));

        assertThat(logs.mensagens(WARN)).singleElement().asString()
                .contains("WhatsApp > API Setup", "+55 48 9****-0000").doesNotContain(PARA);
    }

    @Test
    @DisplayName("401 vira ERROR de token inválido, sem o token no log")
    void tokenInvalidoDeveDarErrorSemOToken() {
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(json(401, """
                {"error":{"message":"Error validating access token","type":"OAuthException","code":190}}""")));

        envio.send(PARA, Reply.of("oi"));

        assertThat(logs.mensagens(ERROR)).singleElement().asString()
                .contains("Token do WhatsApp expirado ou inválido", "WHATSAPP_ACCESS_TOKEN").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("Código 190 com outro status também é token inválido")
    void codigo190DeveSerTokenInvalido() {
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(json(400, "{\"error\":{\"code\":190}}")));

        envio.send(PARA, Reply.of("oi"));

        assertThat(logs.mensagens(ERROR)).singleElement().asString().contains("Token do WhatsApp");
    }

    @Test
    @DisplayName("5xx vira WARN e não é repetido, porque reenviar poderia duplicar a mensagem")
    void erroDoServidorDeveDarWarnSemRepetir() {
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(aResponse().withStatus(503).withBody("<html>erro</html>")));

        envio.send(PARA, Reply.of("oi"));

        GRAPH.verify(exactly(1), postRequestedFor(urlEqualTo(ENVIOS)));
        assertThat(logs.mensagens(WARN)).singleElement().asString().contains("status 503");
    }

    @Test
    @DisplayName("Uma Graph API lenta esbarra no read timeout configurado e vira WARN")
    void respostaLentaDeveEsbarrarNoTimeout() {
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(aResponse().withStatus(200).withFixedDelay(6_000)));

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> envio.send(PARA, Reply.of("oi")));

        assertThat(logs.mensagens(WARN)).singleElement().asString().contains("não respondeu a tempo");
    }

    @Test
    @DisplayName("Uma falha na primeira parte não manda as seguintes, e nada escapa como exceção")
    void falhaNaPrimeiraParteNaoDeveMandarAsOutras() {
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(aResponse().withStatus(500)));

        assertThatCode(() -> envio.send(PARA, Reply.of("a".repeat(3000) + "\n\n" + "b".repeat(3000))))
                .doesNotThrowAnyException();

        GRAPH.verify(exactly(1), postRequestedFor(urlEqualTo(ENVIOS)));
    }

    private static AppProperties.WhatsApp whatsApp(String graphUrl) {
        return new AppProperties.WhatsApp(true, "v".repeat(32), "segredo", PHONE_NUMBER_ID, 30,
                Duration.ofHours(24), 50, DataSize.ofKilobytes(256),
                new AppProperties.Sending(true, TOKEN, graphUrl, "v23.0", Duration.ofSeconds(2), READ_TIMEOUT));
    }
}
