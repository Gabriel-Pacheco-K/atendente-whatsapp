package com.gabrielpacheco.atendente.domain.booking;

import com.gabrielpacheco.atendente.domain.booking.Suggestion.Alternatives;
import com.gabrielpacheco.atendente.domain.booking.Suggestion.ExactMatch;
import com.gabrielpacheco.atendente.domain.booking.Suggestion.NoSlots;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sugestão de horários")
class SlotSuggesterTest {

    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime AGORA_MAIS_30 = LocalDateTime.of(2026, 10, 13, 14, 30);

    private static final Duration CORTE = Duration.ofMinutes(30);

    private final SlotSuggester sugestor = new SlotSuggester();

    @Test
    @DisplayName("Horário pedido livre é escolhido direto")
    void horarioLivreDeveSerEscolhido() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "09:00", "11:30"));

        Suggestion sugestao = sugestor.suggest(dia, exata("10:00"), CORTE, 6);

        assertThat(sugestao).isEqualTo(new ExactMatch(opcao(SEXTA, "10:00", JOAO)));
    }

    @Test
    @DisplayName("Horário ocupado oferece os 3 mais próximos, em ordem; no empate vence o mais cedo")
    void horarioOcupadoDeveOferecerOsTresMaisProximos() {
        DayAvailability dia = dia(SEXTA, SEXTA,
                new ProfessionalDaySlots(JOAO, SEXTA, horas("09:00", "09:30", "10:30", "11:00", "11:30")));

        Suggestion sugestao = sugestor.suggest(dia, exata("10:00"), CORTE, 6);

        assertThat(sugestao).isEqualTo(new Alternatives(
                List.of(opcao(SEXTA, "09:00", JOAO), opcao(SEXTA, "09:30", JOAO), opcao(SEXTA, "10:30", JOAO)),
                Note.REQUESTED_TAKEN));
    }

    @Test
    @DisplayName("Horário fora do expediente não é \"já tem cliente\"")
    void horarioForaDoExpedienteDeveTerNotaPropria() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "13:00", "18:30"));

        assertThat(nota(sugestor.suggest(dia, exata("20:00"), CORTE, 6))).isEqualTo(Note.OUTSIDE_HOURS);
        assertThat(nota(sugestor.suggest(dia, exata("12:15"), CORTE, 6))).isEqualTo(Note.OUTSIDE_HOURS);
    }

    @Test
    @DisplayName("Horário em que o serviço não termina antes do fim do turno também é fora do expediente")
    void servicoQueNaoCabeNoTurnoDeveSerForaDoExpediente() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "13:00", "18:00"));

        assertThat(nota(sugestor.suggest(dia, exata("18:45"), CORTE, 6))).isEqualTo(Note.OUTSIDE_HOURS);
        assertThat(nota(sugestor.suggest(dia, exata("18:30"), Duration.ofMinutes(60), 6)))
                .isEqualTo(Note.OUTSIDE_HOURS);
        assertThat(nota(sugestor.suggest(dia, exata("18:30"), CORTE, 6))).isEqualTo(Note.REQUESTED_TAKEN);
    }

    @Test
    @DisplayName("Horário fora da grade de 15 minutos não é \"já tem cliente\"")
    void horarioForaDaGradeDeveTerNotaPropria() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "09:00", "11:30"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, exata("10:10"), CORTE, 6);

        assertThat(sugestao.note()).isEqualTo(Note.OFF_GRID);
        assertThat(horarios(sugestao)).containsExactly("10:00", "10:15", "10:30");
    }

    @Test
    @DisplayName("Horário que já passou ou está em cima tem nota própria")
    void horarioEmCimaDeveTerNotaPropria() {
        LocalDate hoje = AGORA_MAIS_30.toLocalDate();
        DayAvailability dia = dia(hoje, hoje, livres(JOAO, hoje, "14:30", "18:30"));

        assertThat(nota(sugestor.suggest(dia, exata("14:15"), CORTE, 6))).isEqualTo(Note.TOO_SOON);
        assertThat(nota(sugestor.suggest(dia, exata("10:00"), CORTE, 6))).isEqualTo(Note.TOO_SOON);
    }

    @Test
    @DisplayName("Período pedido mostra só os horários dele")
    void periodoDeveFiltrarOsHorarios() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "09:00", "11:30", "13:00", "18:30"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, periodo(PartOfDay.AFTERNOON), CORTE, 6);

        assertThat(sugestao.note()).isEqualTo(Note.NONE);
        assertThat(sugestao.options()).allMatch(o -> PartOfDay.AFTERNOON.contains(o.startAt().toLocalTime()));
    }

    @Test
    @DisplayName("Período cheio mostra o dia todo com a nota de período cheio")
    void periodoCheioDeveMostrarODiaTodo() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "13:00", "14:00"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, periodo(PartOfDay.MORNING), CORTE, 6);

        assertThat(sugestao.note()).isEqualTo(Note.PERIOD_FULL);
        assertThat(sugestao.options()).hasSize(5);
    }

    @Test
    @DisplayName("Sem preferência, espalha 6 horários pelo dia, incluindo o primeiro e o último")
    void semPreferenciaDeveEspalharSeisHorarios() {
        DayAvailability dia = dia(SEXTA, SEXTA, livres(JOAO, SEXTA, "08:00", "11:45"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, null, CORTE, 6);

        assertThat(horarios(sugestao)).containsExactly("08:00", "08:45", "09:30", "10:15", "11:00", "11:45");
    }

    @Test
    @DisplayName("Com até 6 horários livres, mostra todos")
    void poucosHorariosDevemSerMostradosTodos() {
        DayAvailability dia = dia(SEXTA, SEXTA, new ProfessionalDaySlots(JOAO, SEXTA, horas("09:00", "15:00")));

        assertThat(horarios((Alternatives) sugestor.suggest(dia, null, CORTE, 6))).containsExactly("09:00", "15:00");
    }

    @Test
    @DisplayName("\"Tanto faz\" no sábado de manhã alterna João e Pedro")
    void tantoFazDeveAlternarNoSabado() {
        DayAvailability dia = dia(SABADO, SABADO, livres(JOAO, SABADO, "08:00", "13:30"),
                livres(PEDRO, SABADO, "08:00", "13:30"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, periodo(PartOfDay.MORNING), CORTE, 6);

        assertThat(sugestao.options()).containsExactly(opcao(SABADO, "08:00", JOAO), opcao(SABADO, "08:45", PEDRO),
                opcao(SABADO, "09:30", JOAO), opcao(SABADO, "10:15", PEDRO), opcao(SABADO, "11:00", JOAO),
                opcao(SABADO, "11:45", PEDRO));
    }

    @Test
    @DisplayName("\"Tanto faz\" num dia de semana alterna e desempata por quem tem mais horários livres")
    void tantoFazDeveAlternarNaTerca() {
        DayAvailability dia = dia(LocalDate.of(2026, 10, 19), TERCA,
                livres(JOAO, TERCA, "09:00", "11:30", "13:00", "18:30"),
                livres(PEDRO, TERCA, "10:00", "12:30", "14:00", "18:30"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, null, CORTE, 6);

        assertThat(sugestao.note()).isEqualTo(Note.MOVED_TO_NEXT_DAY);
        assertThat(sugestao.options()).containsExactly(opcao(TERCA, "09:00", JOAO), opcao(TERCA, "10:45", PEDRO),
                opcao(TERCA, "13:00", JOAO), opcao(TERCA, "14:45", PEDRO), opcao(TERCA, "16:45", JOAO),
                opcao(TERCA, "18:30", PEDRO));
    }

    @Test
    @DisplayName("No empate de opções recebidas, vai para quem tem o dia mais livre")
    void empateDeveIrParaQuemTemMaisHorariosLivres() {
        DayAvailability dia = dia(SEXTA, SEXTA, new ProfessionalDaySlots(JOAO, SEXTA, horas("10:00")),
                new ProfessionalDaySlots(PEDRO, SEXTA, horas("10:00", "11:00")));

        assertThat(sugestor.suggest(dia, exata("10:00"), CORTE, 6)).isEqualTo(new ExactMatch(opcao(SEXTA, "10:00", PEDRO)));
    }

    @Test
    @DisplayName("Num dia diferente do pedido, o horário pedido guia os mais próximos")
    void diaMovidoDeveUsarOHorarioPedido() {
        DayAvailability dia = dia(SEXTA, SABADO, livres(JOAO, SABADO, "08:00", "13:30"));

        Alternatives sugestao = (Alternatives) sugestor.suggest(dia, exata("10:00"), CORTE, 6);

        assertThat(sugestao.note()).isEqualTo(Note.MOVED_TO_NEXT_DAY);
        assertThat(horarios(sugestao)).containsExactly("09:45", "10:00", "10:15");
    }

    @Test
    @DisplayName("Sem nenhum horário, diz até onde procurou")
    void semHorariosDeveDizerAteOndeProcurou() {
        LocalDate ate = SEXTA.plusDays(6);
        DayAvailability vazio = new DayAvailability(SEXTA, ate, List.of(), RequestedDayStatus.FULL, null, ate,
                AGORA_MAIS_30);

        assertThat(sugestor.suggest(vazio, exata("10:00"), CORTE, 6)).isEqualTo(new NoSlots(ate));
    }

    // Apoio

    private static DayAvailability dia(LocalDate pedido, LocalDate encontrado, ProfessionalDaySlots... livres) {
        RequestedDayStatus status = pedido.equals(encontrado)
                ? RequestedDayStatus.AVAILABLE : RequestedDayStatus.CLOSED_WEEKDAY;
        return new DayAvailability(pedido, encontrado, List.of(livres), status, null, null, AGORA_MAIS_30);
    }

    /** Horários de 15 em 15 minutos em cada intervalo fechado [início, fim]. */
    private static ProfessionalDaySlots livres(Professional profissional, LocalDate data, String... intervalos) {
        List<LocalTime> horarios = new ArrayList<>();
        for (int i = 0; i < intervalos.length; i += 2) {
            LocalTime fim = LocalTime.parse(intervalos[i + 1]);
            for (LocalTime t = LocalTime.parse(intervalos[i]); !t.isAfter(fim); t = t.plusMinutes(15)) {
                horarios.add(t);
            }
        }
        return new ProfessionalDaySlots(profissional, data, horarios);
    }

    private static List<LocalTime> horas(String... horas) {
        return Stream.of(horas).map(LocalTime::parse).toList();
    }

    private static SlotOption opcao(LocalDate data, String hora, Professional profissional) {
        return new SlotOption(data.atTime(LocalTime.parse(hora)), profissional.id(), profissional.name());
    }

    private static TimePreference exata(String hora) {
        return new TimePreference.Exact(LocalTime.parse(hora));
    }

    private static TimePreference periodo(PartOfDay parte) {
        return new TimePreference.Period(parte);
    }

    private static Note nota(Suggestion sugestao) {
        return ((Alternatives) sugestao).note();
    }

    private static List<String> horarios(Alternatives sugestao) {
        return sugestao.options().stream().map(o -> o.startAt().toLocalTime().toString()).toList();
    }
}
