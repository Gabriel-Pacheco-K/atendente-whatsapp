package com.gabrielpacheco.atendente.domain.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Texto do horário de funcionamento")
class OpeningHoursTextTest {

    @Test
    @DisplayName("Agrupa dias vizinhos com o mesmo horário e lista os fechados numa linha no fim")
    void deveAgruparOHorarioDaBarbearia() {
        List<OpeningPeriod> horario = List.of(
                periodo(Set.of(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), "09:00", "19:00"),
                periodo(Set.of(SATURDAY), "08:00", "14:00"));

        assertThat(OpeningHoursText.describe(horario)).isEqualTo("""
                Terça a sexta: 09:00 às 19:00
                Sábado: 08:00 às 14:00
                Segunda e domingo: fechado""");
    }

    @Test
    @DisplayName("Dois dias vizinhos viram \"X e Y\" e um horário diferente no meio quebra o grupo")
    void deveQuebrarOGrupoQuandoOHorarioMuda() {
        List<OpeningPeriod> horario = List.of(
                periodo(Set.of(MONDAY, TUESDAY, THURSDAY), "09:00", "18:00"),
                periodo(Set.of(WEDNESDAY), "13:00", "18:00"));

        assertThat(OpeningHoursText.describe(horario)).isEqualTo("""
                Segunda e terça: 09:00 às 18:00
                Quarta: 13:00 às 18:00
                Quinta: 09:00 às 18:00
                Sexta, sábado e domingo: fechado""");
    }

    @Test
    @DisplayName("Dia com pausa para o almoço mostra os dois turnos")
    void deveMostrarOsDoisTurnos() {
        List<OpeningPeriod> horario = List.of(
                periodo(Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY), "13:00", "19:00"),
                periodo(Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY), "09:00", "12:00"));

        assertThat(OpeningHoursText.describe(horario)).isEqualTo("Segunda a domingo: 09:00 às 12:00 e 13:00 às 19:00");
    }

    private static OpeningPeriod periodo(Set<DayOfWeek> dias, String abre, String fecha) {
        return new OpeningPeriod(dias, LocalTime.parse(abre), LocalTime.parse(fecha));
    }
}
