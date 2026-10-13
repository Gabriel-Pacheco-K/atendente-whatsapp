package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.domain.reply.PtBr;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.time.LocalDate;

/**
 * As frases de nlu/frases.csv documentam o que o motor de regras entende. Todo erro encontrado em produção vira uma
 * linha nova aqui antes de ser corrigido.
 */
@DisplayName("Corpus de frases em português")
class IntentCorpusTest {

    private final RuleBasedInterpreter interpretador = new RuleBasedInterpreter();

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvFileSource(resources = "/nlu/frases.csv", delimiter = ';', numLinesToSkip = 1, encoding = "UTF-8")
    @DisplayName("Entende a intenção e as entidades de cada frase")
    void deveEntenderAFrase(String frase, Intent intencao, String servico, String profissional, LocalDate data,
                            String hora) {
        Interpretation resultado = interpretador.interpret(frase, Barbearia.contexto(Expectation.NONE));
        Entities entidades = resultado.entities();

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(resultado.intent()).as("intenção").isEqualTo(intencao);
            soft.assertThat(nomeDoServico(entidades)).as("serviço").isEqualTo(servico);
            soft.assertThat(nomeDoProfissional(entidades)).as("profissional").isEqualTo(profissional);
            soft.assertThat(entidades.date()).as("data").isEqualTo(data);
            soft.assertThat(horaExata(entidades)).as("hora").isEqualTo(hora);
        });
    }

    private static String nomeDoServico(Entities entidades) {
        return entidades.serviceId() == null ? null
                : Barbearia.catalogo().service(entidades.serviceId()).orElseThrow().name();
    }

    private static String nomeDoProfissional(Entities entidades) {
        return entidades.professionalId() == null ? null
                : Barbearia.catalogo().professional(entidades.professionalId()).orElseThrow().name();
    }

    /** A coluna de hora registra só horários exatos; períodos como "de manhã" ficam de fora. */
    private static String horaExata(Entities entidades) {
        return entidades.time() instanceof TimePreference.Exact exact ? PtBr.time(exact.time()) : null;
    }
}
