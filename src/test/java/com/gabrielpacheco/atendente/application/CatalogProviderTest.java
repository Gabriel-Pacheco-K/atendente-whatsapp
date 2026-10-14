package com.gabrielpacheco.atendente.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.catalog.WorkingPeriod;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import com.gabrielpacheco.atendente.fixtures.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Catálogo da agenda")
class CatalogProviderTest {

    private static final Duration VALIDADE = Duration.ofSeconds(60);

    private final MutableClock relogio = MutableClock.naReferencia();
    private final AgendaDeCatalogo agenda = new AgendaDeCatalogo();
    private final List<Runnable> recargasPendentes = new ArrayList<>();
    private final CatalogProvider catalogo =
            new CatalogProvider(agenda, Barbearia.perfil(), relogio, VALIDADE, recargasPendentes::add);
    private final ListAppender<ILoggingEvent> avisos = new ListAppender<>();

    @BeforeEach
    void capturarAvisos() {
        avisos.start();
        logger().addAppender(avisos);
    }

    @AfterEach
    void soltarAvisos() {
        logger().detachAppender(avisos);
    }

    @Test
    @DisplayName("Junta aos serviços da agenda os apelidos do business.yml, pelo nome")
    void deveJuntarOsApelidosPeloNome() {
        agenda.servicos = List.of(new ServiceType(1, "CORTE MASCULINO", 30, new BigDecimal("45.00"), List.of()));

        catalogo.warmUp();
        Catalog carregado = catalogo.current();

        assertThat(carregado.online()).isTrue();
        assertThat(carregado.services().get(0).aliases()).contains("corte", "cabelo", "degrade");
    }

    @Test
    @DisplayName("Dentro da validade, não consulta a agenda de novo")
    void dentroDaValidadeNaoDeveConsultarDeNovo() {
        catalogo.warmUp();
        relogio.avancar(Duration.ofSeconds(59));

        catalogo.current();

        assertThat(agenda.cargas).isEqualTo(1);
        assertThat(recargasPendentes).isEmpty();
    }

    @Test
    @DisplayName("Vencido, devolve o catálogo antigo na hora e recarrega em segundo plano")
    void vencidoDeveRecarregarEmSegundoPlano() {
        catalogo.warmUp();
        Catalog antigo = catalogo.current();
        agenda.servicos = List.of(Barbearia.servicosDaAgenda().get(1));
        relogio.avancar(Duration.ofSeconds(61));

        assertThat(catalogo.current()).isSameAs(antigo);
        assertThat(catalogo.current()).isSameAs(antigo);
        assertThat(recargasPendentes).hasSize(1);

        recargasPendentes.remove(0).run();

        assertThat(catalogo.current().services()).extracting(ServiceType::name).containsExactly("Barba");
    }

    @Test
    @DisplayName("Se a recarga falha, o catálogo antigo continua valendo")
    void recargaQueFalhaDeveManterOAntigo() {
        catalogo.warmUp();
        Catalog antigo = catalogo.current();
        agenda.fora = true;
        relogio.avancar(Duration.ofSeconds(61));

        catalogo.current();
        recargasPendentes.remove(0).run();

        assertThat(catalogo.current()).isSameAs(antigo);
        assertThat(mensagens()).anyMatch(m -> m.contains("mantendo o anterior"));
    }

    @Test
    @DisplayName("Com a agenda fora na subida, começa offline, e a consulta seguinte tenta de novo em segundo plano")
    void semCargaNaSubidaDeveFicarOfflineETentarDeNovo() {
        agenda.fora = true;
        catalogo.warmUp();

        assertThat(catalogo.current()).isEqualTo(Catalog.offline());
        assertThat(mensagens()).anyMatch(m -> m.contains("começando no modo offline"));

        agenda.fora = false;
        recargasPendentes.remove(0).run();
        assertThat(catalogo.current().online()).isTrue();
    }

    @Test
    @DisplayName("Sem catálogo, a consulta nunca espera a agenda: devolve o offline e recarrega uma vez, em segundo plano")
    void semCatalogoNaoDeveConsultarAAgendaNaHora() {
        for (int turno = 0; turno < 3; turno++) {
            assertThat(catalogo.current()).isEqualTo(Catalog.offline());
        }

        assertThat(agenda.cargas).isZero();
        assertThat(recargasPendentes).hasSize(1);
    }

    @Test
    @DisplayName("Invalidado, devolve o antigo e recarrega em segundo plano, sem esperar a validade")
    void invalidadoDeveRecarregarSemEsperarAValidade() {
        catalogo.warmUp();
        Catalog antigo = catalogo.current();
        agenda.servicos = List.of(Barbearia.servicosDaAgenda().get(1));

        catalogo.invalidate();

        assertThat(catalogo.current()).isSameAs(antigo);
        recargasPendentes.remove(0).run();
        assertThat(catalogo.current().services()).extracting(ServiceType::name).containsExactly("Barba");
        assertThat(agenda.cargas).isEqualTo(2);
    }

    @Test
    @DisplayName("Avisa quando o business.yml tem apelidos de um serviço que a agenda não tem")
    void deveAvisarDeApelidoSemServico() {
        agenda.servicos = Barbearia.servicosDaAgenda().subList(0, 3);

        catalogo.warmUp();

        assertThat(mensagens()).anyMatch(m -> m.contains("apelidos para 'Sobrancelha'"));
    }

    @Test
    @DisplayName("Avisa quando um profissional trabalha num dia em que o estabelecimento não abre")
    void deveAvisarDeExpedienteEmDiaFechado() {
        List<WorkingPeriod> comSegunda = new ArrayList<>(JOAO.periods());
        comSegunda.add(new WorkingPeriod(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        agenda.profissionais = List.of(new Professional(1, "João Batista", comSegunda));

        catalogo.warmUp();

        assertThat(mensagens()).anyMatch(m -> m.contains("profissional #1 trabalha às segundas"));
    }

    private List<String> mensagens() {
        return avisos.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(CatalogProvider.class);
    }

    /** Só o catálogo da agenda, contando as cargas. */
    private static final class AgendaDeCatalogo implements SchedulingPort {

        private List<Professional> profissionais = Barbearia.profissionais();
        private List<ServiceType> servicos = Barbearia.servicosDaAgenda();
        private boolean fora;
        private int cargas;

        @Override
        public List<Professional> professionals() {
            if (fora) {
                throw new SchedulingUnavailableException("conexão recusada");
            }
            cargas++;
            return profissionais;
        }

        @Override
        public List<ServiceType> services() {
            return servicos;
        }

        @Override
        public List<LocalTime> freeSlots(long professionalId, long serviceId, LocalDate date) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppointmentInfo book(NewAppointment appointment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppointmentInfo find(long appointmentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppointmentInfo cancel(long appointmentId, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AppointmentInfo> dayAgenda(long professionalId, LocalDate date) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SchedulingPort within(Deadline deadline) {
            return this;
        }
    }
}
