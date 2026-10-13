package com.gabrielpacheco.atendente.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gabrielpacheco.atendente.IntegrationTestBase;
import com.gabrielpacheco.atendente.fixtures.CenaPrincipal;
import com.gabrielpacheco.atendente.fixtures.ChatHttp;
import com.gabrielpacheco.atendente.fixtures.ChatHttp.Resposta;
import com.gabrielpacheco.atendente.fixtures.ChatHttp.Sessao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.postsDeAgendamento;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.FRASE;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.NOME;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.TELEFONE;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("API do chat web")
class ChatApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TransactionTemplate transacao;

    private ChatHttp chat;

    @BeforeEach
    void abrirChat() {
        chat = new ChatHttp(mvc);
        CenaPrincipal.prepararAgenda(agenda(), 1);
    }

    @Test
    @DisplayName("A sessão nova já traz o menu, os botões e as sugestões de primeira mensagem")
    void sessaoNovaDeveTrazerOMenu() {
        Resposta criada = chat.executar(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + TELEFONE + "\"}"));

        JsonNode corpo = criada.json();
        assertThat(criada.status()).isEqualTo(201);
        assertThat(UUID.fromString(corpo.path("sessionId").asText())).isNotNull();
        assertThat(corpo.path("businessName").asText()).isEqualTo("Barbearia Navalha de Ouro");
        assertThat(corpo.path("welcome").path("text").asText()).contains("1. Agendar um horário");
        assertThat(corpo.path("welcome").path("quickReplies")).hasSize(4);
        assertThat(corpo.path("suggestions").get(0).asText()).isEqualTo("Quero agendar um corte");
    }

    @Test
    @DisplayName("Os dados do estabelecimento trazem o horário agrupado e quem interpreta as mensagens")
    void deveDescreverOEstabelecimento() {
        JsonNode negocio = chat.executar(get("/api/chat/business")).json();

        assertThat(negocio.path("name").asText()).isEqualTo("Barbearia Navalha de Ouro");
        assertThat(negocio.path("openingHours").asText()).isEqualTo(
                "Terça a sexta: 09:00 às 19:00\nSábado: 08:00 às 14:00\nSegunda e domingo: fechado");
        assertThat(negocio.path("interpreter").asText()).isEqualTo("RULES");
    }

    @Test
    @DisplayName("A conversa inteira pelo HTTP agenda de verdade: um POST exato e a linha guardada com o serviço")
    void deveAgendarAConversaInteiraPeloHttp() {
        Sessao sessao = chat.novaSessao(TELEFONE);

        Resposta frase = sessao.envia(FRASE);
        Resposta nome = sessao.envia(NOME);
        Resposta sim = sessao.envia("sim");

        assertThat(frase.textos()).contains("Boa notícia", "quarta-feira, 14/10, às 15:00");
        assertThat(frase.json().path("debug").path("stateAfter").asText()).isEqualTo("ASKING_NAME");
        assertThat(frase.json().path("debug").path("interpretedBy").asText()).isEqualTo("RULES");
        assertThat(nome.textos()).contains("No nome de Gabriel Pacheco.");
        assertThat(sim.textos()).contains("Agendado, Gabriel! Seu código é *#1*.");
        AGENDA.verify(exactly(1), postsDeAgendamento().withRequestBody(equalToJson("""
                {"professionalId":1,"serviceId":1,"startAt":"2026-10-14T15:00",
                 "customerName":"Gabriel Pacheco","customerPhone":"+55 48 99999-0001"}""")));
        Map<String, Object> guardado = jdbc.sql("SELECT appointment_id, service_id, status FROM bookings")
                .query().singleRow();
        assertThat(guardado).containsEntry("appointment_id", 1L).containsEntry("service_id", 1L)
                .containsEntry("status", "SCHEDULED");
    }

    @Test
    @DisplayName("O mesmo clientMessageId devolve o corpo idêntico, marcado como reenvio, e o POST sai uma vez só")
    void reenvioDeveDevolverOMesmoCorpoSemAgendarDeNovo() {
        Sessao sessao = chat.novaSessao(TELEFONE);
        sessao.envia(FRASE);
        sessao.envia(NOME);

        Resposta original = sessao.envia("sim");
        Resposta reenvio = sessao.reenviaAUltima("sim");

        assertThat(reenvio.status()).isEqualTo(200);
        assertThat(reenvio.corpo()).isEqualTo(original.corpo());
        assertThat(reenvio.cabecalho(ChatController.IDEMPOTENT_REPLAY)).isEqualTo("true");
        assertThat(original.cabecalho(ChatController.IDEMPOTENT_REPLAY)).isNull();
        AGENDA.verify(exactly(1), postsDeAgendamento());
    }

    @Test
    @DisplayName("O clientMessageId de outra sessão é uma mensagem nova, e a resposta da outra não vaza")
    void idDeOutraSessaoNaoDeveDevolverARespostaDela() {
        Sessao ana = chat.novaSessao(TELEFONE);
        Sessao bruno = chat.novaSessao("+55 48 98888-0002");
        UUID mesmoId = UUID.randomUUID();

        Resposta daAna = ana.envia(FRASE, mesmoId);
        Resposta doBruno = bruno.envia("oi", mesmoId);

        assertThat(daAna.textos()).contains("Boa notícia");
        assertThat(doBruno.textos()).contains("Posso te ajudar a:").doesNotContain("Boa notícia");
        assertThat(doBruno.cabecalho(ChatController.IDEMPOTENT_REPLAY)).isNull();
    }

    @Nested
    @DisplayName("Erros")
    class Erros {

        @Test
        @DisplayName("Campos inválidos dão 400 com o erro de cada campo")
        void camposInvalidosDevemDar400ComErros() {
            Sessao sessao = chat.novaSessao(TELEFONE);

            Resposta resposta = sessao.enviarCorpo("{\"text\":\"\"}");

            assertThat(resposta.status()).isEqualTo(400);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Dados inválidos");
            assertThat(resposta.json().path("errors").path("clientMessageId").asText()).isEqualTo("é obrigatório");
            assertThat(resposta.json().path("errors").path("text").asText()).isEqualTo("é obrigatório");
        }

        @Test
        @DisplayName("JSON quebrado dá 400 sem ecoar o que foi enviado")
        void jsonQuebradoDeveDar400() {
            Sessao sessao = chat.novaSessao(TELEFONE);

            Resposta resposta = sessao.enviarCorpo("{\"text\":\"meu segredo");

            assertThat(resposta.status()).isEqualTo(400);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Dados inválidos");
            assertThat(resposta.corpo()).doesNotContain("meu segredo");
        }

        @Test
        @DisplayName("Telefone com 21 caracteres dá 400: não caberia no banco nem no agendamento-api")
        void telefoneLongoDemaisDeveDar400() {
            Resposta resposta = chat.executar(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"+12345678901234567890\"}"));

            assertThat(resposta.status()).isEqualTo(400);
            assertThat(resposta.json().path("errors").has("phone")).isTrue();
            assertThat(jdbc.sql("SELECT count(*) FROM customers").query(Long.class).single()).isZero();
        }

        @Test
        @DisplayName("Sessão que não existe dá 404")
        void sessaoInexistenteDeveDar404() {
            Resposta resposta = chat.executar(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"text\":\"oi\"}"));

            assertThat(resposta.status()).isEqualTo(404);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Sessão não encontrada");
        }

        @Test
        @DisplayName("Texto de 1001 caracteres dá 400")
        void textoLongoDemaisDeveDar400() {
            Resposta resposta = chat.novaSessao(TELEFONE).envia("a".repeat(1001));

            assertThat(resposta.status()).isEqualTo(400);
            assertThat(resposta.json().path("errors").path("text").asText())
                    .isEqualTo("deve ter no máximo 1000 caracteres");
        }

        @Test
        @DisplayName("Corpo de 17 KB dá 413 antes de chegar ao controller")
        void corpoGrandeDemaisDeveDar413() {
            Sessao sessao = chat.novaSessao(TELEFONE);

            Resposta resposta = sessao.enviarCorpo("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"text\":\""
                    + "a".repeat(17 * 1024) + "\"}");

            assertThat(resposta.status()).isEqualTo(413);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Mensagem muito grande");
        }

        @Test
        @DisplayName("A 21ª mensagem no mesmo minuto dá 429 com Retry-After")
        void mensagensDemaisDevemDar429() {
            Sessao sessao = chat.novaSessao(TELEFONE);
            for (int i = 0; i < 20; i++) {
                assertThat(sessao.envia("oi").status()).isEqualTo(200);
            }

            Resposta resposta = sessao.envia("oi");

            assertThat(resposta.status()).isEqualTo(429);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Muitas mensagens");
            assertThat(Long.parseLong(resposta.cabecalho("Retry-After"))).isBetween(1L, 60L);
        }

        @Test
        @DisplayName("A 11ª sessão do mesmo IP na hora dá 429")
        void sessoesDemaisDevemDar429() {
            for (int i = 0; i < 10; i++) {
                chat.novaSessao(TELEFONE);
            }

            Resposta resposta = chat.executar(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"" + TELEFONE + "\"}"));

            assertThat(resposta.status()).isEqualTo(429);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Muitas sessões");
            assertThat(resposta.cabecalho("Retry-After")).isNotBlank();
        }

        @Test
        @DisplayName("Com outra transação segurando a conversa, a mensagem espera o lock_timeout e dá 503")
        void conversaTravadaDeveDar503() throws Exception {
            Sessao sessao = chat.novaSessao(TELEFONE);
            sessao.envia("oi");
            CountDownLatch travada = new CountDownLatch(1);
            CountDownLatch liberar = new CountDownLatch(1);
            CompletableFuture<Void> outraTransacao = CompletableFuture.runAsync(() ->
                    transacao.executeWithoutResult(status -> {
                        jdbc.sql("""
                                        SELECT customer_id FROM conversations WHERE customer_id =
                                            (SELECT id FROM customers WHERE external_id = :sessao) FOR UPDATE""")
                                .param("sessao", sessao.id().toString()).query().listOfRows();
                        travada.countDown();
                        aguardar(liberar);
                    }));
            aguardar(travada);

            Resposta resposta = sessao.envia("oi");
            liberar.countDown();
            outraTransacao.get(10, TimeUnit.SECONDS);

            assertThat(resposta.status()).isEqualTo(503);
            assertThat(resposta.json().path("title").asText()).isEqualTo("Atendente ocupado");
            assertThat(resposta.cabecalho("Retry-After")).isEqualTo("1");
        }

        @Test
        @DisplayName("Um bug vira 500 genérico, sem stack trace nem mensagem interna")
        void erroInesperadoDeveDar500Generico() {
            Resposta resposta = chat.novaSessao(TELEFONE).envia(ERRO_PROPOSITAL);

            assertThat(resposta.status()).isEqualTo(500);
            JsonNode problema = resposta.json();
            assertThat(problema.path("title").asText()).isEqualTo("Erro interno");
            assertThat(problema.path("detail").asText())
                    .isEqualTo("Algo deu errado do nosso lado. Tente de novo em instantes.");
            assertThat(resposta.corpo()).doesNotContain("IllegalStateException", "Falha proposital", "at com.");
        }

        @Test
        @DisplayName("Método não permitido e rota inexistente também respondem em português")
        void errosDoSpringDevemTerTituloEmPortugues() {
            Resposta metodo = chat.executar(get("/api/chat/sessions"));
            Resposta rota = chat.executar(get("/api/nada-aqui"));

            assertThat(metodo.status()).isEqualTo(405);
            assertThat(metodo.json().path("title").asText()).isEqualTo("Método não permitido");
            assertThat(rota.status()).isEqualTo(404);
            assertThat(rota.json().path("title").asText()).isEqualTo("Recurso não encontrado");
        }

        private static void aguardar(CountDownLatch sinal) {
            try {
                assertThat(sinal.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    @DisplayName("Com os bastidores desligados")
    @TestPropertySource(properties = "app.web.show-diagnostics=false")
    class SemBastidores {

        @Autowired
        private MockMvc mvcSemBastidores;

        @Test
        @DisplayName("A resposta não traz o campo debug")
        void respostaNaoDeveTrazerDebug() {
            Resposta resposta = new ChatHttp(mvcSemBastidores).novaSessao(TELEFONE).envia("oi");

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(resposta.json().has("debug")).isFalse();
            assertThat(resposta.textos()).contains("Posso te ajudar a:");
        }
    }
}
