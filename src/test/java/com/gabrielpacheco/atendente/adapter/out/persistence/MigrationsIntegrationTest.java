package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Migrações do banco")
class MigrationsIntegrationTest extends IntegrationTestBase {

    private static final String ESTADO =
            "{\"type\":\"CHOOSING_SLOT\",\"draft\":{\"serviceId\":1,\"professionalId\":2,\"anyProfessional\":false,"
                    + "\"date\":\"2026-10-16\",\"time\":{\"type\":\"EXACT\",\"time\":\"10:00\"},\"chosen\":null},"
                    + "\"date\":\"2026-10-16\",\"options\":[{\"startAt\":\"2026-10-16T10:30\",\"professionalId\":2,"
                    + "\"professionalName\":\"Pedro Henrique\"}]}";

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("O Flyway aplica a V1 e o Hibernate valida o esquema")
    void deveAplicarAMigracaoInicial() {
        List<String> versoes = jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                .query(String.class).list();
        List<String> tabelas = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' ORDER BY table_name""")
                .query(String.class).list();

        assertThat(versoes).containsExactly("1");
        assertThat(tabelas).containsExactly("bookings", "conversations", "customers", "processed_messages");
    }

    @Test
    @DisplayName("O estado da conversa em JSONB volta igual ao que foi gravado")
    void deveFazerIdaEVoltaDoEstadoEmJsonb() throws Exception {
        long cliente = criarCliente("WEB", "sessao-1");
        jdbc.sql("INSERT INTO conversations (customer_id, state, updated_at) VALUES (:id, CAST(:state AS jsonb), :agora)")
                .param("id", cliente).param("state", ESTADO).param("agora", agora())
                .update();

        String lido = jdbc.sql("SELECT state::text FROM conversations WHERE customer_id = :id")
                .param("id", cliente).query(String.class).single();

        assertThat(json.readTree(lido)).isEqualTo(json.readTree(ESTADO));
    }

    @Test
    @DisplayName("O mesmo canal e identificador externo não se repetem")
    void deveRecusarClienteDuplicadoNoMesmoCanal() {
        criarCliente("WHATSAPP", "5548999990000");
        criarCliente("WEB", "5548999990000");

        assertThatThrownBy(() -> criarCliente("WHATSAPP", "5548999990000"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_customers_channel_external");
    }

    @Test
    @DisplayName("Canal desconhecido e agendamento que termina antes de começar são recusados")
    void deveRecusarValoresForaDasRegras() {
        long cliente = criarCliente("WEB", "sessao-1");

        assertThatThrownBy(() -> criarCliente("SMS", "sessao-2")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> criarAgendamento(cliente, 7, "2026-10-14 15:00", "2026-10-14 14:30"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_bookings_interval");
    }

    @Test
    @DisplayName("A idempotência é por cliente: o mesmo id de mensagem vale para clientes diferentes")
    void deveEscoparAIdempotenciaPorCliente() {
        long ana = criarCliente("WEB", "sessao-ana");
        long bruno = criarCliente("WEB", "sessao-bruno");

        registrarMensagem(ana, "8b3f0c2e-6a51-4d0e-9a1f-2f6c1d9e7a10");
        registrarMensagem(bruno, "8b3f0c2e-6a51-4d0e-9a1f-2f6c1d9e7a10");

        assertThatThrownBy(() -> registrarMensagem(ana, "8b3f0c2e-6a51-4d0e-9a1f-2f6c1d9e7a10"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Apagar o cliente leva junto conversa, agendamentos guardados e idempotência")
    void deveApagarEmCascata() {
        long cliente = criarCliente("WEB", "sessao-1");
        jdbc.sql("INSERT INTO conversations (customer_id, state, updated_at) VALUES (:id, '{\"type\":\"IDLE\"}', :agora)")
                .param("id", cliente).param("agora", agora()).update();
        criarAgendamento(cliente, 7, "2026-10-14 15:00", "2026-10-14 15:30");
        registrarMensagem(cliente, "m-1");

        jdbc.sql("DELETE FROM customers WHERE id = :id").param("id", cliente).update();

        for (String tabela : List.of("conversations", "bookings", "processed_messages")) {
            assertThat(jdbc.sql("SELECT count(*) FROM " + tabela).query(Long.class).single()).as(tabela).isZero();
        }
    }

    private long criarCliente(String canal, String idExterno) {
        return jdbc.sql("""
                        INSERT INTO customers (channel, external_id, phone, created_at, last_seen_at)
                        VALUES (:canal, :idExterno, '+55 48 99999-0001', :agora, :agora) RETURNING id""")
                .param("canal", canal).param("idExterno", idExterno).param("agora", agora())
                .query(Long.class).single();
    }

    private void criarAgendamento(long cliente, long idNaAgenda, String inicio, String fim) {
        jdbc.sql("""
                        INSERT INTO bookings (customer_id, appointment_id, service_id, service_name, professional_id,
                            professional_name, price, start_at, end_at, status, created_at, updated_at)
                        VALUES (:cliente, :idNaAgenda, 1, 'Corte masculino', 1, 'João Batista', 45.00,
                            CAST(:inicio AS timestamp), CAST(:fim AS timestamp), 'SCHEDULED', :agora, :agora)""")
                .param("cliente", cliente).param("idNaAgenda", idNaAgenda)
                .param("inicio", inicio).param("fim", fim).param("agora", agora())
                .update();
    }

    private void registrarMensagem(long cliente, String idMensagem) {
        jdbc.sql("INSERT INTO processed_messages (customer_id, message_id, processed_at) VALUES (:cliente, :id, :agora)")
                .param("cliente", cliente).param("id", idMensagem).param("agora", agora())
                .update();
    }

    private Timestamp agora() {
        return Timestamp.from(clock.instant());
    }
}
