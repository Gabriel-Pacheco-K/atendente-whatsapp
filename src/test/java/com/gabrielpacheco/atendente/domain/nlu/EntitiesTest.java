package com.gabrielpacheco.atendente.domain.nlu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidades reconhecidas")
class EntitiesTest {

    private static final TimePreference QUINZE_HORAS = new TimePreference.Exact(LocalTime.of(15, 0));

    @Test
    @DisplayName("Serviço, profissional, \"tanto faz\", data, problema de data e hora contam como dados de agenda")
    void deveReconhecerDadosDeAgenda() {
        assertThat(Entities.none().hasBookingData()).isFalse();
        assertThat(comServico(1L).hasBookingData()).isTrue();
        assertThat(new Entities(null, null, true, null, null, null, null, null, null, false, false).hasBookingData())
                .isTrue();
        assertThat(new Entities(null, null, false, null, DateIssue.PAST, null, null, null, null, false, false)
                .hasBookingData()).isTrue();
        assertThat(new Entities(null, null, false, null, null, null, 2, "Ana", "pagamento", true, true)
                .hasBookingData()).isFalse();
    }

    @Test
    @DisplayName("Ao mesclar, cada campo fica com o meu valor quando presente e com o do outro quando falta")
    void deveMesclarCampoACampo() {
        Entities minhas = new Entities(1L, null, false, null, null, QUINZE_HORAS, null, null, null, false, false);
        Entities outras = new Entities(2L, 1L, false, LocalDate.of(2026, 10, 14), null, null, 3, null, "pix", true,
                false);

        Entities mescladas = minhas.or(outras);

        assertThat(mescladas).isEqualTo(new Entities(1L, 1L, false, LocalDate.of(2026, 10, 14), null, QUINZE_HORAS,
                3, null, "pix", true, false));
    }

    @Test
    @DisplayName("Ao mesclar, o profissional e o \"tanto faz\" andam juntos")
    void profissionalETantoFazDevemAndarJuntos() {
        Entities comPedro = new Entities(null, 2L, false, null, null, null, null, null, null, false, false);
        Entities tantoFaz = new Entities(null, null, true, null, null, null, null, null, null, false, false);

        assertThat(comPedro.or(tantoFaz)).isEqualTo(comPedro);
        assertThat(tantoFaz.or(comPedro)).isEqualTo(tantoFaz);
        assertThat(Entities.none().or(comPedro)).isEqualTo(comPedro);
    }

    @Test
    @DisplayName("Ao mesclar, a data e o problema de data andam juntos")
    void dataEProblemaDeDataDevemAndarJuntos() {
        Entities comData = new Entities(null, null, false, LocalDate.of(2026, 10, 20), null, null, null, null, null,
                false, false);
        Entities comProblema = new Entities(null, null, false, null, DateIssue.INVALID, null, null, null, null, false,
                false);

        assertThat(comData.or(comProblema).dateIssue()).isNull();
        assertThat(comProblema.or(comData).date()).isNull();
        assertThat(Entities.none().or(comProblema).dateIssue()).isEqualTo(DateIssue.INVALID);
    }

    @Test
    @DisplayName("Os períodos do dia cobrem manhã, tarde e noite sem buracos")
    void periodosDevemCobrirODia() {
        assertThat(PartOfDay.MORNING.contains(LocalTime.of(11, 59))).isTrue();
        assertThat(PartOfDay.MORNING.contains(LocalTime.NOON)).isFalse();
        assertThat(PartOfDay.AFTERNOON.contains(LocalTime.NOON)).isTrue();
        assertThat(PartOfDay.AFTERNOON.contains(LocalTime.of(18, 0))).isFalse();
        assertThat(PartOfDay.EVENING.contains(LocalTime.of(18, 0))).isTrue();
        assertThat(PartOfDay.EVENING.contains(LocalTime.of(23, 59))).isTrue();
    }

    private static Entities comServico(Long servico) {
        return new Entities(servico, null, false, null, null, null, null, null, null, false, false);
    }
}
