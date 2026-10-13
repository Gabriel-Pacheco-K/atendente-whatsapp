package com.gabrielpacheco.atendente.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * O agendamento-api no WireMock, com o JSON exatamente como ele responde (horários com segundos, status em texto,
 * ProblemDetail nos erros de regra). Os dados são os mesmos da {@link Barbearia}.
 */
public final class AgendaHttp {

    public static final String AGENDAMENTOS = "/api/appointments";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATA_E_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Stubbing servidor;

    private AgendaHttp(Stubbing servidor) {
        this.servidor = servidor;
    }

    /** Vale para o WireMockServer dos testes com Spring e para a extensão JUnit dos testes do adaptador. */
    public static AgendaHttp em(Stubbing servidor) {
        return new AgendaHttp(servidor);
    }

    // Respostas prontas

    public AgendaHttp comCatalogoDoSeed() {
        servidor.stubFor(get(urlEqualTo("/api/professionals")).willReturn(json(200, profissionais())));
        servidor.stubFor(get(urlEqualTo("/api/services")).willReturn(json(200, servicos())));
        return this;
    }

    public AgendaHttp comHorariosLivres(Professional profissional, ServiceType servico, LocalDate dia,
                                        String... horarios) {
        servidor.stubFor(get(urlPathEqualTo("/api/professionals/" + profissional.id() + "/availability"))
                .withQueryParam("serviceId", equalTo(String.valueOf(servico.id())))
                .withQueryParam("date", equalTo(dia.toString()))
                .willReturn(json(200, disponibilidade(profissional, servico, dia, horarios))));
        return this;
    }

    /** O POST cria o agendamento {@code id} para quem pediu, ecoando nome e telefone do pedido. */
    public AgendaHttp aceitandoAgendamento(long id, Professional profissional, ServiceType servico,
                                           LocalDateTime inicio, String nome, String telefone) {
        servidor.stubFor(post(urlEqualTo(AGENDAMENTOS))
                .willReturn(json(201, agendamento(id, profissional, servico, inicio, nome, telefone, "SCHEDULED"))));
        return this;
    }

    public AgendaHttp comAgendaDoDia(Professional profissional, LocalDate dia, String... agendamentos) {
        servidor.stubFor(get(urlPathEqualTo(AGENDAMENTOS))
                .withQueryParam("professionalId", equalTo(String.valueOf(profissional.id())))
                .withQueryParam("date", equalTo(dia.toString()))
                .willReturn(json(200, "[" + String.join(",", agendamentos) + "]")));
        return this;
    }

    public AgendaHttp responder(MappingBuilder pedido) {
        servidor.stubFor(pedido);
        return this;
    }

    // JSON do agendamento-api

    public static String profissionais() {
        return escrever(Barbearia.profissionais().stream().map(AgendaHttp::profissional).toList());
    }

    public static String servicos() {
        return escrever(Barbearia.servicosDaAgenda().stream().map(servico -> mapa(
                "id", servico.id(), "name", servico.name(), "durationMinutes", servico.durationMinutes(),
                "price", servico.price())).toList());
    }

    public static String disponibilidade(Professional profissional, ServiceType servico, LocalDate dia,
                                         String... horarios) {
        List<String> livres = Arrays.stream(horarios).map(h -> LocalTime.parse(h).format(HORA)).toList();
        return escrever(mapa("professionalId", profissional.id(), "serviceId", servico.id(), "date", dia.toString(),
                "durationMinutes", servico.durationMinutes(), "slots", livres));
    }

    public static String agendamento(long id, Professional profissional, ServiceType servico, LocalDateTime inicio,
                                     String nome, String telefone, String status) {
        return escrever(mapa("id", id, "professionalId", profissional.id(), "professionalName", profissional.name(),
                "service", servico.name(), "price", servico.price(), "customerName", nome, "customerPhone", telefone,
                "startAt", inicio.format(DATA_E_HORA),
                "endAt", inicio.plusMinutes(servico.durationMinutes()).format(DATA_E_HORA),
                "status", status, "cancellationReason", null));
    }

    /** Um erro como o GlobalExceptionHandler do agendamento-api monta. */
    public static String problema(int status, String titulo, String detalhe) {
        return escrever(mapa("type", "about:blank", "title", titulo, "status", status, "detail", detalhe,
                "instance", AGENDAMENTOS));
    }

    /** O JSON padrão do Spring Boot, que o agendamento-api devolve num 500. */
    public static String erroPadraoDoBoot(int status, String erro) {
        return escrever(mapa("timestamp", "2026-10-13T17:00:00.000+00:00", "status", status, "error", erro,
                "path", AGENDAMENTOS));
    }

    public static ResponseDefinitionBuilder json(int status, String corpo) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(corpo);
    }

    public static RequestPatternBuilder postsDeAgendamento() {
        return postRequestedFor(urlEqualTo(AGENDAMENTOS));
    }

    public static RequestPatternBuilder consultasDaAgendaDoDia() {
        return getRequestedFor(urlPathEqualTo(AGENDAMENTOS));
    }

    private static Map<String, Object> profissional(Professional profissional) {
        List<Map<String, Object>> expediente = profissional.periods().stream()
                .map(turno -> mapa("dayOfWeek", turno.day().name(), "start", turno.start().format(HORA),
                        "end", turno.end().format(HORA)))
                .toList();
        return mapa("id", profissional.id(), "name", profissional.name(), "specialty", "Barbeiro",
                "workingHours", expediente);
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
}
