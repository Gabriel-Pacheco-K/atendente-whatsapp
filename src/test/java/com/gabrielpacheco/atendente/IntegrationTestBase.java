package com.gabrielpacheco.atendente;

import com.gabrielpacheco.atendente.application.CatalogProvider;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.InMemoryBookingStore;
import com.gabrielpacheco.atendente.fixtures.InMemoryConversationStore;
import com.gabrielpacheco.atendente.fixtures.InMemoryCustomerStore;
import com.gabrielpacheco.atendente.fixtures.InMemoryProcessedMessageStore;
import com.gabrielpacheco.atendente.fixtures.MutableClock;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base dos testes com Spring: PostgreSQL real e WireMock para a agenda e para a Graph API.
 * Os contêineres e servidores sobem uma vez por JVM e nunca param, para que a porta não mude e o contexto do
 * Spring continue reaproveitável entre as classes de teste. Antes de cada teste, a agenda volta a ter só o catálogo
 * do seed e o relógio volta para terça, 13/10/2026, 14:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({IntegrationTestBase.RelogioDeTeste.class, IntegrationTestBase.PortasEmMemoria.class})
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final WireMockServer AGENDA = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer GRAPH = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        AGENDA.start();
        GRAPH.start();
    }

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock relogio;

    @Autowired
    private CatalogProvider catalogo;

    @DynamicPropertySource
    static void servicosExternos(DynamicPropertyRegistry registry) {
        registry.add("app.scheduling-api.base-url", AGENDA::baseUrl);
        registry.add("app.whatsapp.sending.graph-url", GRAPH::baseUrl);
    }

    @BeforeEach
    void limparEstadoCompartilhado() {
        jdbc.sql("TRUNCATE customers CASCADE").update();
        AGENDA.resetAll();
        GRAPH.resetAll();
        relogio.ajustar(MutableClock.REFERENCIA);
        agenda().comCatalogoDoSeed();
        catalogo.invalidate();
    }

    protected static AgendaHttp agenda() {
        return AgendaHttp.em(AGENDA);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelogioDeTeste {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return MutableClock.naReferencia();
        }
    }

    /** Os stores da conversa em memória, no lugar das portas de saída que ainda não têm adaptador. */
    @TestConfiguration(proxyBeanMethods = false)
    static class PortasEmMemoria {

        @Bean
        InMemoryCustomerStore clientesEmMemoria() {
            return new InMemoryCustomerStore();
        }

        @Bean
        InMemoryConversationStore conversasEmMemoria(MutableClock relogio) {
            return new InMemoryConversationStore(relogio);
        }

        @Bean
        InMemoryBookingStore agendamentosEmMemoria() {
            return new InMemoryBookingStore();
        }

        @Bean
        InMemoryProcessedMessageStore mensagensProcessadasEmMemoria(MutableClock relogio) {
            return new InMemoryProcessedMessageStore(relogio);
        }
    }
}
