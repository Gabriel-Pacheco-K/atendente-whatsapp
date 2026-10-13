package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.adapter.out.nlu.rules.IntentClassifier.Classification;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Set;

import static com.gabrielpacheco.atendente.domain.nlu.Intent.AFFIRM;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.BOOK;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.DENY;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.THANKS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Classificação de intenções")
class IntentClassifierTest {

    private static final Entities COM_DATA =
            new Entities(null, null, false, LocalDate.of(2026, 10, 16), null, null, null, null, null, false, false);

    private final IntentClassifier classificador = new IntentClassifier();

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "nao quero agendar, DENY",
            "nao quero mais marcar, DENY",
            "nao vou marcar, DENY",
            "nem marcar, DENY",
            "nao sei se da para agendar, BOOK"
    })
    @DisplayName("Negação até duas palavras antes de uma pista de agendamento vira recusa")
    void deveTratarANegacao(String texto, Intent esperada) {
        assertThat(classificar(texto, Expectation.NONE).intent()).isEqualTo(esperada);
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "oi quero agendar, BOOK",
            "quero cancelar meu horario, CANCEL_BOOKING",
            "oi quero falar com uma pessoa, HUMAN",
            "boa tarde, GREETING",
            "pode me passar o endereco, ADDRESS"
    })
    @DisplayName("Vence a maior pontuação e, no empate, a intenção mais importante")
    void deveEscolherPelaPontuacaoEPelaPrioridade(String texto, Intent esperada) {
        assertThat(classificar(texto, Expectation.NONE).intent()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("\"sim\" e \"não\" na mesma mensagem dão UNKNOWN e marcam o conflito")
    void simENaoJuntosDevemDarDesconhecido() {
        Classification resultado = classificar("sim nao", Expectation.YES_NO);

        assertThat(resultado.intent()).isEqualTo(UNKNOWN);
        assertThat(resultado.yesNoConflict()).isTrue();
        assertThat(resultado.confidence()).isZero();
    }

    @Test
    @DisplayName("Na confirmação, sim e não valem o dobro")
    void simENaoDevemValerODobroNaConfirmacao() {
        assertThat(classificar("ok valeu", Expectation.NONE).intent()).isEqualTo(THANKS);
        assertThat(classificar("ok valeu", Expectation.YES_NO).intent()).isEqualTo(AFFIRM);
    }

    @Test
    @DisplayName("Só com entidades e sem pista, a intenção é agendar com confiança 0,8")
    void soEntidadesDevemDarAgendamento() {
        Classification resultado = classificador.classify(semCorrecao("barba sexta"), Expectation.NONE, COM_DATA, 0);

        assertThat(resultado.intent()).isEqualTo(BOOK);
        assertThat(resultado.confidence()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("Sem pista e sem entidades, não entendeu")
    void semNadaDeveSerDesconhecido() {
        Classification resultado = classificar("k xpto", Expectation.NONE);

        assertThat(resultado.intent()).isEqualTo(UNKNOWN);
        assertThat(resultado.confidence()).isZero();
    }

    @Test
    @DisplayName("Confiança: pontuação sobre 4, com desconto de 0,7 quando a folga para a segunda é menor que 2")
    void deveCalcularAConfianca() {
        assertThat(classificar("quero agendar", Expectation.NONE).confidence()).isCloseTo(0.75, within(1e-9));
        assertThat(classificar("tem horario para agendar", Expectation.NONE).confidence()).isEqualTo(1.0);
        assertThat(classificar("oi quero agendar", Expectation.NONE).confidence()).isCloseTo(0.525, within(1e-9));
    }

    @Test
    @DisplayName("Quando a pista vencedora veio de uma palavra corrigida, a confiança cai 10%")
    void palavraCorrigidaDeveReduzirAConfianca() {
        Correction corrigida = new Correction(TextNormalizer.normalize("tem horario para agendar").tokens(), Set.of(3));

        Classification resultado = classificador.classify(corrigida, Expectation.NONE, Entities.none(), 0);

        assertThat(resultado.confidence()).isCloseTo(0.9, within(1e-9));
    }

    @Test
    @DisplayName("Na confirmação, um sim ou um não sozinho tem confiança total")
    void simSozinhoNaConfirmacaoDeveTerConfiancaTotal() {
        assertThat(classificar("ok", Expectation.YES_NO).confidence()).isEqualTo(1.0);
        assertThat(classificar("nao", Expectation.YES_NO).intent()).isEqualTo(DENY);
    }

    @Test
    @DisplayName("Palavras-chave de pergunta frequente valem 3 cada")
    void faqDevePontuarPorPalavraChave() {
        Classification resultado = classificador.classify(semCorrecao("pode pagar no cartao"), Expectation.NONE,
                Entities.none(), 2);

        assertThat(resultado.intent()).isEqualTo(Intent.FAQ);
        assertThat(resultado.confidence()).isEqualTo(1.0);
    }

    private Classification classificar(String texto, Expectation expectativa) {
        return classificador.classify(semCorrecao(texto), expectativa, Entities.none(), 0);
    }

    private static Correction semCorrecao(String texto) {
        return Correction.unchanged(TextNormalizer.normalize(texto).tokens());
    }
}
