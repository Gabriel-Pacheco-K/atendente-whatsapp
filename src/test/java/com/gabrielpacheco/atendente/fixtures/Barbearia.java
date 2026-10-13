package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.domain.business.Faq;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.catalog.WorkingPeriod;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/** A Barbearia Navalha de Ouro como o seed do agendamento-api a cadastra; hoje é terça, 13/10/2026, 14:00. */
public final class Barbearia {

    public static final LocalDate HOJE = LocalDate.of(2026, 10, 13);
    public static final LocalTime AGORA = LocalTime.of(14, 0);
    public static final int DIAS_A_FRENTE = 30;

    public static final ServiceType CORTE = new ServiceType(1, "Corte masculino", 30, new BigDecimal("45.00"),
            List.of("corte", "cortar", "cabelo", "degrade", "social", "maquina", "tesoura"));
    public static final ServiceType BARBA = new ServiceType(2, "Barba", 30, new BigDecimal("35.00"),
            List.of("barba", "barbear", "bigode", "aparar"));
    public static final ServiceType CORTE_E_BARBA = new ServiceType(3, "Corte + barba", 60, new BigDecimal("70.00"),
            List.of("combo", "corte e barba", "cabelo e barba", "completo"));
    public static final ServiceType SOBRANCELHA = new ServiceType(4, "Sobrancelha", 15, new BigDecimal("15.00"),
            List.of("sobrancelha"));

    public static final Professional JOAO = new Professional(1, "João Batista", expediente(
            LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(19, 0)));
    public static final Professional PEDRO = new Professional(2, "Pedro Henrique", expediente(
            LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(19, 0)));

    private static final Catalog CATALOGO =
            new Catalog(List.of(CORTE, BARBA, CORTE_E_BARBA, SOBRANCELHA), List.of(JOAO, PEDRO), true);

    private static final List<Faq> FAQS = List.of(
            new Faq("pagamento", List.of("pix", "cartao", "dinheiro", "pagamento", "pagar", "debito", "credito"),
                    "Aceitamos Pix, cartão de débito e crédito e dinheiro."),
            new Faq("estacionamento", List.of("estacionamento", "estacionar", "carro"),
                    "Temos 3 vagas em frente à barbearia e estacionamento rotativo na rua."),
            new Faq("criancas", List.of("crianca", "infantil", "filho", "menino"),
                    "Atendemos crianças a partir de 3 anos, com um responsável."));

    private Barbearia() {
    }

    public static Catalog catalogo() {
        return CATALOGO;
    }

    public static List<Faq> faqs() {
        return FAQS;
    }

    public static InterpretationContext contexto(Expectation expectativa, String... opcoes) {
        return new InterpretationContext(HOJE, AGORA, CATALOGO, FAQS, expectativa, List.of(opcoes), DIAS_A_FRENTE);
    }

    /** Terça a sexta com dois turnos e sábado das 08:00 às 14:00, como no seed. */
    private static List<WorkingPeriod> expediente(LocalTime abre, LocalTime almoco, LocalTime volta, LocalTime fecha) {
        List<WorkingPeriod> periodos = new ArrayList<>();
        for (DayOfWeek dia : List.of(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)) {
            periodos.add(new WorkingPeriod(dia, abre, almoco));
            periodos.add(new WorkingPeriod(dia, volta, fecha));
        }
        periodos.add(new WorkingPeriod(SATURDAY, LocalTime.of(8, 0), LocalTime.of(14, 0)));
        return periodos;
    }
}
