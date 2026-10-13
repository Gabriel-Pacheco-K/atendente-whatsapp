package com.gabrielpacheco.atendente.domain.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Normalização de texto")
class TextNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            Oiii, qro agnedar um corte p/ amanhã ás 3 da tarde c/ o Joao!! | oi quero agnedar um corte para amanha as 3 da tarde com o joao
            vc tem horario hj                                               | voce tem horario hoje
            AÇÚCAR, Ação e PÊSSEGO                                          | acucar acao e pessego
            siiim                                                           | sim
            oiii                                                            | oi
            carro                                                           | carro
            👍                                                              | sim
            👌🏽 obrigado                                                     | sim obrigado
            2️⃣                                                              | 2
            às 15:30 do dia 15/10                                           | as 15:30 do dia 15/10
            dia 20-10                                                       | dia 20-10
            corte-barba: 15:                                                | corte barba 15
            s/ barba, c/ máquina                                            | sem barba com maquina
            15hrs                                                           | 15hrs
            as 15 hrs                                                       | as 15 hora
            eae                                                             | e ai
            obg, vlw                                                        | obrigado valeu
            quero ter um horario na qua                                     | quero ter um horario na qua
            """)
    @DisplayName("Normaliza acentos, caixa, emojis, repetições, abreviações e preserva horas e datas")
    void deveNormalizar(String entrada, String esperado) {
        assertThat(TextNormalizer.normalize(entrada).text()).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({"s, sim", "n, nao", "S!, sim"})
    @DisplayName("\"s\" e \"n\" viram sim e não quando são a mensagem inteira")
    void deveExpandirSimENaoSozinhos(String entrada, String esperado) {
        assertThat(TextNormalizer.normalize(entrada).text()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("\"s\" e \"n\" no meio de uma frase ficam como estão")
    void naoDeveExpandirSENNoMeioDaFrase() {
        assertThat(TextNormalizer.normalize("plano s ou n").text()).isEqualTo("plano s ou n");
    }

    @Test
    @DisplayName("Separa as palavras e guarda o texto original")
    void deveTokenizarEGuardarOOriginal() {
        NormalizedText normalizado = TextNormalizer.normalize("  Quero   CORTAR!  ");

        assertThat(normalizado.tokens()).containsExactly("quero", "cortar");
        assertThat(normalizado.original()).isEqualTo("  Quero   CORTAR!  ");
    }

    @Test
    @DisplayName("Corta a mensagem em 1000 caracteres")
    void deveCortarEm1000Caracteres() {
        NormalizedText normalizado = TextNormalizer.normalize("a".repeat(999) + " bcdef");

        assertThat(normalizado.original()).hasSize(1000);
        assertThat(normalizado.tokens()).hasSize(1);
    }

    @Test
    @DisplayName("Texto nulo ou só com pontuação vira vazio")
    void deveTratarTextoVazio() {
        assertThat(TextNormalizer.normalize(null).tokens()).isEmpty();
        assertThat(TextNormalizer.normalize("???").text()).isEmpty();
    }

    @Test
    @DisplayName("fold só tira acentos e caixa, sem expandir abreviações")
    void deveDobrarSemExpandir() {
        assertThat(TextNormalizer.fold("João")).isEqualTo("joao");
        assertThat(TextNormalizer.fold("S")).isEqualTo("s");
    }
}
