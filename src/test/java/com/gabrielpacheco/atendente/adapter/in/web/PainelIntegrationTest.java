package com.gabrielpacheco.atendente.adapter.in.web;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.AGENDAMENTOS;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.problema;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.BARBA;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.HOJE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Painel do dono")
class PainelIntegrationTest extends IntegrationTestBase {

    /** Cada teste entra de um IP diferente, para o limite de tentativas de um não atrapalhar o outro. */
    private static final AtomicInteger IPS = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    private final String ip = "10.0.0." + IPS.getAndIncrement();

    private MockHttpServletRequestBuilder doIp(MockHttpServletRequestBuilder pedido) {
        return pedido.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private Cookie entrar() throws Exception {
        Cookie cookie = mvc.perform(doIp(post("/api/painel/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"senha-de-teste\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("painel");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    @Test
    @DisplayName("Senha errada não entra")
    void senhaErrada() throws Exception {
        mvc.perform(doIp(post("/api/painel/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sem login a agenda responde 401")
    void semLogin() throws Exception {
        mvc.perform(get("/api/painel/agenda")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/painel/agenda").cookie(new Cookie("painel", "inventado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Depois de 5 tentativas no minuto, o login responde 429")
    void limiteDeTentativas() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(doIp(post("/api/painel/login")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"errada\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(doIp(post("/api/painel/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"senha-de-teste\"}")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Mostra a agenda de hoje por profissional, com o previsto e o feito")
    void agendaDoDia() throws Exception {
        Cookie cookie = entrar();
        agenda().comAgendaDoDia(JOAO, HOJE,
                        AgendaHttp.agendamento(2, JOAO, CORTE, HOJE.atTime(16, 0), "Bento Ramos", "48999990002", "SCHEDULED"),
                        AgendaHttp.agendamento(1, JOAO, BARBA, HOJE.atTime(9, 0), "Ana Souza", "48999990001", "COMPLETED"),
                        AgendaHttp.agendamento(3, JOAO, CORTE, HOJE.atTime(11, 0), "Carla Mendes", "48999990003", "CANCELLED"))
                .comAgendaDoDia(PEDRO, HOJE);

        mvc.perform(get("/api/painel/agenda").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(HOJE.toString()))
                .andExpect(jsonPath("$.professionals[0].name").value(JOAO.name()))
                .andExpect(jsonPath("$.professionals[0].appointments[0].customer").value("Ana Souza"))
                .andExpect(jsonPath("$.professionals[0].appointments[0].start").value("09:00"))
                .andExpect(jsonPath("$.professionals[1].appointments").isEmpty())
                .andExpect(jsonPath("$.appointments").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.expected").value(80.0))
                .andExpect(jsonPath("$.done").value(35.0));
    }

    @Test
    @DisplayName("Concluído, não veio e cancelar chegam no agendamento-api")
    void acoes() throws Exception {
        Cookie cookie = entrar();
        String marcado = AgendaHttp.agendamento(7, JOAO, CORTE, HOJE.atTime(16, 0), "Ana Souza", "48999990001",
                "COMPLETED");
        agenda().responder(patch(urlEqualTo(AGENDAMENTOS + "/7/complete")).willReturn(json(200, marcado)))
                .responder(patch(urlEqualTo(AGENDAMENTOS + "/7/no-show")).willReturn(json(200, marcado)))
                .responder(patch(urlEqualTo(AGENDAMENTOS + "/7/cancel")).willReturn(json(200, marcado)));

        mvc.perform(post("/api/painel/agendamentos/7/concluir").cookie(cookie)).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/painel/agendamentos/7/nao-veio").cookie(cookie)).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/painel/agendamentos/7/cancelar").cookie(cookie)).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/painel/agendamentos/7/apagar").cookie(cookie)).andExpect(status().isNotFound());

        AGENDA.verify(exactly(1), patchRequestedFor(urlEqualTo(AGENDAMENTOS + "/7/complete")));
        AGENDA.verify(exactly(1), patchRequestedFor(urlEqualTo(AGENDAMENTOS + "/7/no-show")));
        AGENDA.verify(exactly(1), patchRequestedFor(urlEqualTo(AGENDAMENTOS + "/7/cancel"))
                .withRequestBody(equalToJson("{\"reason\":\"Cancelado pelo estabelecimento\"}", true, true)));
    }

    @Test
    @DisplayName("Erros do agendamento-api viram mensagens claras")
    void erros() throws Exception {
        Cookie cookie = entrar();
        agenda().responder(patch(urlEqualTo(AGENDAMENTOS + "/8/complete"))
                        .willReturn(json(422, problema(422, "Regra de negócio violada", "Já cancelado"))))
                .responder(patch(urlEqualTo(AGENDAMENTOS + "/9/complete"))
                        .willReturn(json(404, problema(404, "Recurso não encontrado", "Não existe"))));

        mvc.perform(post("/api/painel/agendamentos/8/concluir").cookie(cookie))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/painel/agendamentos/9/concluir").cookie(cookie))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/painel/agenda").param("date", LocalDate.of(2026, 10, 20).toString()).cookie(cookie))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Depois de sair, o cookie não vale mais")
    void sair() throws Exception {
        Cookie cookie = entrar();
        mvc.perform(post("/api/painel/logout").cookie(cookie)).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/painel/agenda").cookie(cookie)).andExpect(status().isUnauthorized());
    }
}
