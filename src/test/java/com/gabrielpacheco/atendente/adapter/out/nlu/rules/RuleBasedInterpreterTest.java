package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static com.gabrielpacheco.atendente.domain.nlu.Intent.AFFIRM;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.BOOK;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.FAQ;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.UNKNOWN;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Motor de regras completo")
class RuleBasedInterpreterTest {

    private static final String[] NOME_SUGERIDO = {"Sim, no nome de Gabriel", "Outro nome"};

    private final RuleBasedInterpreter interpretador = new RuleBasedInterpreter();

    @Test
    @DisplayName("Entende a frase da demonstração, com erros de digitação, numa mensagem só")
    void deveEntenderAFraseDaDemonstracao() {
        Interpretation resultado = interpretador.interpret(
                "Oiii, qro agnedar um corte p/ amanhã ás 3 da tarde c/ o Joao!!", Barbearia.contexto(Expectation.NONE));

        assertThat(resultado.intent()).isEqualTo(BOOK);
        assertThat(resultado.confidence()).isCloseTo(0.9, within(1e-9));
        assertThat(resultado.interpretedBy()).isEqualTo(InterpreterKind.RULES);
        assertThat(resultado.entities()).isEqualTo(new Entities(CORTE.id(), JOAO.id(), false,
                LocalDate.of(2026, 10, 14), null, new TimePreference.Exact(LocalTime.of(15, 0)), null, null, null,
                false, false));
    }

    @Test
    @DisplayName("Na confirmação, \"sim, mas às 16h\" é um sim com correção de horário")
    void simComHorarioDeveTrazerACorrecao() {
        Interpretation resultado = interpretador.interpret("sim, mas às 16h", Barbearia.contexto(Expectation.YES_NO));

        assertThat(resultado.intent()).isEqualTo(AFFIRM);
        assertThat(resultado.confidence()).isEqualTo(1.0);
        assertThat(resultado.entities().time()).isEqualTo(new TimePreference.Exact(LocalTime.of(16, 0)));
    }

    @Test
    @DisplayName("\"sim\" e \"não\" juntos marcam o conflito nas entidades")
    void deveMarcarConflitoDeSimENao() {
        Interpretation resultado = interpretador.interpret("sim... não, peraí", Barbearia.contexto(Expectation.YES_NO));

        assertThat(resultado.intent()).isEqualTo(UNKNOWN);
        assertThat(resultado.entities().yesNoConflict()).isTrue();
    }

    @Test
    @DisplayName("Na confirmação, uma pergunta frequente continua sendo reconhecida")
    void deveReconhecerPerguntaFrequenteNaConfirmacao() {
        Interpretation resultado = interpretador.interpret("aceita pix?", Barbearia.contexto(Expectation.YES_NO));

        assertThat(resultado.intent()).isEqualTo(FAQ);
        assertThat(resultado.entities().faqId()).isEqualTo("pagamento");
        assertThat(resultado.entities().yesNoConflict()).isFalse();
    }

    @Test
    @DisplayName("Quando o atendente pediu o dia, \"20\" é o dia 20, com confiança 0,95")
    void deveLerODiaPedido() {
        Interpretation resultado = interpretador.interpret("20", Barbearia.contexto(Expectation.DATE));

        assertThat(resultado.entities().date()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(resultado.confidence()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Na escolha de opção, \"2\" é a opção 2, com confiança total")
    void deveLerAOpcaoEscolhida() {
        Interpretation resultado = interpretador.interpret("2",
                Barbearia.contexto(Expectation.OPTION, "10:30 com Pedro", "10:45 com Pedro"));

        assertThat(resultado.entities().option()).isEqualTo(2);
        assertThat(resultado.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("\"remarcar\" pede a dica de remarcação")
    void devePerceberPedidoDeRemarcacao() {
        Interpretation resultado = interpretador.interpret("quero remarcar", Barbearia.contexto(Expectation.NONE));

        assertThat(resultado.entities().rescheduleRequested()).isTrue();
    }

    @Test
    @DisplayName("Mensagem vazia não é entendida")
    void mensagemVaziaNaoDeveSerEntendida() {
        Interpretation resultado = interpretador.interpret("   ", Barbearia.contexto(Expectation.NONE));

        assertThat(resultado.intent()).isEqualTo(UNKNOWN);
        assertThat(resultado.confidence()).isZero();
        assertThat(resultado.entities()).isEqualTo(Entities.none());
    }

    @Nested
    @DisplayName("Quando o atendente pede o nome")
    class PedindoONome {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({
                "joão, João",
                "pedro silva, Pedro Silva",
                "clara, Clara",
                "meu nome completo é ana souza, Ana Souza",
                "gabriel pacheco, Gabriel Pacheco"
        })
        @DisplayName("O nome vence o corretor e o catálogo: \"Pedro Silva\" é o cliente, não o profissional")
        void deveFicarComONome(String texto, String nome) {
            Interpretation resultado = interpretador.interpret(texto, Barbearia.contexto(Expectation.NAME));

            assertThat(resultado.entities().personName()).isEqualTo(nome);
            assertThat(resultado.entities().hasBookingData()).isFalse();
            assertThat(resultado.confidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("\"na verdade sexta\" é correção da data, não nome")
        void dataDeveSerCorrecao() {
            Interpretation resultado = interpretador.interpret("na verdade sexta", Barbearia.contexto(Expectation.NAME));

            assertThat(resultado.entities().date()).isEqualTo(LocalDate.of(2026, 10, 16));
            assertThat(resultado.entities().personName()).isNull();
        }

        @Test
        @DisplayName("\"com o pedro\" é troca de profissional, não nome")
        void profissionalComPistaDeveSerCorrecao() {
            Interpretation resultado = interpretador.interpret("prefiro com o pedro",
                    Barbearia.contexto(Expectation.NAME));

            assertThat(resultado.entities().professionalId()).isEqualTo(PEDRO.id());
            assertThat(resultado.entities().personName()).isNull();
        }

        @ParameterizedTest(name = "\"{0}\" → opção {1}")
        @CsvSource({"1, 1", "2, 2", "Outro nome, 2", "outro nom, 2"})
        @DisplayName("Com nome sugerido, \"1\" aceita, \"2\" e \"Outro nome\" pedem outro, sem salvar \"Outro Nome\"")
        void deveEntenderAsOpcoesDoNomeSugerido(String texto, int opcao) {
            assertThat(comNomeSugerido(texto))
                    .extracting(Entities::option, Entities::personName)
                    .containsExactly(opcao, null);
        }

        @Test
        @DisplayName("Com nome sugerido, outro nome digitado é o nome")
        void nomeDigitadoDeveValerMesmoComSugestao() {
            assertThat(comNomeSugerido("Maria"))
                    .extracting(Entities::option, Entities::personName)
                    .containsExactly(null, "Maria");
        }

        @Test
        @DisplayName("\"sim\" aceita o nome sugerido e não vira nome")
        void simDeveAceitarASugestao() {
            Interpretation resultado = interpretador.interpret("sim", Barbearia.contexto(Expectation.NAME, NOME_SUGERIDO));

            assertThat(resultado.intent()).isEqualTo(AFFIRM);
            assertThat(resultado.entities().personName()).isNull();
        }

        private Entities comNomeSugerido(String texto) {
            return interpretador.interpret(texto, Barbearia.contexto(Expectation.NAME, NOME_SUGERIDO)).entities();
        }
    }
}
