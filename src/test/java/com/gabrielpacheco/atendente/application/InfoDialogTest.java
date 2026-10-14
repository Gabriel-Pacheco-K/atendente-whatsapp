package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Diálogos de informação")
class InfoDialogTest {

    private static final String PRECOS = """
            Nossos serviços:
            - Corte masculino: R$ 45,00 (30 min)
            - Barba: R$ 35,00 (30 min)
            - Corte + barba: R$ 70,00 (60 min)
            - Sobrancelha: R$ 15,00 (15 min)
            Quer agendar? É só me dizer o serviço e o dia.""";

    private static final String HORARIO = """
            Nosso horário de funcionamento:
            Terça a sexta: 09:00 às 19:00
            Sábado: 08:00 às 14:00
            Segunda e domingo: fechado""";

    private final Dialogo dialogo = Dialogo.naWeb();

    @Test
    @DisplayName("Lista os preços de todos os serviços")
    void deveListarOsPrecos() {
        dialogo.cliente("qual o valor dos servicos?").atendenteDiz(PRECOS).estadoE(Idle.class);
    }

    @Test
    @DisplayName("O preço de um serviço é a resposta, não o começo de um agendamento")
    void precoDeUmServicoNaoDeveIniciarAgendamento() {
        dialogo.cliente("quanto custa a barba?")
                .atendenteDiz("*Barba* custa R$ 35,00 e leva 30 min. Quer agendar? É só me dizer o dia.")
                .estadoE(Idle.class);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource({"vcs abrem domingo?", "abre segunda?", "voces abrem sabado?"})
    @DisplayName("Perguntar se abre num dia responde o horário, em vez de começar um agendamento")
    void perguntaSeAbreNumDiaDeveResponderOHorario(String pergunta) {
        dialogo.cliente(pergunta).atendenteDiz(HORARIO).atendenteNaoDiz("Qual serviço").estadoE(Idle.class);
    }

    @Test
    @DisplayName("Perguntar quanto tempo um serviço leva responde a duração")
    void quantoTempoDeveResponderADuracao() {
        dialogo.cliente("quanto tempo demora o corte?")
                .atendenteDiz("*Corte masculino* custa R$ 45,00 e leva 30 min.")
                .estadoE(Idle.class);
    }

    @Test
    @DisplayName("Endereço, inclusive quando a pergunta cita a barbearia")
    void deveResponderOEndereco() {
        dialogo.cliente("onde fica a barbearia?")
                .atendenteDiz("Ficamos na Rua das Palmeiras, 120, Centro, Florianópolis/SC (endereço fictício).")
                .estadoE(Idle.class);
    }

    @Test
    @DisplayName("Horário de funcionamento com os dias agrupados")
    void deveResponderOHorarioAgrupado() {
        dialogo.cliente("que horas abre?").atendenteDiz(HORARIO);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource(delimiter = '|', textBlock = """
            aceita pix?                 | Aceitamos Pix, cartão de débito e crédito e dinheiro.
            da para pagar no cartao?    | Aceitamos Pix, cartão de débito e crédito e dinheiro.
            tem estacionamento?         | Temos 3 vagas em frente à barbearia e estacionamento rotativo na rua.
            onde eu deixo o carro?      | Temos 3 vagas em frente à barbearia e estacionamento rotativo na rua.
            voces atendem crianca?      | Atendemos crianças a partir de 3 anos, com um responsável.
            """)
    @DisplayName("Responde cada pergunta frequente do business.yml")
    void deveResponderAsPerguntasFrequentes(String pergunta, String resposta) {
        dialogo.cliente(pergunta).atendenteDiz(resposta).estadoE(Idle.class);
    }

    @Test
    @DisplayName("As opções 3 e 4 do menu respondem preços e endereço com horário")
    void opcoesDoMenuDevemResponder() {
        dialogo.cliente("oi");

        dialogo.cliente("3").atendenteDiz(PRECOS);
        dialogo.cliente("4")
                .atendenteDiz("Ficamos na Rua das Palmeiras, 120, Centro, Florianópolis/SC (endereço fictício).\n" + HORARIO);
    }

    @Test
    @DisplayName("Agradecimento e recusa no início têm resposta própria")
    void agradecimentoERecusaDevemSerRespondidos() {
        dialogo.cliente("muito obrigado").atendenteDiz("Por nada! Se precisar, é só chamar.");
        dialogo.cliente("não").atendenteDiz("Tudo bem! Se precisar, é só chamar.");
    }
}
