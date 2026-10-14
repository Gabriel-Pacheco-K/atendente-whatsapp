package com.gabrielpacheco.atendente.adapter.out.nlu;

import com.gabrielpacheco.atendente.application.port.out.InterpretationUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.gabrielpacheco.atendente.fixtures.Entidades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;

import static ch.qos.logback.classic.Level.WARN;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.BARBA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Interpretação híbrida: regras primeiro, Claude só quando agrega")
class HybridInterpreterTest {

    private static final double LIMIAR = 0.75;
    private static final InterpretationContext LIVRE = Barbearia.contexto(Expectation.NONE);
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);

    private final MessageInterpreter regras = mock(MessageInterpreter.class);
    private final MessageInterpreter claude = mock(MessageInterpreter.class);
    private final HybridInterpreter hibrido = new HybridInterpreter(regras, Optional.of(claude), LIMIAR);

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Expectation.class, names = {"YES_NO", "NAME"})
    @DisplayName("Na confirmação e no nome, só as regras decidem, mesmo em dúvida")
    void confirmacaoENomeNuncaDevemChamarOClaude(Expectation expectativa) {
        InterpretationContext contexto = Barbearia.contexto(expectativa);
        Interpretation incerta = pelasRegras(Intent.UNKNOWN, 0);
        when(regras.interpret("hmm, talvez sim", contexto)).thenReturn(incerta);

        assertThat(hibrido.interpret("hmm, talvez sim", contexto)).isEqualTo(incerta);
        verify(claude, never()).interpret(anyString(), any());
    }

    @ParameterizedTest(name = "confiança {0}")
    @ValueSource(doubles = {0.75, 0.9, 1.0})
    @DisplayName("Com as regras confiantes, o Claude não é chamado")
    void regrasConfiantesNaoDevemChamarOClaude(double confianca) {
        Interpretation confiante = pelasRegras(Intent.BOOK, confianca);
        when(regras.interpret("quero agendar um corte", LIVRE)).thenReturn(confiante);

        assertThat(hibrido.interpret("quero agendar um corte", LIVRE)).isEqualTo(confiante);
        verify(claude, never()).interpret(anyString(), any());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"??", "ok", "2 3", "kk"})
    @DisplayName("Texto com menos de 3 letras não chama o Claude")
    void textoCurtoNaoDeveChamarOClaude(String texto) {
        when(regras.interpret(texto, LIVRE)).thenReturn(pelasRegras(Intent.UNKNOWN, 0));

        hibrido.interpret(texto, LIVRE);

        verify(claude, never()).interpret(anyString(), any());
    }

    @Test
    @DisplayName("Com as regras em dúvida, a intenção segura do Claude vale e as entidades se completam")
    void regrasIncertasDevemMesclarComOClaude() {
        Entities soAData = Entidades.com().data(SEXTA).montar();
        Entities soOServico = Entidades.com().servico(BARBA).montar();
        when(regras.interpret("dá pra fazer a barba sexta?", LIVRE))
                .thenReturn(new Interpretation(Intent.BOOK, 0.5, soAData, InterpreterKind.RULES));
        when(claude.interpret("dá pra fazer a barba sexta?", LIVRE))
                .thenReturn(new Interpretation(Intent.PRICES, 0.8, soOServico, InterpreterKind.CLAUDE));

        Interpretation resultado = hibrido.interpret("dá pra fazer a barba sexta?", LIVRE);

        assertThat(resultado).isEqualTo(new Interpretation(Intent.PRICES, 0.8,
                Entidades.com().servico(BARBA).data(SEXTA).montar(),
                InterpreterKind.CLAUDE));
    }

    @Test
    @DisplayName("Se o Claude também está em dúvida, a intenção das regras fica")
    void claudeIncertoDeveManterAIntencaoDasRegras() {
        when(regras.interpret("barba sexta talvez", LIVRE)).thenReturn(pelasRegras(Intent.BOOK, 0.5));
        when(claude.interpret("barba sexta talvez", LIVRE))
                .thenReturn(new Interpretation(Intent.PRICES, 0.55, Entities.none(), InterpreterKind.CLAUDE));

        Interpretation resultado = hibrido.interpret("barba sexta talvez", LIVRE);

        assertThat(resultado.intent()).isEqualTo(Intent.BOOK);
        assertThat(resultado.interpretedBy()).isEqualTo(InterpreterKind.CLAUDE);
    }

    @Test
    @DisplayName("\"Não sei\" do Claude não substitui a intenção das regras, por mais confiante que esteja")
    void unknownDoClaudeNaoDeveSubstituirAsRegras() {
        when(regras.interpret("kkkkk xpto", LIVRE)).thenReturn(pelasRegras(Intent.GREETING, 0.35));
        when(claude.interpret("kkkkk xpto", LIVRE))
                .thenReturn(new Interpretation(Intent.UNKNOWN, 0.95, Entities.none(), InterpreterKind.CLAUDE));

        assertThat(hibrido.interpret("kkkkk xpto", LIVRE).intent()).isEqualTo(Intent.GREETING);
    }

    @Test
    @DisplayName("Se o Claude falhar, vale o que as regras entenderam, marcado como RULES_AFTER_FAILURE, com WARN")
    void falhaDoClaudeDeveVoltarParaAsRegras() {
        when(regras.interpret("quero ver os horarios", LIVRE)).thenReturn(pelasRegras(Intent.BOOK, 0.5));
        when(claude.interpret("quero ver os horarios", LIVRE))
                .thenThrow(new InterpretationUnavailableException("http_529"));

        try (CapturaDeLogs logs = CapturaDeLogs.de(HybridInterpreter.class)) {
            Interpretation resultado = hibrido.interpret("quero ver os horarios", LIVRE);

            assertThat(resultado).isEqualTo(new Interpretation(Intent.BOOK, 0.5, Entities.none(),
                    InterpreterKind.RULES_AFTER_FAILURE));
            assertThat(logs.mensagens(WARN))
                    .containsExactly("Claude indisponível (http_529); usando o motor de regras");
        }
    }

    @Test
    @DisplayName("Sem Claude configurado, só as regras respondem")
    void semClaudeDeveUsarSoAsRegras() {
        HybridInterpreter soRegras = new HybridInterpreter(regras, Optional.empty(), LIMIAR);
        Interpretation incerta = pelasRegras(Intent.UNKNOWN, 0);
        when(regras.interpret("kkkkk xpto", LIVRE)).thenReturn(incerta);

        assertThat(soRegras.interpret("kkkkk xpto", LIVRE)).isEqualTo(incerta);
    }

    private static Interpretation pelasRegras(Intent intent, double confianca) {
        return new Interpretation(intent, confianca, Entities.none(), InterpreterKind.RULES);
    }
}
