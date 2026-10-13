package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Correção de digitação")
class SpellCorrectorTest {

    private final SpellCorrector corretor = new SpellCorrector(Barbearia.catalogo());

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "agnedar, agendar",
            "gaendar, agendar",
            "cancelra, cancelar",
            "amanah, amanha",
            "sabdo, sabado",
            "endereso, endereco",
            "barva, barba",
            "dsmarcar, desmarcar",
            "atendnte, atendente",
            "quinat, quinta",
            "joaoo, joao",
            "pedor, pedro",
            "sombrancelha, sobrancelha",
            "simm, sim"
    })
    @DisplayName("Corrige erros comuns de digitação")
    void deveCorrigir(String digitado, String esperado) {
        assertThat(corrigir(digitado)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} continua {0}")
    @ValueSource(strings = {"ou", "oi", "porte", "cara"})
    @DisplayName("Não troca palavras curtas nem palavras com outra inicial")
    void naoDeveCorrigirPalavrasDiferentes(String digitado) {
        assertThat(corrigir(digitado)).isEqualTo(digitado);
    }

    @Test
    @DisplayName("\"clara\" vira \"claro\": é por isso que o corretor fica desligado quando o nome é pedido")
    void deveTrocarClaraPorClaro() {
        assertThat(corrigir("clara")).isEqualTo("claro");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"barbearia", "barbeiro", "cabelos", "horarios", "agendamento", "quando", "mais", "pessoal"})
    @DisplayName("Palavras conhecidas do dia a dia nunca são corrigidas")
    void naoDeveCorrigirPalavrasConhecidas(String palavra) {
        assertThat(corrigir(palavra)).isEqualTo(palavra);
    }

    @Test
    @DisplayName("Números, palavras com dígito e palavras de ligação ficam como estão")
    void naoDeveCorrigirNumerosNemPalavrasDeLigacao() {
        assertThat(corrigir("1500 15hrs minha voce")).isEqualTo("1500 15hrs minha voce");
    }

    @Test
    @DisplayName("Marca quais palavras foram corrigidas")
    void deveMarcarAsPalavrasCorrigidas() {
        Correction correcao = corretor.correct(TextNormalizer.normalize("quero agnedar amanah").tokens());

        assertThat(correcao.text()).isEqualTo("quero agendar amanha");
        assertThat(correcao.correctedIndexes()).containsExactlyInAnyOrder(1, 2);
    }

    private String corrigir(String texto) {
        return corretor.correct(TextNormalizer.normalize(texto).tokens()).text();
    }
}
