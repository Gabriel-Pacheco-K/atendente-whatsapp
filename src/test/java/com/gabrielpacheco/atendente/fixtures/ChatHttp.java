package com.gabrielpacheco.atendente.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** O chat web pelo HTTP, como a página o usa: cria a sessão e manda mensagens com um clientMessageId novo cada. */
public final class ChatHttp {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mvc;

    public ChatHttp(MockMvc mvc) {
        this.mvc = mvc;
    }

    public Sessao novaSessao(String telefone) {
        Resposta criada = executar(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + telefone + "\"}"));
        assertThat(criada.status()).as("criação da sessão").isEqualTo(201);
        return new Sessao(UUID.fromString(criada.json().path("sessionId").asText()));
    }

    public Resposta executar(MockHttpServletRequestBuilder pedido) {
        try {
            MockHttpServletResponse resposta = mvc.perform(pedido).andReturn().getResponse();
            return new Resposta(resposta.getStatus(), resposta.getContentAsString(StandardCharsets.UTF_8), resposta);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public final class Sessao {

        private final UUID id;
        private UUID ultimoId;

        private Sessao(UUID id) {
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        public Resposta envia(String texto) {
            return envia(texto, UUID.randomUUID());
        }

        public Resposta envia(String texto, UUID clientMessageId) {
            ultimoId = clientMessageId;
            return enviarCorpo("{\"clientMessageId\":\"" + clientMessageId + "\",\"text\":" + jsonString(texto) + "}");
        }

        /** Reenvia com o mesmo clientMessageId, como a página faz depois de uma falha de rede. */
        public Resposta reenviaAUltima(String texto) {
            return envia(texto, ultimoId);
        }

        public Resposta enviarCorpo(String corpo) {
            return executar(post("/api/chat/sessions/" + id + "/messages").contentType(MediaType.APPLICATION_JSON)
                    .content(corpo));
        }
    }

    public record Resposta(int status, String corpo, MockHttpServletResponse http) {

        public JsonNode json() {
            try {
                return JSON.readTree(corpo);
            } catch (Exception e) {
                throw new IllegalStateException("Resposta não é JSON: " + corpo, e);
            }
        }

        /** Os textos das respostas do atendente, separados por uma linha em branco. */
        public String textos() {
            List<String> textos = new ArrayList<>();
            json().path("replies").forEach(reply -> textos.add(reply.path("text").asText()));
            return textos.stream().collect(Collectors.joining("\n\n"));
        }

        public String cabecalho(String nome) {
            return http.getHeader(nome);
        }
    }

    private static String jsonString(String texto) {
        try {
            return JSON.writeValueAsString(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
