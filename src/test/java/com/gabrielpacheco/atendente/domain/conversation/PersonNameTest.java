package com.gabrielpacheco.atendente.domain.conversation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Nome de pessoa")
class PersonNameTest {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"Ana", "João Pereira", "Maria da Silva Souza", "D'Ávila", "Ana-Clara", "Jô"})
    @DisplayName("Aceita de 1 a 4 palavras com letras, apóstrofo, ponto e hífen")
    void deveAceitarNomes(String nome) {
        assertThat(PersonName.isValid(nome)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "  ", "A", "Gabriel 🔥", "Ana 2", "123", "Ana Maria da Silva Souza", "-Ana"})
    @DisplayName("Recusa nomes vazios, curtos, com números, emojis ou palavras demais")
    void deveRecusarNomesInvalidos(String nome) {
        assertThat(PersonName.isValid(nome)).isFalse();
    }

    @Test
    @DisplayName("Recusa nome nulo e nome com mais de 60 caracteres")
    void deveRecusarNuloELongo() {
        assertThat(PersonName.isValid(null)).isFalse();
        assertThat(PersonName.isValid("A" + "a".repeat(60))).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
            "joão da silva, João da Silva",
            "MARIA DOS SANTOS E SOUZA, Maria dos Santos e Souza",
            "ana-clara d'ávila, Ana-Clara D'Ávila",
            "de souza, De Souza"
    })
    @DisplayName("Coloca maiúsculas nas palavras e deixa os conectivos em minúscula")
    void deveColocarMaiusculas(String nome, String esperado) {
        assertThat(PersonName.titleCase(nome)).isEqualTo(esperado);
    }
}
