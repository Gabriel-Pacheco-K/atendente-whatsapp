package com.gabrielpacheco.atendente.domain.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Perfil do estabelecimento")
class BusinessProfileTest {

    private final BusinessProfile barbearia = new BusinessProfile("Barbearia Navalha de Ouro", "Rua das Palmeiras, 120",
            "(48) 3333-0000", "de terça a sábado",
            List.of(new OpeningPeriod(Set.of(TUESDAY, SATURDAY), LocalTime.of(9, 0), LocalTime.of(14, 0))),
            List.of(new ClosedDate(LocalDate.of(2026, 10, 31), "Confraternização da equipe")),
            List.of(), Map.of());

    @Test
    @DisplayName("Abre só nos dias da semana com expediente")
    void deveAbrirNosDiasComExpediente() {
        assertThat(barbearia.opensOn(LocalDate.of(2026, 10, 13))).isTrue();
        assertThat(barbearia.opensOn(LocalDate.of(2026, 10, 19))).isFalse();
    }

    @Test
    @DisplayName("Conhece os dias fechados e o motivo")
    void deveConhecerOsDiasFechados() {
        assertThat(barbearia.closedOn(LocalDate.of(2026, 10, 31)))
                .map(ClosedDate::reason)
                .contains("Confraternização da equipe");
        assertThat(barbearia.closedOn(LocalDate.of(2026, 10, 24))).isEmpty();
    }
}
