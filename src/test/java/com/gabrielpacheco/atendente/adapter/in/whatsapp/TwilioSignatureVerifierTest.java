package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Assinatura do webhook da Twilio")
class TwilioSignatureVerifierTest {

    static final String URL = "https://exemplo.trycloudflare.com/webhook/twilio";
    /** Calculada à parte (HMAC-SHA1 em Python) para estes parâmetros, URL e token. */
    static final String ASSINATURA = "tSkx5BD4nQQBHLxMCamrhUA/eDU=";

    static Map<String, String> parametros() {
        return new HashMap<>(Map.of("Body", "oi", "From", "whatsapp:+5548999990001", "MessageSid", "SM123",
                "ProfileName", "Ana"));
    }

    private final TwilioSignatureVerifier verificador = new TwilioSignatureVerifier("token-de-teste", URL);

    @Test
    @DisplayName("Aceita a assinatura certa, em qualquer ordem dos parâmetros")
    void aceitaAAssinaturaCerta() {
        assertThat(verificador.isValid(parametros(), ASSINATURA)).isTrue();
    }

    @Test
    @DisplayName("Recusa corpo alterado, outra URL, assinatura vazia ou ilegível")
    void recusaOResto() {
        Map<String, String> alterado = parametros();
        alterado.put("Body", "oi!");
        assertThat(verificador.isValid(alterado, ASSINATURA)).isFalse();
        assertThat(new TwilioSignatureVerifier("token-de-teste", URL + "/").isValid(parametros(), ASSINATURA))
                .isFalse();
        assertThat(new TwilioSignatureVerifier("outro-token", URL).isValid(parametros(), ASSINATURA)).isFalse();
        assertThat(verificador.isValid(parametros(), null)).isFalse();
        assertThat(verificador.isValid(parametros(), "%%%")).isFalse();
    }
}
