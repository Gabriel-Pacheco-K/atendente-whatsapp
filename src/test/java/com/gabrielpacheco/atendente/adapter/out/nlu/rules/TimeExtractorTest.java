package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference.Exact;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference.Period;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Extração de horários")
class TimeExtractorTest {

    private static final String[] SEIS_HORARIOS = {"10:30", "10:45", "11:00", "11:15", "11:30", "11:45"};

    private final TimeExtractor extrator = new TimeExtractor();

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "15h, 15:00",
            "15 h, 15:00",
            "15hs, 15:00",
            "15hrs, 15:00",
            "15 horas, 15:00",
            "15:30, 15:30",
            "15h30, 15:30",
            "9h, 09:00",
            "3 da tarde, 15:00",
            "as 3 da tarde, 15:00",
            "tres e meia da tarde, 15:30",
            "9 da manha, 09:00",
            "9 de manha, 09:00",
            "8 da noite, 20:00",
            "meio dia, 12:00",
            "meio-dia, 12:00",
            "meio dia e meia, 12:30",
            "as 4, 16:00",
            "as 4:30, 16:30",
            "as 7, 19:00",
            "as 8, 08:00",
            "as 10, 10:00",
            "as 10 e meia, 10:30",
            "as duas, 14:00",
            "amanha as 15h, 15:00",
            "as 14:30h, 14:30",
            "10 e meia, 10:30",
            "dez e meia, 10:30",
            "3 e meia, 15:30"
    })
    @DisplayName("Lê horários escritos de vários jeitos")
    void deveLerOHorario(String texto, LocalTime esperado) {
        assertThat(extrair(texto)).contains(new Exact(esperado));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"25h", "15:75", "as 20/10", "dia 20", "um corte", "tres"})
    @DisplayName("Ignora horas impossíveis e números que não são hora")
    void deveIgnorarOQueNaoEHora(String texto) {
        assertThat(extrair(texto)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "sabado de manha, MORNING",
            "pela manha, MORNING",
            "bem cedo, MORNING",
            "hoje a tarde, AFTERNOON",
            "de tarde, AFTERNOON",
            "depois do almoco, AFTERNOON",
            "a noite, EVENING",
            "de noite, EVENING",
            "no fim do dia, EVENING",
            "final do dia, EVENING"
    })
    @DisplayName("Reconhece períodos do dia")
    void deveReconhecerPeriodos(String texto, PartOfDay periodo) {
        assertThat(extrair(texto)).contains(new Period(periodo));
    }

    @ParameterizedTest(name = "\"{0}\" deixa \"{1}\" para a data")
    @CsvSource({
            "20/10 de tarde, 20/10",
            "20-10 de tarde, 20-10",
            "dia 20 de tarde, dia 20",
            "sexta dia 16 de manha, dia 16",
            "15h 20/10, 20/10"
    })
    @DisplayName("Não consome o número de uma data")
    void naoDeveConsumirONumeroDeUmaData(String texto, String data) {
        Spans spans = spans(texto);

        extrator.extract(spans, Barbearia.contexto(Expectation.NONE));

        assertThat(spans.remaining()).contains(data);
    }

    @Test
    @DisplayName("\"15h 20/10\" é 15:00, e o dia da data não vira minuto")
    void diaDaDataNaoDeveVirarMinuto() {
        assertThat(extrair("15h 20/10")).contains(new Exact(LocalTime.of(15, 0)));
        assertThat(extrair("20/10 de tarde")).contains(new Period(PartOfDay.AFTERNOON));
    }

    @Test
    @DisplayName("\"boa tarde\" é saudação, não período")
    void boaTardeNaoDeveSerPeriodo() {
        assertThat(extrair("boa tarde")).isEmpty();
        assertThat(extrair("boa noite")).isEmpty();
    }

    @Test
    @DisplayName("Com várias horas na frase, vale a última; hora exata vale mais que período")
    void deveFicarComAUltimaHoraExata() {
        assertThat(extrair("as 15h, nao, as 16h")).contains(new Exact(LocalTime.of(16, 0)));
        assertThat(extrair("de tarde, umas 15h")).contains(new Exact(LocalTime.of(15, 0)));
        assertThat(extrair("de manha ou a noite")).contains(new Period(PartOfDay.EVENING));
    }

    @Test
    @DisplayName("Na escolha de horário, \"15\" sozinho é 15:00 quando não é número de opção")
    void deveLerNumeroSozinhoComoHoraNaEscolhaDeHorario() {
        assertThat(extrator.extract(spans("15"), Barbearia.contexto(Expectation.OPTION, SEIS_HORARIOS)))
                .contains(new Exact(LocalTime.of(15, 0)));
        assertThat(extrator.extract(spans("4"), Barbearia.contexto(Expectation.OPTION, SEIS_HORARIOS))).isEmpty();
        assertThat(extrator.extract(spans("15"), Barbearia.contexto(Expectation.NONE))).isEmpty();
        assertThat(extrator.extract(spans("23"), Barbearia.contexto(Expectation.OPTION, SEIS_HORARIOS))).isEmpty();
    }

    private Optional<TimePreference> extrair(String texto) {
        Spans spans = spans(texto);
        PortugueseLexicon.GREETINGS.forEach(spans::consumeAll);
        return extrator.extract(spans, Barbearia.contexto(Expectation.NONE));
    }

    private static Spans spans(String texto) {
        return new Spans(TextNormalizer.normalize(texto).tokens());
    }
}
