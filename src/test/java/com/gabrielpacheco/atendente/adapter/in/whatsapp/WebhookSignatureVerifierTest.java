package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Vetores conferidos com {@code openssl dgst -sha256 -hmac segredo-de-teste} e com o hmac do Python. */
@DisplayName("Assinatura do webhook")
class WebhookSignatureVerifierTest {

    private static final String SEGREDO = "segredo-de-teste";
    private static final byte[] CORPO_SIMPLES = bytes("{\"object\":\"whatsapp_business_account\"}");
    private static final String ASSINATURA_SIMPLES =
            "sha256=ca71f4654f6eafafbad971060f8351acbb7a875d818134326dc31cd06b391d6c";
    /** O corpo como a Meta manda: "amanhã às 15h" com os acentos em escape unicode, seis caracteres cada. */
    private static final byte[] CORPO_COM_ESCAPE = bytes("{\"object\":\"whatsapp_business_account\",\"entry\":"
            + "[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messages\":[{\"type\":\"text\",\"text\":"
            + "{\"body\":\"amanh\\u00e3 \\u00e0s 15h\"}}]}}]}]}");
    private static final String ASSINATURA_COM_ESCAPE =
            "sha256=086de7dd5c5c71d7bfb49f74a5999da35853f7bc29819b95594d1012b3618f12";

    private final WebhookSignatureVerifier verificador = new WebhookSignatureVerifier(SEGREDO);

    @Test
    @DisplayName("Aceita a assinatura certa de um corpo simples")
    void deveAceitarAAssinaturaCerta() {
        assertThat(verificador.isValid(CORPO_SIMPLES, ASSINATURA_SIMPLES)).isTrue();
    }

    @Test
    @DisplayName("Aceita o corpo com escape unicode, assinado como chegou")
    void deveAceitarOCorpoComEscapeUnicode() {
        assertThat(new String(CORPO_COM_ESCAPE, StandardCharsets.UTF_8)).contains("amanh\\u00e3");

        assertThat(verificador.isValid(CORPO_COM_ESCAPE, ASSINATURA_COM_ESCAPE)).isTrue();
    }

    @Test
    @DisplayName("O mesmo JSON reserializado em UTF-8 não passa: a conta é sobre os bytes crus")
    void jsonReserializadoNaoDevePassar() {
        byte[] reserializado = bytes(new String(CORPO_COM_ESCAPE, StandardCharsets.UTF_8)
                .replace("\\u00e3", "ã").replace("\\u00e0", "à"));

        assertThat(new String(reserializado, StandardCharsets.UTF_8)).contains("amanhã às 15h");
        assertThat(verificador.isValid(reserializado, ASSINATURA_COM_ESCAPE)).isFalse();
        assertThat(verificador.isValid(reserializado,
                "sha256=af7ed42b46cebee6474cdacb97b80d2d542d3dec6983727114f75ae095fcf23b")).isTrue();
    }

    @Test
    @DisplayName("Um byte a mais no corpo invalida a assinatura")
    void corpoAlteradoNaoDevePassar() {
        assertThat(verificador.isValid(bytes("{\"object\":\"whatsapp_business_account\" }"), ASSINATURA_SIMPLES))
                .isFalse();
    }

    @Test
    @DisplayName("Outro app secret não confere")
    void outroSegredoNaoDevePassar() {
        assertThat(new WebhookSignatureVerifier("outro-segredo").isValid(CORPO_SIMPLES, ASSINATURA_SIMPLES)).isFalse();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {
            "sha1=ca71f4654f6eafafbad971060f8351acbb7a875d818134326dc31cd06b391d6c",
            "sha256=ca71f4654f6eafafbad971060f8351acbb7a875d818134326dc31cd06b391d6",
            "sha256=ca71f4654f6eafafbad971060f8351acbb7a875d818134326dc31cd06b391d6c0",
            "sha256=zz71f4654f6eafafbad971060f8351acbb7a875d818134326dc31cd06b391d6c",
            "ca71f4654f6eafafbad971060f8351acbb7a875d818134326dc31cd06b391d6c"})
    @DisplayName("Cabeçalho ausente, outro algoritmo, tamanho errado ou hex inválido não passam")
    void cabecalhoMalFormadoNaoDevePassar(String cabecalho) {
        assertThat(verificador.isValid(CORPO_SIMPLES, cabecalho)).isFalse();
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }
}
