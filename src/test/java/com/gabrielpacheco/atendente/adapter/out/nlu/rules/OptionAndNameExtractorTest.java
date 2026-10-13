package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.conversation.PersonName;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Extração de opções e de nomes")
class OptionAndNameExtractorTest {

    private static final String[] SERVICOS = {"Corte masculino", "Barba", "Corte + barba", "Sobrancelha"};
    private static final String[] PROFISSIONAIS = {"João Batista", "Pedro Henrique", "Tanto faz"};
    private static final List<String> NOME_SUGERIDO = List.of("Sim, no nome de Gabriel", "Outro nome");

    @Nested
    @DisplayName("Opções")
    class Opcoes {

        private final OptionExtractor extrator = new OptionExtractor();

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({
                "2, 2",
                "opcao 2, 2",
                "a opção 3, 3",
                "numero 4, 4",
                "o 1, 1",
                "a 2, 2",
                "a segunda, 2",
                "segunda opcao, 2",
                "o primeiro, 1",
                "terceira, 3",
                "quero o quarto, 4",
                "a ultima, 4",
                "barba, 2",
                "sobrancelah, 4",
                "corte + barba, 3"
        })
        @DisplayName("Reconhece número, ordinal e o próprio texto da opção")
        void deveReconhecerAOpcao(String texto, int esperada) {
            assertThat(extrair(texto, Expectation.OPTION, SERVICOS)).contains(esperada);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"na segunda", "segunda", "sexta", "um corte", "uma barba", "5", "0", "opcao 9"})
        @DisplayName("Dia da semana, \"um\" e números fora da lista não são opção")
        void naoDeveConfundirComOpcao(String texto) {
            assertThat(extrair(texto, Expectation.OPTION, SERVICOS)).isEmpty();
        }

        @Test
        @DisplayName("\"Tanto faz\" é a última opção na escolha do profissional")
        void tantoFazDeveSerAUltimaOpcao() {
            assertThat(extrair("tanto faz", Expectation.OPTION, PROFISSIONAIS)).contains(3);
        }

        @Test
        @DisplayName("No menu inicial, só um dígito sozinho conta")
        void noMenuSoDigitoSozinhoConta() {
            String[] menu = {"Agendar", "Meus agendamentos", "Preços", "Endereço e horário"};

            assertThat(extrair("3", Expectation.NONE, menu)).contains(3);
            assertThat(extrair("opcao 3", Expectation.NONE, menu)).isEmpty();
            assertThat(extrair("precos", Expectation.NONE, menu)).isEmpty();
            assertThat(extrair("3", Expectation.NONE)).isEmpty();
        }

        @Test
        @DisplayName("Na pergunta do nome com sugestão, \"1\", \"2\" e \"Outro nome\" escolhem a opção")
        void devePegarOpcaoNaPerguntaDoNome() {
            String[] opcoes = NOME_SUGERIDO.toArray(String[]::new);

            assertThat(extrair("1", Expectation.NAME, opcoes)).contains(1);
            assertThat(extrair("2", Expectation.NAME, opcoes)).contains(2);
            assertThat(extrair("Outro nome", Expectation.NAME, opcoes)).contains(2);
            assertThat(extrair("Gabriel", Expectation.NAME, opcoes)).isEmpty();
            assertThat(extrair("2", Expectation.NAME)).isEmpty();
        }

        @Test
        @DisplayName("Na data ou no sim e não, ninguém escolhe opção")
        void naoDeveProcurarOpcaoForaDaHora() {
            assertThat(extrair("2", Expectation.DATE, SERVICOS)).isEmpty();
            assertThat(extrair("1", Expectation.YES_NO, SERVICOS)).isEmpty();
        }

        private Optional<Integer> extrair(String texto, Expectation expectativa, String... opcoes) {
            Spans spans = new Spans(TextNormalizer.normalize(texto).tokens());
            return extrator.extract(spans, Barbearia.contexto(expectativa, opcoes));
        }
    }

    @Nested
    @DisplayName("\"Tanto faz\"")
    class QualquerProfissional {

        private final AnyProfessionalExtractor extrator = new AnyProfessionalExtractor();

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"tanto faz", "qualquer um", "com qualquer uma", "qualquer", "quem tiver", "indiferente"})
        @DisplayName("Reconhece que o cliente aceita qualquer profissional")
        void deveReconhecerQualquerProfissional(String texto) {
            assertThat(extrator.extract(spans(texto))).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"qualquer dia", "qualquer horario", "com o joao"})
        @DisplayName("\"qualquer dia\" fala de data, não de profissional")
        void naoDeveConfundirComQualquerDia(String texto) {
            assertThat(extrator.extract(spans(texto))).isFalse();
        }

        private Spans spans(String texto) {
            return new Spans(TextNormalizer.normalize(texto).tokens());
        }
    }

    @Nested
    @DisplayName("Nomes")
    class Nomes {

        private final NameExtractor extrator = new NameExtractor();

        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource({
                "me chamo joão da silva, João da Silva",
                "gabriel pacheco, Gabriel Pacheco",
                "meu nome é Ana Souza, Ana Souza",
                "meu nome completo é ana souza, Ana Souza",
                "Oi! Sou o Pedro, Pedro",
                "pode colocar no nome de Márcia, Márcia",
                "aqui é a Clara, Clara",
                "joão, João",
                "Ana Souza por favor, Ana Souza"
        })
        @DisplayName("Tira prefixos, recupera acentos e coloca maiúsculas")
        void deveExtrairONome(String texto, String esperado) {
            assertThat(extrator.extract(texto, List.of())).contains(esperado);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"sim", "não", "cancelar", "menu", "ok", "2", "Ana 2", "quero marcar outro dia com alguém",
                "de souza", "me chamo"})
        @DisplayName("Recusa comandos, números, frases longas e textos sem nome")
        void naoDeveAceitarOQueNaoENome(String texto) {
            assertThat(extrator.extract(texto, List.of())).isEmpty();
        }

        @Test
        @DisplayName("Recusa o texto de uma opção oferecida, como \"Outro nome\"")
        void naoDeveAceitarORotuloDeUmaOpcao() {
            assertThat(extrator.extract("Outro nome", NOME_SUGERIDO)).isEmpty();
            assertThat(extrator.extract("outro nom", NOME_SUGERIDO)).isEmpty();
            assertThat(extrator.extract("Gabriel", NOME_SUGERIDO)).contains("Gabriel");
        }

        @Test
        @DisplayName("A validação do nome do perfil do WhatsApp é a mesma do domínio")
        void deveValidarONomeDoPerfil() {
            assertThat(PersonName.isValid("João Pereira")).isTrue();
            assertThat(PersonName.isValid("🔥 Gabi 🔥")).isFalse();
        }
    }
}
