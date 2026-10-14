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

    private static final LocalDate QUARTA = HOJE.plusDays(1);

    @Test
    @DisplayName("Lista profissionais, serviços e horários livres para o formulário")
    void formulario() throws Exception {
        Cookie cookie = entrar();
        agenda().comHorariosLivres(JOAO, CORTE, QUARTA, "09:00", "09:30");

        mvc.perform(get("/api/painel/catalogo").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionals[0].name").value(JOAO.name()))
                .andExpect(jsonPath("$.services[0].price").value(45.0));
        mvc.perform(get("/api/painel/horarios").cookie(cookie).param("professionalId", "1").param("serviceId", "1")
                        .param("date", QUARTA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("09:00"))
                .andExpect(jsonPath("$[1]").value("09:30"));
        mvc.perform(get("/api/painel/catalogo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("O dono marca um cliente que ligou")
    void marcarNaMao() throws Exception {
        Cookie cookie = entrar();
        agenda().aceitandoAgendamento(30, JOAO, CORTE, QUARTA.atTime(9, 0), "Bento Ramos", "48999990002");

        mvc.perform(post("/api/painel/agendamentos").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalId":1,"serviceId":1,"date":"2026-10-14","time":"09:00",
                                 "name":" Bento Ramos ","phone":"48999990002","block":false}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.blocked").value(false));

        AGENDA.verify(exactly(1), AgendaHttp.postsDeAgendamento().withRequestBody(equalToJson("""
                {"professionalId":1,"serviceId":1,"startAt":"2026-10-14T09:00","customerName":"Bento Ramos",
                 "customerPhone":"48999990002"}""", true, true)));
    }

    @Test
    @DisplayName("Bloquear ocupa o horário sem cliente e não entra no previsto")
    void bloquear() throws Exception {
        Cookie cookie = entrar();
        agenda().aceitandoAgendamento(31, JOAO, CORTE, QUARTA.atTime(12, 0), "Horário bloqueado", "00000000");

        mvc.perform(post("/api/painel/agendamentos").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalId":1,"serviceId":1,"date":"2026-10-14","time":"12:00","block":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.phone").doesNotExist());

        agenda().comAgendaDoDia(JOAO, QUARTA, AgendaHttp.agendamento(31, JOAO, CORTE, QUARTA.atTime(12, 0),
                        "Horário bloqueado", "00000000", "SCHEDULED"))
                .comAgendaDoDia(PEDRO, QUARTA);
        mvc.perform(get("/api/painel/agenda").param("date", QUARTA.toString()).cookie(cookie))
                .andExpect(jsonPath("$.professionals[0].appointments[0].blocked").value(true))
                .andExpect(jsonPath("$.appointments").value(0))
                .andExpect(jsonPath("$.expected").value(0));
    }

    @Test
    @DisplayName("Sem nome, sem telefone ou com horário ocupado, explica o que houve")
    void marcarComErro() throws Exception {
        Cookie cookie = entrar();
        String base = "{\"professionalId\":1,\"serviceId\":1,\"date\":\"2026-10-14\",\"time\":\"09:00\",";
        mvc.perform(post("/api/painel/agendamentos").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"name\":\"\",\"phone\":\"48999990002\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Coloque o nome do cliente."));
        mvc.perform(post("/api/painel/agendamentos").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"name\":\"Ana Souza\",\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/painel/agendamentos").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Souza\"}"))
                .andExpect(status().isBadRequest());

        agenda().responder(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(AGENDAMENTOS))
                .willReturn(json(409, problema(409, "Horário indisponível", "Ocupado"))));
        mvc.perform(post("/api/painel/agendamentos").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"name\":\"Ana Souza\",\"phone\":\"48999990001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esse horário já está ocupado. Escolha outro."));
        AGENDA.verify(exactly(1), AgendaHttp.postsDeAgendamento());
    }

    @Test
    @DisplayName("O faturamento do ano soma os dias concluídos em cada mês")
    void faturamento() throws Exception {
        Cookie cookie = entrar();
        agenda().responder(com.github.tomakehurst.wiremock.client.WireMock.get(
                        urlEqualTo(AGENDAMENTOS + "/revenue?from=2026-01-01&to=2026-12-31"))
                .willReturn(json(200, """
                        [{"date":"2026-09-30","completed":2,"revenue":80.00},
                         {"date":"2026-10-01","completed":1,"revenue":45.00},
                         {"date":"2026-10-13","completed":3,"revenue":150.00}]""")));

        mvc.perform(get("/api/painel/faturamento").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.completed").value(6))
                .andExpect(jsonPath("$.revenue").value(275.0))
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.months[8].revenue").value(80.0))
                .andExpect(jsonPath("$.months[9].completed").value(4))
                .andExpect(jsonPath("$.months[9].revenue").value(195.0))
                .andExpect(jsonPath("$.months[0].revenue").value(0));
        mvc.perform(get("/api/painel/faturamento").param("ano", "1999").cookie(cookie))
                .andExpect(status().isBadRequest());
    }
}
