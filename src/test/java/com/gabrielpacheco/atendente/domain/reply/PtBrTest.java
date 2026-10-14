package com.gabrielpacheco.atendente.domain.reply;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Formatação em português")
class PtBrTest {

    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            45       | R$ 45,00
            35.5     | R$ 35,50
            0        | R$ 0,00
            1200     | R$ 1.200,00
            1234567.891 | R$ 1.234.567,89
            """)
    @DisplayName("Formata dinheiro com vírgula nos centavos e ponto no milhar")
    void deveFormatarDinheiro(BigDecimal valor, String esperado) {
        assertThat(PtBr.money(valor)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("O espaço depois de R$ é o espaço comum, nunca o não separável")
    void deveUsarEspacoComum() {
        assertThat(PtBr.money(new BigDecimal("45.00")))
                .contains("R$ 45")
                .doesNotContain(" ");
    }

    @Test
    @DisplayName("Formata hora com dois dígitos")
    void deveFormatarHora() {
        assertThat(PtBr.time(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(PtBr.time(LocalTime.of(15, 0))).isEqualTo("15:00");
    }

    @Test
    @DisplayName("Data longa com o dia da semana; sábado e domingo sem \"-feira\"")
    void deveFormatarDataLonga() {
        assertThat(PtBr.longDate(QUARTA)).isEqualTo("quarta-feira, 14/10");
        assertThat(PtBr.longDate(SABADO)).isEqualTo("sábado, 17/10");
        assertThat(PtBr.longDate(LocalDate.of(2026, 10, 19))).isEqualTo("segunda-feira, 19/10");
    }

    @Test
    @DisplayName("\"na\" antes de dia útil e \"no\" antes de sábado e domingo")
    void deveConcordarAPreposicao() {
        assertThat(PtBr.onDate(QUARTA)).isEqualTo("na quarta-feira, 14/10");
        assertThat(PtBr.onDate(SABADO)).isEqualTo("no sábado, 17/10");
        assertThat(PtBr.onDate(DOMINGO)).isEqualTo("no domingo, 18/10");
    }

    @Test
    @DisplayName("Dia da semana curto com a preposição certa, e data com hora para rótulos")
    void deveFormatarDiasCurtos() {
        assertThat(PtBr.onWeekday(DayOfWeek.WEDNESDAY)).isEqualTo("na quarta");
        assertThat(PtBr.onWeekday(DayOfWeek.SATURDAY)).isEqualTo("no sábado");
        assertThat(PtBr.dayMonth(LocalDate.of(2026, 11, 3))).isEqualTo("03/11");
        assertThat(PtBr.shortDateTime(QUARTA.atTime(15, 0))).isEqualTo("qua 14/10 15:00");
        assertThat(PtBr.shortDateTime(SABADO.atTime(8, 30))).isEqualTo("sáb 17/10 08:30");
    }

    @Test
    @DisplayName("Dia lotado concorda em gênero")
    void deveConcordarODiaLotado() {
        assertThat(PtBr.fullDay(QUARTA)).isEqualTo("A quarta-feira, 14/10, já está lotada");
        assertThat(PtBr.fullDay(SABADO)).isEqualTo("O sábado, 17/10, já está lotado");
    }

    @Test
    @DisplayName("Plural do dia da semana com o artigo certo, com ou sem maiúscula")
    void deveFormatarOPluralDoDia() {
        assertThat(PtBr.weekdayPlural(DayOfWeek.MONDAY, true)).isEqualTo("Às segundas");
        assertThat(PtBr.weekdayPlural(DayOfWeek.MONDAY, false)).isEqualTo("às segundas");
        assertThat(PtBr.weekdayPlural(DayOfWeek.SATURDAY, true)).isEqualTo("Aos sábados");
        assertThat(PtBr.weekdayPlural(DayOfWeek.SUNDAY, false)).isEqualTo("aos domingos");
    }

    @Test
    @DisplayName("Junta listas com vírgula e \"e\" no último item")
    void deveJuntarComE() {
        assertThat(PtBr.joinWithAnd(List.of())).isEmpty();
        assertThat(PtBr.joinWithAnd(List.of("segunda"))).isEqualTo("segunda");
        assertThat(PtBr.joinWithAnd(List.of("segunda", "domingo"))).isEqualTo("segunda e domingo");
        assertThat(PtBr.joinWithAnd(List.of("a", "b", "c"))).isEqualTo("a, b e c");
    }

    @Test
    @DisplayName("Nomes curtos e completos dos dias e maiúscula inicial")
    void deveNomearOsDias() {
        assertThat(PtBr.dayName(DayOfWeek.TUESDAY)).isEqualTo("terça");
        assertThat(PtBr.weekdayName(DayOfWeek.TUESDAY)).isEqualTo("terça-feira");
        assertThat(PtBr.weekdayName(DayOfWeek.SUNDAY)).isEqualTo("domingo");
        assertThat(PtBr.capitalize("sábado")).isEqualTo("Sábado");
        assertThat(PtBr.capitalize("")).isEmpty();
    }
}
