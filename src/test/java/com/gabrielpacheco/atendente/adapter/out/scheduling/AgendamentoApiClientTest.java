package com.gabrielpacheco.atendente.adapter.out.scheduling;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.SlotTakenException;
import com.gabrielpacheco.atendente.application.port.out.UnknownBookingOutcomeException;
import com.gabrielpacheco.atendente.config.AppProperties;
import com.gabrielpacheco.atendente.config.SchedulingClientConfig;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.catalog.WorkingPeriod;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.gabrielpacheco.atendente.fixtures.MutableClock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.WARN;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.AGENDAMENTOS;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.postsDeAgendamento;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.problema;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** O contrato com o agendamento-api, conferido contra respostas no formato real dele. */
@DisplayName("Cliente HTTP do agendamento-api")
class AgendamentoApiClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);
    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    private static final LocalDateTime QUARTA_15H = QUARTA.atTime(15, 0);
    private static final NewAppointment PEDIDO = new NewAppointment(1, 1, QUARTA_15H, "Gabriel Pacheco",
            "+55 48 99999-0001");

    @RegisterExtension
    static final WireMockExtension API = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort())
            .build();

    private final AgendaHttp agenda = AgendaHttp.em(API);
    private SchedulingPort cliente;
    private CapturaDeLogs logs;

    @BeforeEach
    void criarCliente() {
        cliente = clientePara(API.baseUrl());
        logs = CapturaDeLogs.de(ProblemDetailMapper.class);
    }

    @AfterEach
    void soltarLogs() {
        logs.close();
    }

    @Nested
    @DisplayName("Respostas de sucesso")
    class Sucesso {

        @Test
        @DisplayName("O POST manda exatamente o corpo que a API espera, com a hora sem segundos")
        void deveEnviarOCorpoDoAgendamentoNoFormatoDaApi() {
            agenda.aceitandoAgendamento(7, JOAO, CORTE, QUARTA_15H, "Gabriel Pacheco", "+55 48 99999-0001");

            AppointmentInfo criado = cliente.book(PEDIDO);

            API.verify(postsDeAgendamento().withRequestBody(equalToJson("""
                    {"professionalId":1,"serviceId":1,"startAt":"2026-10-14T15:00",
                     "customerName":"Gabriel Pacheco","customerPhone":"+55 48 99999-0001"}""")));
            assertThat(criado).isEqualTo(new AppointmentInfo(7, 1, "João Batista", "Corte masculino",
                    new BigDecimal("45.00"), "Gabriel Pacheco", "+55 48 99999-0001", QUARTA_15H,
                    QUARTA_15H.plusMinutes(30), BookingStatus.SCHEDULED));
        }

        @Test
        @DisplayName("Os horários livres chegam como \"09:00:00\" e viram LocalTime")
        void deveLerOsHorariosLivres() {
            agenda.comHorariosLivres(JOAO, CORTE, QUARTA, "09:00", "09:15", "15:00");

            assertThat(cliente.freeSlots(1, 1, QUARTA))
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(15, 0));
        }

        @Test
        @DisplayName("O catálogo traz o expediente de cada profissional e os serviços sem apelidos")
        void deveLerOCatalogo() {
            agenda.comCatalogoDoSeed();

            List<Professional> profissionais = cliente.professionals();
            List<ServiceType> servicos = cliente.services();

            assertThat(profissionais).extracting(Professional::name).containsExactly("João Batista", "Pedro Henrique");
            assertThat(profissionais.get(0).periods()).hasSize(9)
                    .contains(new WorkingPeriod(DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(19, 0)));
            assertThat(servicos).extracting(ServiceType::name)
                    .containsExactly("Corte masculino", "Barba", "Corte + barba", "Sobrancelha");
            assertThat(servicos).allSatisfy(servico -> assertThat(servico.aliases()).isEmpty());
        }

        @Test
        @DisplayName("A agenda do dia vem como lista, com todos os status")
        void deveLerAAgendaDoDia() {
            agenda.comAgendaDoDia(JOAO, QUARTA,
                    AgendaHttp.agendamento(3, JOAO, CORTE, QUARTA.atTime(9, 0), "Ana", "+55 48 90000-0001",
                            "CANCELLED"),
                    AgendaHttp.agendamento(4, JOAO, CORTE, QUARTA_15H, "Bruno", "+55 48 90000-0002", "CONFIRMED"));

            assertThat(cliente.dayAgenda(1, QUARTA)).extracting(AppointmentInfo::id, AppointmentInfo::status)
                    .containsExactly(tuple(3L, BookingStatus.CANCELLED), tuple(4L, BookingStatus.CONFIRMED));
        }

        @Test
        @DisplayName("Um status que a API passe a usar vira UNKNOWN, sem quebrar a leitura")
        void statusDesconhecidoDeveVirarUnknown() {
            API.stubFor(get(urlEqualTo(AGENDAMENTOS + "/9")).willReturn(json(200,
                    AgendaHttp.agendamento(9, JOAO, CORTE, QUARTA_15H, "Ana", "+55 48 90000-0001", "RESCHEDULED"))));

            assertThat(cliente.find(9).status()).isEqualTo(BookingStatus.UNKNOWN);
        }

        @Test
        @DisplayName("O cancelamento manda o motivo num PATCH")
        void deveCancelarComOMotivo() {
            API.stubFor(patch(urlEqualTo(AGENDAMENTOS + "/9/cancel")).willReturn(json(200,
                    AgendaHttp.agendamento(9, JOAO, CORTE, QUARTA_15H, "Ana", "+55 48 90000-0001", "CANCELLED"))));

            AppointmentInfo cancelado = cliente.cancel(9, "Cancelado pelo cliente no atendente virtual (chat web)");

            assertThat(cancelado.status()).isEqualTo(BookingStatus.CANCELLED);
            API.verify(patchRequestedFor(urlEqualTo(AGENDAMENTOS + "/9/cancel")).withRequestBody(
                    equalToJson("{\"reason\":\"Cancelado pelo cliente no atendente virtual (chat web)\"}")));
        }
    }

    @Nested
    @DisplayName("Erros de regra e de contrato")
    class ErrosDeRegra {

        @Test
        @DisplayName("409 no POST é horário ocupado, com o detail da API")
        void conflitoNoPostDeveSerHorarioOcupado() {
            responderAoPost(409, problema(409, "Horário indisponível",
                    "O horário 15:00 já está ocupado para João Batista."));

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(SlotTakenException.class)
                    .extracting("detail").isEqualTo("O horário 15:00 já está ocupado para João Batista.");
        }

        @Test
        @DisplayName("422 no POST repassa a regra violada, para o cliente ler")
        void regraVioladaNoPostDeveSerRecusa() {
            responderAoPost(422, problema(422, "Regra de negócio violada",
                    "Agendamentos precisam de pelo menos 30 minutos de antecedência."));

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(BookingRejectedException.class)
                    .extracting("detail").isEqualTo("Agendamentos precisam de pelo menos 30 minutos de antecedência.");
        }

        @Test
        @DisplayName("400 é contrato quebrado: o ERROR cita os campos, nunca os valores")
        void dadosInvalidosDevemSerContratoQuebrado() {
            responderAoPost(400, """
                    {"type":"about:blank","title":"Dados inválidos","status":400,
                     "detail":"Um ou mais campos são inválidos.","errors":{"customerPhone":"telefone inválido"}}""");

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(SchedulingContractException.class);
            assertThat(logs.mensagens(ERROR)).singleElement().asString()
                    .contains("customerPhone").doesNotContain("telefone inválido", "99999");
        }

        @Test
        @DisplayName("404 na consulta ou no cancelamento é agendamento que não existe mais")
        void naoEncontradoDeveSerAgendamentoInexistente() {
            API.stubFor(get(urlEqualTo(AGENDAMENTOS + "/9")).willReturn(json(404,
                    problema(404, "Recurso não encontrado", "Agendamento 9 não encontrado."))));
            API.stubFor(patch(urlEqualTo(AGENDAMENTOS + "/9/cancel")).willReturn(json(404,
                    problema(404, "Recurso não encontrado", "Agendamento 9 não encontrado."))));

            assertThatThrownBy(() -> cliente.find(9)).isInstanceOf(AppointmentNotFoundException.class);
            assertThatThrownBy(() -> cliente.cancel(9, "motivo")).isInstanceOf(AppointmentNotFoundException.class);
        }

        @Test
        @DisplayName("404 na disponibilidade é contrato quebrado: o catálogo está desatualizado")
        void naoEncontradoNaDisponibilidadeDeveSerContratoQuebrado() {
            API.stubFor(get(urlPathEqualTo("/api/professionals/1/availability")).willReturn(json(404,
                    problema(404, "Recurso não encontrado", "Profissional 1 não encontrado."))));

            assertThatThrownBy(() -> cliente.freeSlots(1, 1, QUARTA)).isInstanceOf(SchedulingContractException.class);
        }

        @Test
        @DisplayName("Um title diferente do esperado gera o aviso de mudança de contrato")
        void titleInesperadoDeveGerarAvisoDeContrato() {
            responderAoPost(409, problema(409, "Conflict", "O horário 15:00 já está ocupado para João Batista."));

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(SlotTakenException.class);
            assertThat(logs.mensagens(WARN))
                    .contains("Possível mudança de contrato no agendamento-api: status 409 com title 'Conflict'");
        }

        @Test
        @DisplayName("Um corpo HTML de proxy não quebra a tradução do erro")
        void corpoHtmlNaoDeveQuebrar() {
            API.stubFor(get(urlEqualTo(AGENDAMENTOS + "/9")).willReturn(aResponse().withStatus(404)
                    .withHeader("Content-Type", "text/html").withBody("<html><body>Not Found</body></html>")));

            assertThatThrownBy(() -> cliente.find(9)).isInstanceOf(AppointmentNotFoundException.class);
            assertThat(logs.mensagens(WARN)).anyMatch(m -> m.contains("status 404 com title 'null'"));
        }

        @Test
        @DisplayName("Um sucesso com corpo ilegível é contrato quebrado")
        void corpoDeSucessoIlegivelDeveSerContratoQuebrado() {
            API.stubFor(get(urlEqualTo(AGENDAMENTOS + "/9")).willReturn(json(200, "{\"id\":\"nove\"")));

            assertThatThrownBy(() -> cliente.find(9)).isInstanceOf(SchedulingContractException.class);
        }
    }

    @Nested
    @DisplayName("Falhas do servidor e da rede")
    class FalhasDoServidor {

        @Test
        @DisplayName("500 com o JSON padrão do Spring Boot é falha definitiva: a API desfez tudo")
        void erroInternoDeveSerIndisponibilidade() {
            responderAoPost(500, AgendaHttp.erroPadraoDoBoot(500, "Internal Server Error"));

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(SchedulingUnavailableException.class);
            API.verify(exactly(1), postsDeAgendamento());
        }

        @Test
        @DisplayName("502 no POST deixa o resultado desconhecido, e o POST não é repetido")
        void gatewayNoPostDeveDeixarResultadoDesconhecido() {
            responderAoPost(502, "<html>Bad Gateway</html>");

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(UnknownBookingOutcomeException.class);
            API.verify(exactly(1), postsDeAgendamento());
        }

        @Test
        @DisplayName("POST que passa do read timeout deixa o resultado desconhecido, sem repetir")
        void postLentoDeveDeixarResultadoDesconhecido() {
            API.stubFor(post(urlEqualTo(AGENDAMENTOS)).willReturn(json(201,
                            AgendaHttp.agendamento(7, JOAO, CORTE, QUARTA_15H, "Gabriel Pacheco", "+55 48 99999-0001",
                                    "SCHEDULED"))
                    .withFixedDelay((int) READ_TIMEOUT.multipliedBy(3).toMillis())));

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(UnknownBookingOutcomeException.class);
            API.verify(exactly(1), postsDeAgendamento());
        }

        @Test
        @DisplayName("Conexão derrubada depois do envio deixa o resultado desconhecido, sem repetir")
        void conexaoResetadaNoPostDeveDeixarResultadoDesconhecido() {
            API.stubFor(post(urlEqualTo(AGENDAMENTOS))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(() -> cliente.book(PEDIDO)).isInstanceOf(UnknownBookingOutcomeException.class);
            API.verify(exactly(1), postsDeAgendamento());
        }

        @Test
        @DisplayName("Sem conseguir conectar, o POST nem saiu: é indisponibilidade, não resultado incerto")
        void conexaoRecusadaNoPostDeveSerIndisponibilidade() throws IOException {
            SchedulingPort semServidor = clientePara("http://127.0.0.1:" + portaFechada());

            assertThatThrownBy(() -> semServidor.book(PEDIDO)).isInstanceOf(SchedulingUnavailableException.class);
        }

        @Test
        @DisplayName("Timeout no PATCH deixa o cancelamento incerto")
        void patchLentoDeveDeixarResultadoDesconhecido() {
            API.stubFor(patch(urlEqualTo(AGENDAMENTOS + "/9/cancel")).willReturn(json(200, "{}")
                    .withFixedDelay((int) READ_TIMEOUT.multipliedBy(3).toMillis())));

            assertThatThrownBy(() -> cliente.cancel(9, "motivo")).isInstanceOf(UnknownBookingOutcomeException.class);
            API.verify(exactly(1), patchRequestedFor(urlEqualTo(AGENDAMENTOS + "/9/cancel")));
        }
    }

    @Nested
    @DisplayName("Repetição das consultas")
    class Repeticao {

        private static final String CONSULTA = AGENDAMENTOS + "/9";

        @Test
        @DisplayName("Um GET que recebe 503 é repetido uma vez e então dá certo")
        void getDeveSerRepetidoDepoisDeUm503() {
            API.stubFor(get(urlEqualTo(CONSULTA)).inScenario("instavel").whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(503)).willSetStateTo("de pé"));
            API.stubFor(get(urlEqualTo(CONSULTA)).inScenario("instavel").whenScenarioStateIs("de pé")
                    .willReturn(json(200, AgendaHttp.agendamento(9, JOAO, CORTE, QUARTA_15H, "Ana",
                            "+55 48 90000-0001", "SCHEDULED"))));

            assertThat(cliente.find(9).id()).isEqualTo(9);
            API.verify(exactly(2), getRequestedFor(urlEqualTo(CONSULTA)));
        }

        @Test
        @DisplayName("Dois 503 seguidos: a agenda está indisponível, depois de duas tentativas só")
        void doisErrosSeguidosDevemSerIndisponibilidade() {
            API.stubFor(get(urlEqualTo(CONSULTA)).willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(() -> cliente.find(9)).isInstanceOf(SchedulingUnavailableException.class);
            API.verify(exactly(2), getRequestedFor(urlEqualTo(CONSULTA)));
        }

        @Test
        @DisplayName("500 não é repetido: a API respondeu e falhou de verdade")
        void erroInternoNaoDeveSerRepetido() {
            API.stubFor(get(urlEqualTo(CONSULTA)).willReturn(json(500, AgendaHttp.erroPadraoDoBoot(500, "Erro"))));

            assertThatThrownBy(() -> cliente.find(9)).isInstanceOf(SchedulingUnavailableException.class);
            API.verify(exactly(1), getRequestedFor(urlEqualTo(CONSULTA)));
        }

        @Test
        @DisplayName("A repetição não acontece se não couber no que resta do prazo do turno")
        void naoDeveRepetirForaDoPrazoDoTurno() {
            API.stubFor(get(urlEqualTo(CONSULTA)).willReturn(aResponse().withStatus(503)));
            Deadline quaseAcabando = Deadline.after(READ_TIMEOUT, MutableClock.naReferencia());

            assertThatThrownBy(() -> cliente.within(quaseAcabando).find(9))
                    .isInstanceOf(SchedulingUnavailableException.class);
            API.verify(exactly(1), getRequestedFor(urlEqualTo(CONSULTA)));
        }
    }

    private void responderAoPost(int status, String corpo) {
        API.stubFor(post(urlEqualTo(AGENDAMENTOS)).withHeader("Content-Type", equalTo("application/json"))
                .willReturn(json(status, corpo)));
    }

    private static SchedulingPort clientePara(String baseUrl) {
        return SchedulingClientConfig.create(RestClient.builder(), new AppProperties.SchedulingApi(baseUrl,
                Duration.ofSeconds(1), READ_TIMEOUT, Duration.ofSeconds(60), Duration.ofSeconds(1), 14));
    }

    private static int portaFechada() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
