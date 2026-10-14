package com.gabrielpacheco.atendente.domain.booking;

import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.fixtures.Entidades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Rascunho do agendamento")
class BookingDraftTest {

    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);
    private static final TimePreference AS_15 = new TimePreference.Exact(LocalTime.of(15, 0));
    private static final SlotOption QUARTA_15_COM_JOAO = new SlotOption(QUARTA.atTime(15, 0), 1, "João Batista");

    private static final BookingDraft ESCOLHIDO =
            new BookingDraft(1L, 1L, false, QUARTA, AS_15, QUARTA_15_COM_JOAO);

    @Test
    @DisplayName("O merge só sobrescreve o que a mensagem trouxe")
    void mergeDeveSobrescreverSoOInformado() {
        BookingDraft rascunho = new BookingDraft(1L, 2L, false, null, AS_15, null);

        BookingDraft mesclado = rascunho.merge(Entidades.com().data(SEXTA).montar());

        assertThat(mesclado).isEqualTo(new BookingDraft(1L, 2L, false, SEXTA, AS_15, null));
    }

    @Test
    @DisplayName("Trocar serviço, profissional, data ou hora desfaz o horário escolhido")
    void trocaDeveDesfazerOHorarioEscolhido() {
        assertThat(ESCOLHIDO.merge(Entidades.com().servico(2L).montar()).chosen()).isNull();
        assertThat(ESCOLHIDO.merge(Entidades.com().profissional(2L).montar()).chosen()).isNull();
        assertThat(ESCOLHIDO.merge(Entidades.com().tantoFaz().montar()).chosen()).isNull();
        assertThat(ESCOLHIDO.merge(Entidades.com().data(SEXTA).montar()).chosen()).isNull();
        assertThat(ESCOLHIDO.merge(Entidades.com().hora(new TimePreference.Period(PartOfDay.MORNING)).montar())
                .chosen()).isNull();
    }

    @Test
    @DisplayName("Repetir o que já estava no rascunho mantém o horário escolhido")
    void mesmoValorDeveManterOHorarioEscolhido() {
        assertThat(ESCOLHIDO.merge(Entidades.com().servico(1L).profissional(1L).data(QUARTA).hora(AS_15).montar())).isEqualTo(ESCOLHIDO);
    }

    @Test
    @DisplayName("Trocar a data mantém a hora pedida")
    void trocarADataDeveManterAHora() {
        assertThat(ESCOLHIDO.merge(Entidades.com().data(SEXTA).montar()).time()).isEqualTo(AS_15);
    }

    @Test
    @DisplayName("Escolher um profissional desliga o \"tanto faz\", e o \"tanto faz\" apaga o profissional")
    void profissionalETantoFazDevemSeExcluir() {
        BookingDraft tantoFaz = BookingDraft.EMPTY.merge(Entidades.com().tantoFaz().montar());
        assertThat(tantoFaz.anyProfessional()).isTrue();

        BookingDraft comPedro = tantoFaz.merge(Entidades.com().profissional(2L).montar());
        assertThat(comPedro.anyProfessional()).isFalse();
        assertThat(comPedro.professionalId()).isEqualTo(2L);

        BookingDraft deNovoTantoFaz = comPedro.merge(Entidades.com().tantoFaz().montar());
        assertThat(deNovoTantoFaz.anyProfessional()).isTrue();
        assertThat(deNovoTantoFaz.professionalId()).isNull();
    }

    @Test
    @DisplayName("Com \"tanto faz\", o horário escolhido guarda o profissional e o rascunho continua sem ele")
    void escolhaComTantoFazNaoDeveFixarOProfissional() {
        BookingDraft tantoFaz = new BookingDraft(1L, null, true, QUARTA, null, null);

        BookingDraft escolhido = tantoFaz.withChosen(QUARTA_15_COM_JOAO);

        assertThat(escolhido.chosen()).isEqualTo(QUARTA_15_COM_JOAO);
        assertThat(escolhido.professionalId()).isNull();
        assertThat(escolhido.withoutDateAndTime()).isEqualTo(new BookingDraft(1L, null, true, null, null, null));
    }

    @Test
    @DisplayName("Escolher um horário fixa a data e o profissional do horário")
    void escolhaDeveFixarDataEProfissional() {
        BookingDraft escolhido = new BookingDraft(1L, 1L, false, SEXTA, null, null).withChosen(QUARTA_15_COM_JOAO);

        assertThat(escolhido.date()).isEqualTo(QUARTA);
        assertThat(escolhido.professionalId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("withDate troca o dia, desfaz o horário escolhido e mantém a hora")
    void withDateDeveDesfazerOHorarioEscolhido() {
        assertThat(ESCOLHIDO.withDate(SEXTA)).isEqualTo(new BookingDraft(1L, 1L, false, SEXTA, AS_15, null));
    }

    @Test
    @DisplayName("Recusa estados impossíveis")
    void deveRecusarEstadosImpossiveis() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BookingDraft(1L, 1L, true, null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BookingDraft(1L, 1L, false, SEXTA, null, QUARTA_15_COM_JOAO));
    }
}
