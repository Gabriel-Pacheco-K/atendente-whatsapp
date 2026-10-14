package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import com.gabrielpacheco.atendente.config.JacksonConfig;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.gabrielpacheco.atendente.fixtures.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static ch.qos.logback.classic.Level.WARN;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.PHONE_NUMBER_ID;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.WA_ID;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.imagem;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.payload;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.payloadComPerfil;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.texto;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mensagens tiradas do webhook")
class WhatsAppMessageExtractorTest {

    private static final ObjectMapper JSON = JacksonConfig.webhookObjectMapper(new ObjectMapper());
    private static final Instant AGORA = MutableClock.REFERENCIA;

    private final WhatsAppMessageExtractor extrator =
            new WhatsAppMessageExtractor(PHONE_NUMBER_ID, 50, Duration.ofHours(24));

    @Test
    @DisplayName("Texto vira mensagem com o wa_id, o wamid, o horário de envio e o nome do perfil")
    void textoDeveVirarMensagem() {
        List<ExtractedMessage> mensagens = extrator.extract(fixture("texto.json"), AGORA);

        assertThat(mensagens).containsExactly(new ExtractedMessage(WA_ID, "João Pereira",
                "wamid.HBgMNTU0ODk5OTk5MDAwMBUCABIYFjNFQjA", Instant.ofEpochSecond(1791910500), MessageKind.TEXT,
                "quero cortar o cabelo amanhã às 15h"));
    }

    @Test
    @DisplayName("Payload só com aviso de entrega não traz mensagem")
    void soAvisoDeEntregaNaoDeveTrazerMensagem() {
        assertThat(extrator.extract(fixture("so-status.json"), AGORA)).isEmpty();
    }

    @Test
    @DisplayName("Áudio vira UNSUPPORTED, sem texto")
    void audioDeveVirarNaoSuportado() {
        assertThat(extrator.extract(fixture("audio.json"), AGORA)).singleElement().satisfies(mensagem -> {
            assertThat(mensagem.kind()).isEqualTo(MessageKind.UNSUPPORTED);
            assertThat(mensagem.text()).isNull();
        });
    }

    @Test
    @DisplayName("Reação a uma mensagem é ignorada")
    void reacaoDeveSerIgnorada() {
        assertThat(extrator.extract(fixture("reacao.json"), AGORA)).isEmpty();
    }

    @Test
    @DisplayName("Mensagem para outro número da mesma conta é ignorada, com WARN")
    void outroPhoneNumberIdDeveSerIgnorado() {
        WhatsAppMessageExtractor deOutroNumero = new WhatsAppMessageExtractor("999999999999999", 50,
                Duration.ofHours(24));

        try (CapturaDeLogs logs = CapturaDeLogs.de(WhatsAppMessageExtractor.class)) {
            assertThat(deOutroNumero.extract(fixture("texto.json"), AGORA)).isEmpty();
            assertThat(logs.mensagens(WARN)).singleElement().asString().contains(PHONE_NUMBER_ID);
        }
    }

    @Test
    @DisplayName("Com 51 mensagens no payload, só as 50 primeiras são processadas, com WARN")
    void maisDe50MensagensDevemSerCortadas() {
        Map<?, ?>[] mensagens = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> texto("wamid." + i, "oi"))
                .toArray(Map[]::new);

        try (CapturaDeLogs logs = CapturaDeLogs.de(WhatsAppMessageExtractor.class)) {
            List<ExtractedMessage> extraidas = extrator.extract(ler(payload(mensagens)), AGORA);

            assertThat(extraidas).hasSize(50).last().extracting(ExtractedMessage::id).isEqualTo("wamid.50");
            assertThat(logs.mensagens(WARN)).singleElement().asString().contains("51 mensagens");
        }
    }

    @Test
    @DisplayName("Mensagem enviada há mais de 24 h é ignorada; com menos, passa")
    void mensagemAntigaDeveSerIgnorada() {
        String payload = payload(texto("wamid.velha", "sim", AGORA.minus(Duration.ofHours(25))),
                texto("wamid.recente", "sim", AGORA.minus(Duration.ofHours(23))));

        assertThat(extrator.extract(ler(payload), AGORA)).extracting(ExtractedMessage::id)
                .containsExactly("wamid.recente");
    }

    @Test
    @DisplayName("O nome do perfil é o do contato com o mesmo wa_id; sem contato, fica nulo")
    void nomeDoPerfilDeveVirDoContatoCerto() {
        String semContato = payload(texto("wamid.1", "oi")).replaceAll("\"contacts\":\\[.*?],", "");
        String outroContato = payloadComPerfil("Outra Pessoa", texto("wamid.1", "oi")).replace(
                "\"wa_id\":\"" + WA_ID + "\"", "\"wa_id\":\"5511988887777\"");

        assertThat(extrator.extract(ler(semContato), AGORA)).singleElement()
                .extracting(ExtractedMessage::profileName).isNull();
        assertThat(extrator.extract(ler(outroContato), AGORA)).singleElement()
                .extracting(ExtractedMessage::profileName).isNull();
    }

    @Test
    @DisplayName("Texto com mais de 1000 caracteres é cortado")
    void textoLongoDeveSerCortado() {
        String longo = "a".repeat(1500);

        assertThat(extrator.extract(ler(payload(texto("wamid.1", longo))), AGORA)).singleElement()
                .extracting(ExtractedMessage::text).asString().hasSize(1000);
    }

    @Test
    @DisplayName("Imagem também vira UNSUPPORTED")
    void imagemDeveVirarNaoSuportada() {
        assertThat(extrator.extract(ler(payload(imagem("wamid.foto"))), AGORA)).singleElement()
                .extracting(ExtractedMessage::kind).isEqualTo(MessageKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("Webhook de outro produto da Meta ou de outro campo é ignorado")
    void outroObjectOuOutroCampoDeveSerIgnorado() {
        String texto = payload(texto("wamid.1", "oi"));

        assertThat(extrator.extract(ler(texto.replace("whatsapp_business_account", "page")), AGORA)).isEmpty();
        assertThat(extrator.extract(ler(texto.replace("\"field\":\"messages\"", "\"field\":\"account_update\"")),
                AGORA)).isEmpty();
    }

    private static WhatsAppPayload fixture(String nome) {
        try (InputStream arquivo = WhatsAppMessageExtractorTest.class.getResourceAsStream("/whatsapp/" + nome)) {
            return JSON.readValue(arquivo, WhatsAppPayload.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static WhatsAppPayload ler(String json) {
        try {
            return JSON.readValue(json, WhatsAppPayload.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
