package com.gabrielpacheco.atendente.domain.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Distância de edição OSA")
class EditDistanceTest {

    @ParameterizedTest(name = "{0} ↔ {1} = {2}")
    @CsvSource({
            "agnedar, agendar, 1",
            "cancelra, cancelar, 1",
            "sabdo, sabado, 1",
            "barva, barba, 1",
            "corte, corte, 0",
            "dsmarcar, desmarcar, 1",
            "kitten, sitting, 3"
    })
    @DisplayName("Conta inserção, remoção, troca e transposição de vizinhas como uma edição cada")
    void deveCalcularADistancia(String a, String b, int esperada) {
        assertThat(EditDistance.osa(a, b, 5)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Transposição de letras vizinhas custa 1, e não 2")
    void transposicaoDeveCustarUm() {
        assertThat(EditDistance.osa("ab", "ba", 3)).isEqualTo(1);
        assertThat(EditDistance.osa("quinat", "quinta", 3)).isEqualTo(1);
    }

    @Test
    @DisplayName("A distância é simétrica")
    void deveSerSimetrica() {
        assertThat(EditDistance.osa("endereso", "endereco", 3)).isEqualTo(EditDistance.osa("endereco", "endereso", 3));
        assertThat(EditDistance.osa("atendnte", "atendente", 3)).isEqualTo(EditDistance.osa("atendente", "atendnte", 3));
    }

    @Test
    @DisplayName("Com texto vazio, a distância é o tamanho do outro")
    void deveTratarTextoVazio() {
        assertThat(EditDistance.osa("", "", 2)).isZero();
        assertThat(EditDistance.osa("", "abc", 5)).isEqualTo(3);
        assertThat(EditDistance.osa("abcd", "", 5)).isEqualTo(4);
    }

    @Test
    @DisplayName("Desiste cedo e devolve max + 1 quando a distância passa do limite")
    void deveDevolverMaxMaisUmAoPassarDoLimite() {
        assertThat(EditDistance.osa("abcdef", "uvwxyz", 2)).isEqualTo(3);
        assertThat(EditDistance.osa("ab", "abcdefgh", 2)).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0} letras → {1}")
    @CsvSource({"1, 0", "3, 0", "4, 1", "6, 1", "7, 2", "12, 2"})
    @DisplayName("Tolera nenhum erro até 3 letras, 1 até 6 e 2 a partir de 7")
    void deveTolerarConformeOTamanho(int tamanho, int tolerancia) {
        assertThat(EditDistance.typoTolerance(tamanho)).isEqualTo(tolerancia);
    }

    @Test
    @DisplayName("A primeira letra precisa bater, a não ser que as duas primeiras tenham sido trocadas")
    void devePedirAMesmaPrimeiraLetra() {
        assertThat(EditDistance.matchesWithTypos("gaendar", "agendar")).isTrue();
        assertThat(EditDistance.matchesWithTypos("porte", "corte")).isFalse();
        assertThat(EditDistance.matchesWithTypos("ou", "oi")).isFalse();
        assertThat(EditDistance.matchesWithTypos("pedor", "pedro")).isTrue();
    }
}
