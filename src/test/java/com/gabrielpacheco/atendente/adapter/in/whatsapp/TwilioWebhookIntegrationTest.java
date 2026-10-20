package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("WhatsApp pela Twilio")
class TwilioWebhookIntegrationTest extends IntegrationTestBase {

    private static final String MENSAGENS = "/2010-04-01/Accounts/AC123/Messages.json";

    @DynamicPropertySource
    static void twilio(DynamicPropertyRegistry registry) {
        registry.add("app.twilio.enabled", () -> "true");
        registry.add("app.twilio.account-sid", () -> "AC123");
        registry.add("app.twilio.auth-token", () -> "token-de-teste");
        registry.add("app.twilio.from-number", () -> "+14155238886");
        registry.add("app.twilio.webhook-url", () -> TwilioSignatureVerifierTest.URL);
        registry.add("app.twilio.api-url", GRAPH::baseUrl);
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void twilioAceitaEnvios() {
        GRAPH.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(MENSAGENS)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"sid\":\"SM999\"}")));
    }

    private static MockHttpServletRequestBuilder webhook(Map<String, String> params, String assinatura) {
        MockHttpServletRequestBuilder pedido = post("/webhook/twilio")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(TwilioWebhookController.SIGNATURE_HEADER, assinatura);
        params.forEach(pedido::param);
        return pedido;
    }

    @Test
    @DisplayName("Uma mensagem assinada é respondida pela API da Twilio, do número do sandbox")
    void respondePelaTwilio() throws Exception {
        mvc.perform(webhook(TwilioSignatureVerifierTest.parametros(), TwilioSignatureVerifierTest.ASSINATURA))
                .andExpect(status().isOk())
                .andExpect(content().string(TwilioWebhookController.EMPTY_TWIML));

        GRAPH.verify(moreThanOrExactly(1), postRequestedFor(urlEqualTo(MENSAGENS))
                .withHeader("Authorization", containing("Basic "))
                .withRequestBody(containing("From=whatsapp%3A%2B14155238886"))
                .withRequestBody(containing("To=whatsapp%3A%2B5548999990001")));
    }

    @Test
    @DisplayName("A mesma mensagem reenviada não responde duas vezes")
    void reenvioNaoDuplica() throws Exception {
        mvc.perform(webhook(TwilioSignatureVerifierTest.parametros(), TwilioSignatureVerifierTest.ASSINATURA))
                .andExpect(status().isOk());
        int depoisDaPrimeira = GRAPH.findAll(postRequestedFor(urlEqualTo(MENSAGENS))).size();
        mvc.perform(webhook(TwilioSignatureVerifierTest.parametros(), TwilioSignatureVerifierTest.ASSINATURA))
                .andExpect(status().isOk());
        GRAPH.verify(exactly(depoisDaPrimeira), postRequestedFor(urlEqualTo(MENSAGENS)));
    }

    @Test
    @DisplayName("Sem assinatura válida, recusa e não responde nada")
    void recusaSemAssinatura() throws Exception {
        mvc.perform(webhook(TwilioSignatureVerifierTest.parametros(), "assinatura-falsa"))
                .andExpect(status().isUnauthorized());
        GRAPH.verify(exactly(0), postRequestedFor(urlEqualTo(MENSAGENS)));
    }
}
