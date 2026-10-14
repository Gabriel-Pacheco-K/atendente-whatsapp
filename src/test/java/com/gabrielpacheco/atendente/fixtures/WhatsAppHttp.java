package com.gabrielpacheco.atendente.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.fixtures.ChatHttp.Resposta;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * O webhook do WhatsApp como a Meta o chama: payloads no formato real, assinados com o app secret dos testes, e as
 * respostas que saíram pela Graph API (o WireMock). Os segredos são os do application-test.yml.
 */
public final class WhatsAppHttp {

    public static final String APP_SECRET = "segredo-de-teste";
    public static final String VERIFY_TOKEN = "token-de-verificacao-dos-testes-0123456789";
    public static final String ACCESS_TOKEN = "token-de-acesso-dos-testes";
    public static final String PHONE_NUMBER_ID = "123456789012345";
    public static final String WA_ID = "5548999990000";
    public static final String PERFIL = "João Pereira";
    public static final String WEBHOOK = "/webhook/whatsapp";
    public static final String ENVIOS = "/v23.0/" + PHONE_NUMBER_ID + "/messages";
    public static final String ASSINATURA = "X-Hub-Signature-256";
    /** Cinco minutos antes do relógio dos testes, como o timestamp do texto.json. */
    public static final Instant ENVIADA_EM = MutableClock.REFERENCIA.minus(Duration.ofMinutes(5));

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatHttp http;

    public WhatsAppHttp(MockMvc mvc) {
        this.http = new ChatHttp(mvc);
    }

    // Pedidos

    public Resposta envia(String payload) {
        return enviaComAssinatura(payload, assinar(payload));
    }

    /** {@code assinatura} nula manda o pedido sem o cabeçalho. */
    public Resposta enviaComAssinatura(String payload, String assinatura) {
        MockHttpServletRequestBuilder pedido = postDoWebhook(payload);
        if (assinatura != null) {
            pedido.header(ASSINATURA, assinatura);
        }
        return http.executar(pedido);
    }

    public Resposta executar(MockHttpServletRequestBuilder pedido) {
        return http.executar(pedido);
    }

    public Resposta verifica(String modo, String token, String challenge) {
        return http.executar(MockMvcRequestBuilders.get(WEBHOOK)
                .param("hub.mode", modo)
                .param("hub.verify_token", token)
                .param("hub.challenge", challenge));
    }

    public static MockHttpServletRequestBuilder postDoWebhook(String payload) {
        return MockMvcRequestBuilders.post(WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static String assinar(String corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // Payloads

    /** Um payload com as mensagens dadas, todas do cliente {@link #WA_ID} com o perfil {@link #PERFIL}. */
    public static String payload(Map<?, ?>... mensagens) {
        return payloadComPerfil(PERFIL, mensagens);
    }

    public static String payloadComPerfil(String perfil, Map<?, ?>... mensagens) {
        return envelope(mapa("contacts", List.of(mapa("profile", mapa("name", perfil), "wa_id", WA_ID)),
                "messages", Arrays.asList(mensagens)));
    }

    /** Só um aviso de entrega de uma resposta do atendente, sem mensagem do cliente. */
    public static String soAvisoDeEntrega() {
        return envelope(mapa("statuses", List.of(mapa("id", "wamid.resposta", "status", "delivered",
                "timestamp", String.valueOf(ENVIADA_EM.getEpochSecond()), "recipient_id", WA_ID))));
    }

    public static Map<String, Object> texto(String wamid, String corpo) {
        return texto(wamid, corpo, ENVIADA_EM);
    }

    public static Map<String, Object> texto(String wamid, String corpo, Instant enviadaEm) {
        Map<String, Object> mensagem = mensagem(wamid, "text", enviadaEm);
        mensagem.put("text", mapa("body", corpo));
        return mensagem;
    }

    public static Map<String, Object> imagem(String wamid) {
        Map<String, Object> mensagem = mensagem(wamid, "image", ENVIADA_EM);
        mensagem.put("image", mapa("mime_type", "image/jpeg", "sha256", "abc", "id", "MEDIA_ID"));
        return mensagem;
    }

    // Graph API

    /** A Graph API aceita qualquer envio, com a resposta que ela dá de verdade. */
    public static void aceitarEnvios(WireMockServer graph) {
        graph.stubFor(post(urlEqualTo(ENVIOS)).willReturn(AgendaHttp.json(200, escrever(mapa(
                "messaging_product", "whatsapp",
                "contacts", List.of(mapa("input", WA_ID, "wa_id", WA_ID)),
                "messages", List.of(mapa("id", "wamid.resposta")))))));
    }

    /** Os textos enviados pela Graph API, na ordem em que saíram. */
    public static List<String> textosEnviados(WireMockServer graph) {
        return graph.findAll(postRequestedFor(urlEqualTo(ENVIOS))).stream()
                .sorted(Comparator.comparing(LoggedRequest::getLoggedDate))
                .map(pedido -> lerJson(pedido.getBodyAsString()).path("text").path("body").asText())
                .toList();
    }

    private static Map<String, Object> mensagem(String wamid, String tipo, Instant enviadaEm) {
        return mapa("from", WA_ID, "id", wamid, "timestamp", String.valueOf(enviadaEm.getEpochSecond()), "type", tipo);
    }

    /** O envelope comum: conta do WhatsApp Business, campo "messages" e o número de teste. */
    private static String envelope(Map<String, Object> value) {
        value.put("messaging_product", "whatsapp");
        value.put("metadata", mapa("display_phone_number", "15550000000", "phone_number_id", PHONE_NUMBER_ID));
        return escrever(mapa("object", "whatsapp_business_account", "entry", List.of(mapa("id", "WABA_ID",
                "changes", List.of(mapa("field", "messages", "value", value))))));
    }

    private static Map<String, Object> mapa(Object... chavesEValores) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            mapa.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return mapa;
    }

    private static String escrever(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode lerJson(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
