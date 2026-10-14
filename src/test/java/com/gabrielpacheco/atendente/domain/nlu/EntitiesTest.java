package com.gabrielpacheco.atendente.domain.nlu;

import com.gabrielpacheco.atendente.fixtures.Entidades;
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
        assertThat(Entidades.com().servico(1L).montar().hasBookingData()).isTrue();
        assertThat(Entidades.com().tantoFaz().montar().hasBookingData()).isTrue();
        assertThat(Entidades.com().problemaNaData(DateIssue.PAST).montar().hasBookingData()).isTrue();
        assertThat(Entidades.com().opcao(2).nome("Ana").faq("pagamento").remarcar().simENao().montar()
                .hasBookingData()).isFalse();
    }

    @Test
    @DisplayName("Ao mesclar, cada campo fica com o meu valor quando presente e com o do outro quando falta")
    void deveMesclarCampoACampo() {
        Entities minhas = Entidades.com().servico(1L).hora(QUINZE_HORAS).montar();
        Entities outras = Entidades.com().servico(2L).profissional(1L).data(LocalDate.of(2026, 10, 14)).opcao(3)
                .faq("pix").remarcar().montar();

        Entities mescladas = minhas.or(outras);

        assertThat(mescladas).isEqualTo(Entidades.com().servico(1L).profissional(1L).data(LocalDate.of(2026, 10, 14))
                .hora(QUINZE_HORAS).opcao(3).faq("pix").remarcar().montar());
    }

    @Test
    @DisplayName("Ao mesclar, o profissional e o \"tanto faz\" andam juntos")
    void profissionalETantoFazDevemAndarJuntos() {
        Entities comPedro = Entidades.com().profissional(2L).montar();
        Entities tantoFaz = Entidades.com().tantoFaz().montar();

        assertThat(comPedro.or(tantoFaz)).isEqualTo(comPedro);
        assertThat(tantoFaz.or(comPedro)).isEqualTo(tantoFaz);
        assertThat(Entities.none().or(comPedro)).isEqualTo(comPedro);
    }

    @Test
    @DisplayName("Ao mesclar, a data e o problema de data andam juntos")
    void dataEProblemaDeDataDevemAndarJuntos() {
        Entities comData = Entidades.com().data(LocalDate.of(2026, 10, 20)).montar();
        Entities comProblema = Entidades.com().problemaNaData(DateIssue.INVALID).montar();

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
}
