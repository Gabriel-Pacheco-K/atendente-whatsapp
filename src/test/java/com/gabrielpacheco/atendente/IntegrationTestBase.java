package com.gabrielpacheco.atendente;

import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.adapter.in.web.ChatRateLimits;
import com.gabrielpacheco.atendente.adapter.out.nlu.rules.RuleBasedInterpreter;
import com.gabrielpacheco.atendente.application.CatalogProvider;
import com.gabrielpacheco.atendente.application.port.out.ConversationStore;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.ConversationStoreComFalha;
import com.gabrielpacheco.atendente.fixtures.MutableClock;
import com.gabrielpacheco.atendente.fixtures.WhatsAppHttp;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Spring continue reaproveitável entre as classes de teste. Antes de cada teste, o banco é esvaziado, a agenda volta
 * a ter só o catálogo do seed, a Graph API aceita os envios, os limites de taxa zeram e o relógio volta para terça,
 * 13/10/2026, 14:00. O webhook do WhatsApp fica ligado, com os segredos do application-test.yml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.ConfiguracaoDeTeste.class)
public abstract class IntegrationTestBase {

    /** Uma mensagem com este texto faz o interpretador falhar, como um bug no meio do turno. */
    protected static final String ERRO_PROPOSITAL = "erro-proposital";

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

    @Autowired
    private ObjectProvider<ChatRateLimits> limitesDoChat;

    @Autowired
    private ObjectProvider<RateLimiter> limiteDoWhatsApp;

    @Autowired
    protected ConversationStoreComFalha conversas;

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
        WhatsAppHttp.aceitarEnvios(GRAPH);
        limitesDoChat.ifAvailable(ChatRateLimits::reset);
        limiteDoWhatsApp.ifAvailable(RateLimiter::reset);
        conversas.voltarAoNormal();
    }

    protected static AgendaHttp agenda() {
        return AgendaHttp.em(AGENDA);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoDeTeste {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return MutableClock.naReferencia();
        }

        @Bean
        @Primary
        ConversationStoreComFalha conversationStoreComFalha(
                @Qualifier("jpaConversationStore") ConversationStore real) {
            return new ConversationStoreComFalha(real);
        }

        @Bean
        @Primary
        MessageInterpreter interpretadorQueFalhaDeProposito() {
            RuleBasedInterpreter regras = new RuleBasedInterpreter();
            return (texto, contexto) -> {
                if (texto.contains(ERRO_PROPOSITAL)) {
                    throw new IllegalStateException("Falha proposital do teste");
                }
                return regras.interpret(texto, contexto);
            };
        }
    }
}
