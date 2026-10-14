package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import com.gabrielpacheco.atendente.fixtures.ChatHttp.Resposta;
import com.gabrielpacheco.atendente.fixtures.WhatsAppHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.servlet.DispatcherServlet;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.postsDeAgendamento;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.FRASE;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA_15H;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.ENVIADA_EM;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.ENVIOS;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.VERIFY_TOKEN;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.WA_ID;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.imagem;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.payload;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.postDoWebhook;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.soAvisoDeEntrega;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.texto;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.textosEnviados;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@DisplayName("Webhook do WhatsApp")
class WhatsAppWebhookIntegrationTest extends IntegrationTestBase {

    private static final String MENU = "Posso te ajudar a:";

    @Autowired
    private MockMvc mvc;

    private WhatsAppHttp whatsapp;

    @BeforeEach
    void abrirWebhook() {
        whatsapp = new WhatsAppHttp(mvc);
    }

    @Nested
    @DisplayName("Verificação no painel da Meta")
    class Verificacao {

        @Test
        @DisplayName("Com o token certo, devolve o challenge em texto puro")
        void tokenCertoDeveDevolverOChallenge() {
            Resposta resposta = whatsapp.verifica("subscribe", VERIFY_TOKEN, "1158201444");

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(resposta.corpo()).isEqualTo("1158201444");
            assertThat(resposta.cabecalho("Content-Type")).startsWith("text/plain");
        }

        @Test
        @DisplayName("Token errado ou modo diferente de subscribe dão 403")
        void tokenErradoOuOutroModoDevemDar403() {
            assertThat(whatsapp.verifica("subscribe", "token-errado", "1158201444").status()).isEqualTo(403);
            assertThat(whatsapp.verifica("unsubscribe", VERIFY_TOKEN, "1158201444").status()).isEqualTo(403);
        }

        @Test
        @DisplayName("Um challenge com script dá 403 e nunca é refletido")
        void challengeComScriptDeveDar403() {
            Resposta resposta = whatsapp.verifica("subscribe", VERIFY_TOKEN, "<script>alert(1)</script>");

            assertThat(resposta.status()).isEqualTo(403);
            assertThat(resposta.corpo()).doesNotContain("script");
        }
    }

    @Nested
    @DisplayName("Mensagens")
    class Mensagens {

        @Test
        @DisplayName("Um \"oi\" assinado recebe 200 e o menu sai pela Graph API para o wa_id que chegou")
        void oiDeveSerRespondidoPelaGraphApi() {
            Resposta resposta = whatsapp.envia(payload(texto("wamid.oi", "oi")));

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(resposta.corpo()).isEqualTo("EVENT_RECEIVED");
            GRAPH.verify(exactly(1), postRequestedFor(urlEqualTo(ENVIOS))
                    .withRequestBody(equalToJson("{\"to\":\"" + WA_ID + "\"}", true, true)));
            assertThat(textosEnviados(GRAPH)).singleElement().asString().contains(MENU);
        }

        @Test
        @DisplayName("A conversa agenda com o primeiro nome do perfil e o telefone do wa_id")
        void conversaDeveAgendarComONomeDoPerfil() {
            agenda().comHorariosLivres(JOAO, CORTE, QUARTA, "15:00", "15:30")
                    .aceitandoAgendamento(9, JOAO, CORTE, QUARTA_15H, "João", "+" + WA_ID);

            whatsapp.envia(payload(texto("wamid.1", FRASE)));
            whatsapp.envia(payload(texto("wamid.2", "1")));
            whatsapp.envia(payload(texto("wamid.3", "sim")));

            assertThat(textosEnviados(GRAPH)).satisfiesExactly(
                    boaNoticia -> assertThat(boaNoticia).contains("Boa notícia", "posso colocar no nome de *João*?"),
                    resumo -> assertThat(resumo).contains("No nome de João."),
                    agendado -> assertThat(agendado).contains("Agendado, João! Seu código é *#9*."));
            AGENDA.verify(exactly(1), postsDeAgendamento().withRequestBody(equalToJson(
                    "{\"customerName\":\"João\",\"customerPhone\":\"+5548999990000\"}", true, true)));
        }

        @Test
        @DisplayName("Imagem recebe o pedido de mensagem de texto")
        void imagemDeveReceberOPedidoDeTexto() {
            whatsapp.envia(payload(imagem("wamid.foto")));

            assertThat(textosEnviados(GRAPH)).singleElement().asString().contains("só entendo mensagens de texto");
        }

        @Test
        @DisplayName("Aviso de entrega dá 200 sem resposta nem cliente novo")
        void avisoDeEntregaNaoDeveGerarResposta() {
            assertThat(whatsapp.envia(soAvisoDeEntrega()).status()).isEqualTo(200);

            assertThat(textosEnviados(GRAPH)).isEmpty();
            assertThat(clientes()).isZero();
        }

        @Test
        @DisplayName("Mensagem enviada há mais de 24 h é ignorada")
        void mensagemAntigaDeveSerIgnorada() {
            whatsapp.envia(payload(texto("wamid.velha", "sim", ENVIADA_EM.minus(Duration.ofHours(24)))));

            assertThat(textosEnviados(GRAPH)).isEmpty();
            assertThat(clientes()).isZero();
        }

        @Test
        @DisplayName("Cinco entregas simultâneas do mesmo wamid geram uma resposta só")
        void entregasSimultaneasDoMesmoWamidDevemResponderUmaVez() throws Exception {
            String payload = payload(texto("wamid.repetido", "oi"));
            CountDownLatch largada = new CountDownLatch(1);
            List<CompletableFuture<Resposta>> entregas;
            try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
                entregas = IntStream.range(0, 5)
                        .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                            aguardar(largada);
                            return whatsapp.envia(payload);
                        }, executor))
                        .toList();
                largada.countDown();
            }

            assertThat(entregas).extracting(entrega -> entrega.join().status()).containsOnly(200);
            GRAPH.verify(exactly(1), postRequestedFor(urlEqualTo(ENVIOS)));
            assertThat(processadas("wamid.repetido")).isOne();
        }

        @Test
        @DisplayName("Uma falha da Graph API no envio não desfaz o turno")
        void falhaNoEnvioNaoDeveDesfazerOTurno() {
            GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(json(500, "{}")));

            Resposta resposta = whatsapp.envia(payload(texto("wamid.1", "quero agendar")));

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(processadas("wamid.1")).isOne();
            assertThat(jdbc.sql("SELECT state ->> 'type' FROM conversations").query(String.class).single())
                    .isEqualTo("CHOOSING_SERVICE");
        }

        @Test
        @DisplayName("Passando de 30 mensagens no minuto, avisa uma vez, fica em silêncio e volta no minuto seguinte")
        void mensagensDemaisDevemSerAvisadasUmaVez() {
            Map<?, ?>[] trintaEDuas = IntStream.rangeClosed(1, 32)
                    .mapToObj(i -> texto("wamid.rajada." + i, "oi"))
                    .toArray(Map[]::new);

            whatsapp.envia(payload(trintaEDuas));
            whatsapp.envia(payload(texto("wamid.depois", "oi")));
            List<String> naRajada = textosEnviados(GRAPH);
            relogio.avancar(Duration.ofMinutes(1));
            whatsapp.envia(payload(texto("wamid.minuto-seguinte", "oi")));

            assertThat(naRajada).hasSize(31);
            assertThat(naRajada).filteredOn(texto -> texto.contains(MENU)).hasSize(30);
            assertThat(naRajada).filteredOn(texto -> texto.contains("Recebi muitas mensagens seguidas")).hasSize(1);
            assertThat(textosEnviados(GRAPH)).hasSize(32).last().asString().contains(MENU);
        }
    }

    @Nested
    @DisplayName("Defesas")
    class Defesas {

        @Test
        @DisplayName("Assinatura inválida ou ausente dá 401, sem processar nada")
        void assinaturaInvalidaDeveDar401() {
            String payload = payload(texto("wamid.forjado", "oi"));

            Resposta forjada = whatsapp.enviaComAssinatura(payload, "sha256=" + "0".repeat(64));
            Resposta semAssinatura = whatsapp.enviaComAssinatura(payload, null);

            assertThat(forjada.status()).isEqualTo(401);
            assertThat(forjada.corpo()).isEmpty();
            assertThat(semAssinatura.status()).isEqualTo(401);
            assertThat(textosEnviados(GRAPH)).isEmpty();
            assertThat(clientes()).isZero();
        }

        @Test
        @DisplayName("Corpo multipart sem assinatura dá 401: ninguém lê as partes antes da verificação")
        void multipartSemAssinaturaDeveDar401(@Autowired ApplicationContext contexto) {
            Resposta resposta = whatsapp.executar(MockMvcRequestBuilders.post(WhatsAppHttp.WEBHOOK)
                    .contentType("multipart/form-data; boundary=XYZ")
                    .content("--XYZ\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nabc"));

            assertThat(resposta.status()).isEqualTo(401);
            assertThat(contexto.containsBean(DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)).isFalse();
        }

        @Test
        @DisplayName("JSON inválido com assinatura válida é descartado com 200")
        void jsonInvalidoDeveSerDescartado() {
            Resposta resposta = whatsapp.envia("{\"object\":\"whatsapp_business_account\",\"entry\":[");

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(textosEnviados(GRAPH)).isEmpty();
        }

        @Test
        @DisplayName("JSON com mais de 20 níveis é descartado no parser, mesmo trazendo uma mensagem válida")
        void jsonProfundoDemaisDeveSerDescartado() {
            String profundo = "[".repeat(25) + "]".repeat(25);
            String payload = payload(texto("wamid.fundo", "oi")).replace("\"messaging_product\"",
                    "\"extra\":" + profundo + ",\"messaging_product\"");

            Resposta resposta = whatsapp.envia(payload);

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(textosEnviados(GRAPH)).isEmpty();
            assertThat(clientes()).isZero();
        }

        @Test
        @DisplayName("Corpo acima de 256 KB dá 413, com Content-Length ou enviado em partes")
        void corpoGrandeDemaisDeveDar413() {
            String grande = payload(texto("wamid.grande", "a".repeat(257 * 1024)));

            Resposta comTamanho = whatsapp.executar(postDoWebhook(grande));
            Resposta emPartes = whatsapp.executar(postDoWebhook(grande).with(semContentLength()));

            assertThat(comTamanho.status()).isEqualTo(413);
            assertThat(emPartes.status()).isEqualTo(413);
            assertThat(emPartes.json().path("title").asText()).isEqualTo("Mensagem muito grande");
        }

        /** Simula o envio em partes (chunked): o tamanho do corpo não vem no pedido. */
        private static RequestPostProcessor semContentLength() {
            return pedido -> {
                MockHttpServletRequest emPartes = spy(pedido);
                doReturn(-1).when(emPartes).getContentLength();
                doReturn(-1L).when(emPartes).getContentLengthLong();
                return emPartes;
            };
        }
    }

    @Nested
    @DisplayName("Falhas no processamento")
    class Falhas {

        @Test
        @DisplayName("Mensagem que causa um bug recebe desculpas uma vez e não é processada de novo no reenvio")
        void mensagemEnvenenadaDeveReceberDesculpasUmaVez() {
            String payload = payload(texto("wamid.veneno", ERRO_PROPOSITAL));

            Resposta primeira = whatsapp.envia(payload);
            Resposta reenvio = whatsapp.envia(payload);

            assertThat(primeira.status()).isEqualTo(200);
            assertThat(reenvio.status()).isEqualTo(200);
            assertThat(textosEnviados(GRAPH)).singleElement().asString()
                    .contains("Tive um problema aqui do meu lado", "(48) 3333-0000");
            assertThat(processadas("wamid.veneno")).isOne();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.gabrielpacheco.atendente.adapter.in.whatsapp.WhatsAppWebhookIntegrationTest#bancoFora")
        @DisplayName("Banco fora dá 503 sem marcar a mensagem, e o reenvio da Meta é processado")
        void bancoForaDeveDar503ParaAMetaReenviar(RuntimeException falha) {
            String payload = payload(texto("wamid.banco-fora", "oi"));
            conversas.falharNaProximaTrava(falha);

            Resposta durante = whatsapp.envia(payload);

            assertThat(durante.status()).isEqualTo(503);
            assertThat(processadas("wamid.banco-fora")).isZero();
            assertThat(textosEnviados(GRAPH)).isEmpty();

            Resposta reenvio = whatsapp.envia(payload);

            assertThat(reenvio.status()).isEqualTo(200);
            assertThat(textosEnviados(GRAPH)).singleElement().asString().contains(MENU);
        }
    }

    static Stream<RuntimeException> bancoFora() {
        return Stream.of(new CannotCreateTransactionException("Não foi possível abrir a transação"),
                new DataAccessResourceFailureException("Conexão recusada"));
    }

    @Nested
    @DisplayName("Com o WhatsApp desligado")
    @TestPropertySource(properties = "app.whatsapp.enabled=false")
    class Desligado {

        @Autowired
        private MockMvc mvcSemWhatsApp;

        @Test
        @DisplayName("As rotas do webhook nem existem: 404")
        void rotasDevemDar404() {
            WhatsAppHttp desligado = new WhatsAppHttp(mvcSemWhatsApp);

            assertThat(desligado.verifica("subscribe", VERIFY_TOKEN, "1158201444").status()).isEqualTo(404);
            assertThat(desligado.envia(payload(texto("wamid.1", "oi"))).status()).isEqualTo(404);
        }
    }

    private long clientes() {
        return jdbc.sql("SELECT count(*) FROM customers").query(Long.class).single();
    }

    private long processadas(String wamid) {
        return jdbc.sql("SELECT count(*) FROM processed_messages WHERE message_id = :id AND reply IS NULL")
                .param("id", wamid).query(Long.class).single();
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            sinal.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
