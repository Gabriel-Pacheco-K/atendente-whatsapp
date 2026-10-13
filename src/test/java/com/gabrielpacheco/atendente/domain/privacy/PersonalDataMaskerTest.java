package com.gabrielpacheco.atendente.domain.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Máscara de dados pessoais")
class PersonalDataMaskerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            5548999990000      | +55 48 9****-0000
            +55 48 99999-0001  | +55 48 9****-0001
            554833330000       | +55 48 3****-0000
            (48) 99999-0001    | ****0001
            +1 202 555 0147    | ****0147
            123                | ****
            """)
    @DisplayName("Mostra só o DDD, o primeiro dígito e os 4 últimos do celular brasileiro")
    void deveMascararOTelefone(String telefone, String esperado) {
        assertThat(PersonalDataMasker.maskPhone(telefone)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Telefone ausente vira só a máscara")
    void deveMascararTelefoneNulo() {
        assertThat(PersonalDataMasker.maskPhone(null)).isEqualTo("****");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"me liga no 48 99999-0000", "meu zap é +5548999990000", "fixo (48) 3333-0000 ramal"})
    @DisplayName("Troca sequências com 9 ou mais dígitos por [número]")
    void deveEsconderTelefonesNoTexto(String texto) {
        assertThat(PersonalDataMasker.scrubPhones(texto))
                .contains("[número]")
                .doesNotContainPattern("\\d{4}");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"quero dia 20/10/2026", "dia 2026-10-14", "às 15:30", "corte por 45"})
    @DisplayName("Datas, horas e valores ficam intactos")
    void naoDeveMexerEmDatasEHoras(String texto) {
        assertThat(PersonalDataMasker.scrubPhones(texto)).isEqualTo(texto);
    }
}
