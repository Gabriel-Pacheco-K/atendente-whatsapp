package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import com.gabrielpacheco.atendente.application.flow.BookingConfirmation;
import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.in.Diagnostics;
import com.gabrielpacheco.atendente.application.port.in.HandleIncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import com.gabrielpacheco.atendente.application.port.out.BookingStore;
import com.gabrielpacheco.atendente.application.port.out.ConversationStore;
import com.gabrielpacheco.atendente.application.port.out.CustomerStore;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessage;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessageStore;
import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.Conversation;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.gabrielpacheco.atendente.fixtures.CenaPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ch.qos.logback.classic.Level.ERROR;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.AGENDAMENTOS;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA_15H;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DisplayName("Persistência da conversa")
class PersistenceIntegrationTest extends IntegrationTestBase {

    private static final CustomerRef ANA = new CustomerRef(Channel.WHATSAPP, "5548999990000", "+5548999990000", null);

    @Autowired
    private CustomerStore clientes;

    @Autowired
    private ConversationStore conversas;

    @Autowired
    private BookingStore agendamentos;

    @Autowired
    private ProcessedMessageStore processadas;

    @Autowired
    private HandleIncomingMessage atendente;

    @Autowired
    private TransactionTemplate transacao;

    @Nested
    @DisplayName("Clientes e conversas")
    class ClientesEConversas {

        @Test
        @DisplayName("Oito primeiras mensagens simultâneas do mesmo cliente criam um cliente só, sem violar a chave única")
        void upsertSimultaneoDeveCriarUmClienteSo() throws Exception {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Long>> ids = new ArrayList<>();
            try (ExecutorService threads = Executors.newFixedThreadPool(8)) {
                for (int i = 0; i < 8; i++) {
                    ids.add(threads.submit(() -> {
                        largada.await();
                        return transacao.execute(status -> clientes.upsert(ANA).id());
                    }));
                }
                largada.countDown();
            }

            List<Long> distintos = new ArrayList<>();
            for (Future<Long> id : ids) {
                distintos.add(id.get(10, TimeUnit.SECONDS));
            }
            assertThat(distintos).containsOnly(distintos.get(0));
            assertThat(jdbc.sql("SELECT count(*) FROM customers").query(Long.class).single()).isEqualTo(1);
        }

        @Test
        @DisplayName("Cada mensagem renova o last_seen_at; a data de criação fica a mesma")
        void upsertDeveRenovarUltimaVisita() {
            long id = clientes.upsert(ANA).id();
            relogio.avancar(Duration.ofHours(5));
            clientes.upsert(ANA);

            Map<String, Object> linha = jdbc.sql("SELECT created_at, last_seen_at FROM customers WHERE id = :id")
                    .param("id", id).query().singleRow();
            assertThat(instante(linha.get("created_at"))).isEqualTo(Instant.parse("2026-10-13T17:00:00Z"));
            assertThat(instante(linha.get("last_seen_at"))).isEqualTo(Instant.parse("2026-10-13T22:00:00Z"));
        }

        @Test
        @DisplayName("O nome gravado volta no próximo upsert, e a sessão da web é encontrada pelo UUID")
        void deveGuardarONomeEAcharASessaoDaWeb() {
            Customer web = clientes.createWeb("+55 48 99999-0001");
            clientes.saveName(web.id(), "Gabriel Pacheco");

            Customer achado = clientes.findWeb(UUID.fromString(web.externalId())).orElseThrow();

            assertThat(achado.name()).isEqualTo("Gabriel Pacheco");
            assertThat(clientes.findWeb(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("A conversa nasce no início, e o estado gravado volta igual na próxima trava")
        void conversaDeveFazerIdaEVolta() {
            long cliente = clientes.upsert(ANA).id();
            ChoosingDate escolhendoODia = new ChoosingDate(BookingDraft.EMPTY.withService(CORTE.id())
                    .withProfessional(JOAO.id()));

            Conversation nova = transacao.execute(status -> conversas.lock(cliente));
            transacao.executeWithoutResult(status -> {
                Conversation travada = conversas.lock(cliente);
                travada.moveTo(escolhendoODia, relogio.instant());
                travada.registerMisunderstanding();
                conversas.save(travada);
            });
            Conversation relida = transacao.execute(status -> conversas.lock(cliente));

            assertThat(nova.state()).isEqualTo(Idle.EMPTY);
            assertThat(relida.state()).isEqualTo(escolhendoODia);
            assertThat(relida.misunderstandings()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Idempotência")
    class Idempotencia {

        @Test
        @DisplayName("A resposta guardada volta igual, e só para o mesmo cliente")
        void registroDeveTerEscopoPorCliente() {
            long ana = clientes.upsert(ANA).id();
            long bruno = clientes.upsert(new CustomerRef(Channel.WEB, "sessao-bruno", "+55 48 98888-0002", null)).id();
            ConversationReply resposta = new ConversationReply(List.of(new Reply("Olá!", List.of("Agendar"))),
                    new Diagnostics(InterpreterKind.RULES, Intent.GREETING, 0.5,
                            Map.of("time", "15:00", "date", "2026-10-14"), "IDLE", "IDLE", null),
                    false);

            processadas.register(ana, "msg-1", resposta);
            processadas.register(ana, "msg-1", null);

            Optional<ProcessedMessage> daAna = processadas.find(ana, "msg-1");
            assertThat(daAna).map(ProcessedMessage::reply).contains(resposta);
            assertThat(daAna).map(ProcessedMessage::processedAt).contains(relogio.instant());
            assertThat(processadas.find(bruno, "msg-1")).isEmpty();
        }

        @Test
        @DisplayName("Um turno desfeito desfaz também o registro da mensagem: o reenvio é processado de novo")
        void rollbackDeveDesfazerAIdempotencia() {
            IncomingMessage oi = new IncomingMessage(ANA, MessageKind.TEXT, "oi", "wamid.1", relogio.instant());

            assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
                atendente.handle(oi);
                throw new IllegalStateException("queda do banco simulada");
            })).isInstanceOf(IllegalStateException.class);
            ConversationReply reenvio = atendente.handle(oi);

            assertThat(reenvio.duplicate()).isFalse();
            assertThat(reenvio.replies()).isNotEmpty();
            assertThat(atendente.handle(oi).duplicate()).isTrue();
        }
    }

    @Nested
    @DisplayName("Agendamentos guardados")
    class AgendamentosGuardados {

        @Test
        @DisplayName("O mesmo agendamento do agendamento-api não é guardado duas vezes")
        void appointmentIdDeveSerUnico() {
            long cliente = clientes.upsert(ANA).id();
            Booking agendamento = new Booking(7, cliente, CORTE.id(), CORTE.name(), JOAO.id(), JOAO.name(),
                    CORTE.price(), QUARTA_15H, QUARTA_15H.plusMinutes(30), BookingStatus.SCHEDULED);
            agendamentos.save(agendamento);

            assertThatThrownBy(() -> agendamentos.save(agendamento))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_bookings_appointment");
        }

        @Test
        @DisplayName("Só os ativos e futuros contam, em ordem de início; a posse é conferida pelo cliente")
        void deveListarOsAtivosFuturosDoCliente() {
            long cliente = clientes.upsert(ANA).id();
            LocalDateTime agora = LocalDateTime.of(2026, 10, 13, 14, 0);
            guardar(1, cliente, agora.plusDays(2), BookingStatus.SCHEDULED);
            guardar(2, cliente, agora.plusDays(1), BookingStatus.CONFIRMED);
            guardar(3, cliente, agora.plusDays(3), BookingStatus.CANCELLED);
            guardar(4, cliente, agora.minusDays(1), BookingStatus.SCHEDULED);

            transacao.executeWithoutResult(status -> agendamentos.updateStatus(1, BookingStatus.CANCELLED));

            assertThat(agendamentos.upcoming(cliente, agora)).extracting(Booking::appointmentId).containsExactly(2L);
            assertThat(agendamentos.countActiveUpcoming(cliente, agora)).isEqualTo(1);
            assertThat(agendamentos.belongsTo(2, cliente)).isTrue();
            assertThat(agendamentos.belongsTo(2, cliente + 1)).isFalse();
            assertThat(agendamentos.exists(3)).isTrue();
            assertThat(agendamentos.exists(99)).isFalse();
        }

        @Test
        @DisplayName("Se a gravação local falha depois do POST, o ERROR traz o código para conciliar à mão")
        void falhaDepoisDoPostDeveRegistrarOCodigoParaConciliacao() {
            CustomerRef cliente = new CustomerRef(Channel.WEB, UUID.randomUUID().toString(), CenaPrincipal.TELEFONE,
                    null);
            CenaPrincipal.prepararAgenda(agenda(), 1);
            enviar(cliente, CenaPrincipal.FRASE);
            enviar(cliente, CenaPrincipal.NOME);
            agenda().responder(post(urlEqualTo(AGENDAMENTOS)).willReturn(json(201, precoInvalido())));

            try (CapturaDeLogs logs = CapturaDeLogs.de(BookingConfirmation.class)) {
                assertThatThrownBy(() -> enviar(cliente, "sim")).isInstanceOf(DataIntegrityViolationException.class);

                assertThat(logs.mensagens(ERROR)).containsExactly(
                        "Agendamento #1 criado no agendamento-api, mas a gravação local foi desfeita; conciliar"
                                + " manualmente");
            }
            assertThat(jdbc.sql("SELECT count(*) FROM bookings").query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT state->>'type' FROM conversations").query(String.class).single())
                    .isEqualTo("CONFIRMING_BOOKING");
        }

        /** Um preço que o CHECK da tabela recusa: a gravação falha só no banco, depois de a agenda ter gravado. */
        private static String precoInvalido() {
            return AgendaHttp.agendamento(1, JOAO, CORTE, QUARTA_15H, CenaPrincipal.NOME, CenaPrincipal.TELEFONE,
                    "SCHEDULED").replace("\"price\":45.00", "\"price\":-1");
        }

        private void guardar(long idNaAgenda, long cliente, LocalDateTime inicio, BookingStatus status) {
            agendamentos.save(new Booking(idNaAgenda, cliente, CORTE.id(), CORTE.name(), JOAO.id(), JOAO.name(),
                    new BigDecimal("45.00"), inicio, inicio.plusMinutes(30), status));
        }
    }

    @Nested
    @DisplayName("Retenção")
    class Retencao {

        private static final RetentionJob.Windows JANELAS = new RetentionJob.Windows(Duration.ofDays(14),
                Duration.ofHours(24), Duration.ofDays(7), Duration.ofDays(180));

        @Test
        @DisplayName("Idempotências de mais de 14 dias somem, e respostas da web de mais de 24 h viram nulas")
        void deveLimparAsMensagensProcessadasForaDaJanela() {
            long cliente = cliente(Channel.WHATSAPP, "5548999990000", dias(1), dias(1));
            mensagem(cliente, "velha", dias(15));
            mensagem(cliente, "de-anteontem", dias(2));
            mensagem(cliente, "recente", Duration.ofHours(1));

            purgar();

            List<Map<String, Object>> restantes = jdbc.sql("""
                            SELECT message_id, reply IS NOT NULL AS guardada FROM processed_messages
                            ORDER BY message_id""")
                    .query().listOfRows();
            assertThat(restantes).extracting(linha -> linha.get("message_id"), linha -> linha.get("guardada"))
                    .containsExactly(tuple("de-anteontem", false), tuple("recente", true));
        }

        @Test
        @DisplayName("Sessões da web de mais de 7 dias são apagadas, com a conversa e o que mais for delas")
        void deveApagarSessoesDaWebAntigas() {
            long antiga = cliente(Channel.WEB, "sessao-antiga", dias(8), Duration.ZERO);
            cliente(Channel.WEB, "sessao-da-semana", dias(6), dias(6));
            mensagem(antiga, "m-1", Duration.ofHours(1));

            purgar();

            assertThat(idsExternos()).containsExactly("sessao-da-semana");
            assertThat(jdbc.sql("SELECT count(*) FROM processed_messages").query(Long.class).single()).isZero();
        }

        @Test
        @DisplayName("Clientes do WhatsApp sumidos há 180 dias são apagados, menos quem tem horário marcado pela frente")
        void deveApagarClientesDoWhatsAppSumidos() {
            cliente(Channel.WHATSAPP, "sumido", dias(400), dias(181));
            long comHorario = cliente(Channel.WHATSAPP, "sumido-com-horario", dias(400), dias(181));
            long soPassado = cliente(Channel.WHATSAPP, "sumido-com-horario-passado", dias(400), dias(181));
            long cancelado = cliente(Channel.WHATSAPP, "sumido-com-horario-cancelado", dias(400), dias(181));
            cliente(Channel.WHATSAPP, "ativo", dias(400), dias(10));
            LocalDateTime agora = LocalDateTime.of(2026, 10, 13, 14, 0);
            agendamento(1, comHorario, agora.plusDays(3), BookingStatus.SCHEDULED);
            agendamento(2, soPassado, agora.minusDays(200), BookingStatus.COMPLETED);
            agendamento(3, cancelado, agora.plusDays(3), BookingStatus.CANCELLED);

            purgar();

            assertThat(idsExternos()).containsExactly("ativo", "sumido-com-horario");
        }

        private void purgar() {
            new RetentionJob(jdbc, relogio, JANELAS).purge();
        }

        private long cliente(Channel canal, String idExterno, Duration criadoHa, Duration vistoHa) {
            return jdbc.sql("""
                            INSERT INTO customers (channel, external_id, phone, created_at, last_seen_at)
                            VALUES (:canal, :idExterno, '+5548999990000', :criado, :visto) RETURNING id""")
                    .param("canal", canal.name()).param("idExterno", idExterno)
                    .param("criado", haTanto(criadoHa)).param("visto", haTanto(vistoHa))
                    .query(Long.class).single();
        }

        private void mensagem(long cliente, String id, Duration ha) {
            jdbc.sql("""
                            INSERT INTO processed_messages (customer_id, message_id, reply, processed_at)
                            VALUES (:cliente, :id, '{"replies":[]}', :quando)""")
                    .param("cliente", cliente).param("id", id).param("quando", haTanto(ha))
                    .update();
        }

        private void agendamento(long idNaAgenda, long cliente, LocalDateTime inicio, BookingStatus status) {
            agendamentos.save(new Booking(idNaAgenda, cliente, CORTE.id(), CORTE.name(), JOAO.id(), JOAO.name(),
                    CORTE.price(), inicio, inicio.plusMinutes(30), status));
        }

        private List<String> idsExternos() {
            return jdbc.sql("SELECT external_id FROM customers ORDER BY external_id").query(String.class).list();
        }

        private Timestamp haTanto(Duration quanto) {
            return Timestamp.from(relogio.instant().minus(quanto));
        }

        private static Duration dias(int quantos) {
            return Duration.ofDays(quantos);
        }
    }

    private ConversationReply enviar(CustomerRef cliente, String texto) {
        return atendente.handle(new IncomingMessage(cliente, MessageKind.TEXT, texto, UUID.randomUUID().toString(),
                relogio.instant()));
    }

    private static Instant instante(Object valorDoBanco) {
        return ((Timestamp) valorDoBanco).toInstant();
    }
}
