package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.adapter.out.nlu.rules.DateExtractor.DateMention;
import com.gabrielpacheco.atendente.domain.nlu.DateIssue;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Extração de datas (hoje é terça, 13/10/2026)")
class DateExtractorTest {

    private final DateExtractor extrator = new DateExtractor();

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "hoje, 2026-10-13",
            "amanha, 2026-10-14",
            "depois de amanha, 2026-10-15",
            "sexta, 2026-10-16",
            "sexta-feira, 2026-10-16",
            "na terca, 2026-10-13",
            "terca que vem, 2026-10-20",
            "proxima terca, 2026-10-20",
            "sexta da semana que vem, 2026-10-23",
            "sab, 2026-10-17",
            "segunda, 2026-10-19",
            "dia 20, 2026-10-20",
            "dia 13, 2026-10-13",
            "dia 5, 2026-11-05",
            "20/10, 2026-10-20",
            "20-10, 2026-10-20",
            "20/10/2026, 2026-10-20",
            "20/10/26, 2026-10-20",
            "20 de outubro, 2026-10-20",
            "20 de out, 2026-10-20",
            "'nao sexta, sabado', 2026-10-17"
    })
    @DisplayName("Resolve datas relativas e absolutas a partir de hoje")
    void deveResolverADataAPartirDeHoje(String texto, LocalDate esperada) {
        assertThat(extrair(texto, Expectation.NONE)).contains(DateMention.of(esperada));
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "31/02, INVALID",
            "32/10, INVALID",
            "10/13, INVALID",
            "dia 31 de novembro, INVALID",
            "10/10, PAST",
            "06/10, PAST",
            "05/01, TOO_FAR",
            "15/11, TOO_FAR",
            "10/10/2026, PAST"
    })
    @DisplayName("Aponta data inexistente, passada ou além do limite de 30 dias")
    void deveApontarProblemasNaData(String texto, DateIssue problema) {
        assertThat(extrair(texto, Expectation.NONE)).contains(DateMention.problem(problema));
    }

    @Test
    @DisplayName("Sem ano, uma data que passou há mais de uma semana é do ano seguinte")
    void deveLevarDataAntigaParaOAnoSeguinte() {
        Optional<DateMention> data = extrator.extract(spans("05/10"), comLimiteDeDias(400));

        assertThat(data).contains(DateMention.of(LocalDate.of(2027, 10, 5)));
    }

    @Test
    @DisplayName("O último dia do limite ainda vale")
    void deveAceitarOUltimoDiaDoLimite() {
        assertThat(extrair("12/11", Expectation.NONE)).contains(DateMention.of(LocalDate.of(2026, 11, 12)));
    }

    @Test
    @DisplayName("Quando o atendente pediu o dia, \"20\" sozinho é o dia 20")
    void deveLerNumeroSozinhoComoDiaQuandoADataFoiPedida() {
        assertThat(extrair("20", Expectation.DATE)).contains(DateMention.of(LocalDate.of(2026, 10, 20)));
        assertThat(extrair("20", Expectation.NONE)).isEmpty();
        assertThat(extrair("45", Expectation.DATE)).isEmpty();
    }

    @Test
    @DisplayName("Hora não vira dia: \"15h\" não é dia 15")
    void naoDeveLerHoraComoDia() {
        assertThat(extrair("15h", Expectation.DATE)).isEmpty();
    }

    @Test
    @DisplayName("Consome o trecho da data para os próximos extratores")
    void deveConsumirOTrecho() {
        Spans spans = spans("corte dia 20 com o joao");

        extrator.extract(spans, Barbearia.contexto(Expectation.NONE));

        assertThat(spans.remaining()).isEqualTo("corte com o joao");
    }

    private Optional<DateMention> extrair(String texto, Expectation expectativa) {
        return extrator.extract(spans(texto), Barbearia.contexto(expectativa));
    }

    private static Spans spans(String texto) {
        return new Spans(TextNormalizer.normalize(texto).tokens());
    }

    private static InterpretationContext comLimiteDeDias(int dias) {
        InterpretationContext contexto = Barbearia.contexto(Expectation.NONE);
        return new InterpretationContext(contexto.today(), contexto.now(), contexto.catalog(), contexto.faqs(),
                contexto.expectation(), contexto.offeredOptions(), dias);
    }
}
