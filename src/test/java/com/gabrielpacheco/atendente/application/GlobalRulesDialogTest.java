package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import com.gabrielpacheco.atendente.fixtures.FakeSchedulingPort.Operacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Regras que valem em qualquer passo da conversa")
class GlobalRulesDialogTest {

    private static final String MENU = "Olá! Eu sou o atendente virtual da *Barbearia Navalha de Ouro*.";

    private final Dialogo dialogo = Dialogo.naWeb();

    private Dialogo noResumo() {
        return dialogo.cliente("corte amanha 10h com o joao").cliente("Gabriel").estadoE(ConfirmingBooking.class);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"quero falar com uma pessoa", "atendente"})
    @DisplayName("Pedir uma pessoa funciona no meio do agendamento")
    void humanoDeveFuncionarEmQualquerPasso(String pedido) {
        noResumo().cliente(pedido)
                .atendenteDiz("Claro! Fale com a nossa equipe pelo (48) 3333-0000, de terça a sexta, das 9h às 19h,"
                        + " e aos sábados, das 8h às 14h.")
                .estadoE(Idle.class);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"menu", "sair"})
    @DisplayName("\"menu\" e \"sair\" recomeçam de qualquer passo")
    void menuDeveRecomecar(String pedido) {
        dialogo.cliente("quero agendar um corte").cliente(pedido).atendenteDiz(MENU).estadoE(Idle.class);
    }

    @Test
    @DisplayName("\"cancelar\" no meio do agendamento é desistência e ensina a cancelar um horário marcado")
    void cancelarNoMeioDoAgendamentoDeveSerDesistencia() {
        noResumo().cliente("cancelar")
                .atendenteDiz("Tudo bem, não marquei nada. Se quiser cancelar um horário já marcado, mande *cancelar* de"
                        + " novo.")
                .estadoE(Idle.class);

        assertThat(dialogo.agenda().postCount()).isZero();
    }

    /** Cada passo da conversa, com o caminho até ele. */
    private record Passo(String nome, Supplier<Dialogo> caminho) {

        @Override
        public String toString() {
            return nome;
        }
    }

    static Stream<Arguments> perguntasEmCadaPasso() {
        List<Passo> passos = List.of(
                new Passo("escolha do serviço", () -> Dialogo.naWeb().cliente("quero agendar")),
                new Passo("escolha do profissional", () -> Dialogo.naWeb().cliente("quero cortar o cabelo amanha")),
                new Passo("escolha do dia", () -> Dialogo.naWeb().cliente("corte com o joao")),
                new Passo("escolha do horário", () -> Dialogo.naWeb().cliente("corte amanha com o joao")),
                new Passo("nome", () -> Dialogo.naWeb().cliente("corte amanha 15h com o joao")),
                new Passo("nome sugerido", () -> Dialogo.noWhatsApp("Gabriel Pacheco")
                        .cliente("corte amanha 15h com o joao")),
                new Passo("resumo", () -> Dialogo.naWeb().cliente("corte amanha 15h com o joao").cliente("Gabriel")),
                new Passo("escolha do cancelamento", () -> Dialogo.naWeb()
                        .cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim")
                        .cliente("barba sexta 11h com o pedro").cliente("sim").cliente("cancelar")),
                new Passo("confirmação do cancelamento", () -> Dialogo.naWeb()
                        .cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim").cliente("cancelar")));
        Map<String, String> respostas = Map.of(
                "aceita pix?", "Aceitamos Pix, cartão de débito e crédito e dinheiro.",
                "quanto custa a barba?", "*Barba* custa R$ 35,00 e leva 30 min.",
                "vcs abrem sabado?", "Nosso horário de funcionamento:");
        return passos.stream().flatMap(passo -> respostas.entrySet().stream()
                .map(pergunta -> Arguments.of(passo, pergunta.getKey(), pergunta.getValue())));
    }

    @ParameterizedTest(name = "{0}: \"{1}\"")
    @MethodSource("perguntasEmCadaPasso")
    @DisplayName("Uma pergunta no meio do fluxo é respondida sem mudar nada, e a pergunta do passo se repete")
    void perguntaNoMeioDoFluxoDeveSerRespondidaSemMudarOPasso(Passo passo, String pergunta, String resposta) {
        Dialogo conversa = passo.caminho().get();
        ConversationState antes = conversa.estado();
        String nomeAntes = conversa.clienteSalvo().name();

        conversa.cliente(pergunta).atendenteDiz(resposta).atendenteDiz("Voltando ao seu agendamento: ");

        assertThat(conversa.respostas()).hasSize(2);
        assertThat(conversa.estado()).isEqualTo(antes);
        assertThat(conversa.clienteSalvo().name()).isEqualTo(nomeAntes);
    }

    @Test
    @DisplayName("Saudação e agradecimento no meio do fluxo repetem a pergunta")
    void saudacaoEAgradecimentoDevemRepetirOPasso() {
        dialogo.cliente("corte com o joao");

        dialogo.cliente("oi").atendenteDiz("Oi! Para qual dia?").estadoE(ChoosingDate.class);
        dialogo.cliente("obrigado").atendenteDiz("Por nada! Para qual dia?").estadoE(ChoosingDate.class);
    }

    @Test
    @DisplayName("\"bom dia, sexta\" na escolha do dia é a data, não uma saudação")
    void bomDiaSextaDeveSerAData() {
        dialogo.cliente("corte com o joao");

        dialogo.cliente("bom dia, sexta").atendenteDiz("*sexta-feira, 16/10*").estadoE(ChoosingSlot.class);
    }

    @Test
    @DisplayName("Ver os agendamentos no meio de um agendamento deixa o agendamento de lado")
    void meusAgendamentosNoMeioDoFluxoDeveDeixarOAgendamentoDeLado() {
        dialogo.cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim");
        dialogo.cliente("quero marcar uma barba");

        dialogo.cliente("meus agendamentos")
                .atendenteDiz("Deixei o agendamento de lado. Seus próximos agendamentos:")
                .estadoE(Idle.class);
    }

    @Nested
    @DisplayName("Conversa parada há mais de 30 minutos")
    class ConversaExpirada {

        @Test
        @DisplayName("\"sim\" atrasado não agenda: recomeça por segurança")
        void simAtrasadoNaoDeveAgendar() {
            noResumo().avancarRelogio(Duration.ofMinutes(31));

            dialogo.cliente("sim")
                    .atendenteDiz("Faz um tempinho que conversamos, então recomecei por segurança e nada foi marcado.")
                    .atendenteDiz(MENU)
                    .notaE("SAFETY_RESTART")
                    .estadoE(Idle.class);
            assertThat(dialogo.agenda().postCount()).isZero();
        }

        @Test
        @DisplayName("Um número atrasado não escolhe nada")
        void numeroAtrasadoNaoDeveEscolher() {
            dialogo.cliente("corte sexta com o joao").estadoE(ChoosingSlot.class);
            dialogo.avancarRelogio(Duration.ofMinutes(31));

            dialogo.cliente("2").notaE("SAFETY_RESTART").estadoE(Idle.class);
        }

        @Test
        @DisplayName("Um pedido novo recomeça e segue")
        void pedidoNovoDeveRecomecarESeguir() {
            noResumo().avancarRelogio(Duration.ofMinutes(31));

            dialogo.cliente("quero marcar barba")
                    .atendenteDiz("Como já fazia um tempinho, recomecei nossa conversa. Com quem você prefere?")
                    .estadoE(ChoosingProfessional.class);
        }

        @Test
        @DisplayName("Um áudio não reabre o resumo: o \"sim\" seguinte continua sem agendar")
        void audioNaoDeveReabrirOResumo() {
            noResumo().avancarRelogio(Duration.ofMinutes(31));

            dialogo.enviaAudio().atendenteDiz("Por enquanto eu só entendo mensagens de texto.").estadoE(Idle.class);
            dialogo.cliente("sim").atendenteNaoDiz("Agendado");

            assertThat(dialogo.agenda().postCount()).isZero();
        }
    }

    @Nested
    @DisplayName("\"sim\" repetido depois de agendar")
    class SimRepetido {

        @Test
        @DisplayName("Até 2 minutos depois, responde que já está confirmado, sem agendar de novo")
        void dentroDaJanelaDeveDizerQueJaEstaConfirmado() {
            noResumo().cliente("sim").atendenteDiz("Agendado");

            dialogo.avancarRelogio(Duration.ofSeconds(90)).cliente("sim")
                    .atendenteDiz("Seu horário de *Corte masculino* com *João Batista* na quarta-feira, 14/10, às 10:00,"
                            + " já está confirmado (código *#1*).");
            assertThat(dialogo.agenda().postCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Depois de 2 minutos, mostra o menu")
        void foraDaJanelaDeveMostrarOMenu() {
            noResumo().cliente("sim");

            dialogo.avancarRelogio(Duration.ofMinutes(3)).cliente("sim").atendenteDiz(MENU);
        }

        @Test
        @DisplayName("No WhatsApp, a mesma mensagem reenviada não gera resposta nova, porque a original já foi enviada")
        void mesmaMensagemNoWhatsAppNaoDeveResponderDeNovo() {
            Dialogo whatsapp = Dialogo.noWhatsApp("João Pereira").cliente("oi");

            whatsapp.reenviaAUltima("oi");

            assertThat(whatsapp.resposta().duplicate()).isTrue();
            assertThat(whatsapp.respostas()).isEmpty();
        }

        @Test
        @DisplayName("A mesma mensagem reenviada devolve a resposta original, sem efeito")
        void mesmaMensagemDeveSerIdempotente() {
            noResumo().cliente("sim");
            String original = dialogo.texto();

            dialogo.reenviaAUltima("sim");

            assertThat(dialogo.resposta().duplicate()).isTrue();
            assertThat(dialogo.texto()).isEqualTo(original);
            assertThat(dialogo.agenda().postCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Mensagens não entendidas")
    class NaoEntendidas {

        @Test
        @DisplayName("Na segunda seguida, oferece o telefone e o menu")
        void segundaSeguidaDeveOferecerOHumano() {
            dialogo.cliente("kkkkk xpto")
                    .atendenteDiz("Desculpe, não entendi. Você pode escrever, por exemplo: \"quero cortar o cabelo sexta"
                            + " às 10h\" ou \"quanto custa a barba?\".");
            dialogo.cliente("???")
                    .atendenteDiz("Ainda não consegui entender. Se preferir, fale com uma pessoa pelo (48) 3333-0000 ou"
                            + " mande *menu* para recomeçar.")
                    .atendenteDiz(MENU);
        }

        @Test
        @DisplayName("No resumo, também chega ao telefone na segunda")
        void noResumoDeveChegarAoHumano() {
            noResumo();

            dialogo.cliente("kkkkk").atendenteDiz("Não entendi. Responda *sim* ou *não*.");
            dialogo.cliente("xpto").atendenteDiz("Ainda não consegui entender.").estadoE(ConfirmingBooking.class);
        }

        @Test
        @DisplayName("\"sim\" e \"não\" juntos no resumo pedem uma resposta clara")
        void simENaoJuntosDevemPedirRespostaClara() {
            noResumo().cliente("sim... não, peraí")
                    .atendenteDiz("Responda *sim* para confirmar ou *não* para mudar.")
                    .estadoE(ConfirmingBooking.class);
        }

        @Test
        @DisplayName("Uma pergunta frequente no resumo é respondida, e o resumo se repete")
        void perguntaFrequenteNoResumoDeveRepetirOResumo() {
            noResumo().cliente("aceita pix?")
                    .atendenteDiz("Aceitamos Pix, cartão de débito e crédito e dinheiro.")
                    .atendenteDiz("Voltando ao seu agendamento: confirma o agendamento?")
                    .estadoE(ConfirmingBooking.class);
        }

        @Test
        @DisplayName("Áudio pede texto e não conta como mensagem não entendida")
        void audioNaoDeveContarComoErro() {
            dialogo.cliente("kkkkk xpto");

            dialogo.enviaAudio()
                    .atendenteDiz("Por enquanto eu só entendo mensagens de texto. Pode escrever o que você precisa?")
                    .notaE("UNSUPPORTED");
            dialogo.cliente("kkkkk xpto").atendenteDiz("Desculpe, não entendi.").atendenteNaoDiz("Ainda não");
        }
    }

    @Test
    @DisplayName("Com a agenda fora do ar, agendar dá o telefone, mas endereço e perguntas frequentes funcionam")
    void agendaForaDoArNaoDeveDerrubarAsInformacoes() {
        dialogo.agenda().falharSempreCom(Operacao.CATALOGO, new SchedulingUnavailableException("conexão recusada"));

        dialogo.cliente("quero agendar um corte")
                .atendenteDiz("Nosso sistema de agenda está fora do ar agora. Tente de novo em alguns minutos ou fale com"
                        + " a gente pelo (48) 3333-0000.");
        dialogo.cliente("onde fica?").atendenteDiz("Ficamos na Rua das Palmeiras, 120");
        dialogo.cliente("aceita pix?").atendenteDiz("Aceitamos Pix");
        dialogo.cliente("cancelar").atendenteDiz("Nosso sistema de agenda está fora do ar agora.");
    }

    @Test
    @DisplayName("A agenda volta: o catálogo é carregado de novo")
    void agendaQueVoltaDeveSerUsada() {
        dialogo.agenda().falharSempreCom(Operacao.CATALOGO, new SchedulingUnavailableException("conexão recusada"));
        dialogo.cliente("quero agendar um corte").atendenteDiz("fora do ar");

        dialogo.agenda().voltarAoNormal();

        dialogo.cliente("quero agendar um corte").atendenteDiz("Com quem você prefere?");
    }
}
