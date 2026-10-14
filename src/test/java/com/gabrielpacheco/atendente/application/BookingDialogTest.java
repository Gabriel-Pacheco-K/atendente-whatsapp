package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.in.Diagnostics;
import com.gabrielpacheco.atendente.domain.booking.Booking;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.conversation.AskingName;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingService;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.fixtures.Ambiente;
import com.gabrielpacheco.atendente.fixtures.Barbearia;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Diálogos de agendamento")
class BookingDialogTest {

    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);

    @Test
    @DisplayName("A frase completa, com erros de digitação, agenda em três mensagens")
    void deveAgendarComUmaFraseSo() {
        Dialogo dialogo = Dialogo.naWeb();

        dialogo.cliente("oiii")
                .atendenteDiz("Olá! Eu sou o atendente virtual da *Barbearia Navalha de Ouro*")
                .estadoE(Idle.class);
        dialogo.cliente("qro agnedar um corte amanha as 3 da tarde c/ o joao")
                .atendenteDiz("Boa notícia: o *João Batista* tem horário livre na *quarta-feira, 14/10, às 15:00*"
                        + " para *Corte masculino* (30 min, R$ 45,00). Para reservar, qual nome eu coloco no agendamento?")
                .estadoE(AskingName.class);
        dialogo.cliente("gabriel pacheco")
                .atendenteDiz("No nome de Gabriel Pacheco.")
                .estadoE(ConfirmingBooking.class);
        dialogo.cliente("sim")
                .atendenteDiz("Agendado, Gabriel! Seu código é *#1*.")
                .estadoE(Idle.class);

        assertThat(dialogo.agenda().postCount()).isEqualTo(1);
        assertThat(dialogo.agenda().agendamento(1).customerName()).isEqualTo("Gabriel Pacheco");
        Booking gravado = dialogo.ambiente().agendamentos().doAgendamento(1);
        assertThat(gravado.customerId()).isEqualTo(dialogo.clienteSalvo().id());
        assertThat(gravado.serviceId()).isEqualTo(CORTE.id());
        assertThat(gravado.startAt()).isEqualTo(QUARTA.atTime(15, 0));
    }

    @Test
    @DisplayName("Os bastidores mostram quem entendeu, o que entendeu e a mudança de estado")
    void deveMostrarOsBastidoresDoTurno() {
        Dialogo dialogo = Dialogo.naWeb().cliente("qro agnedar um corte amanha as 3 da tarde c/ o joao");

        Diagnostics bastidores = dialogo.resposta().diagnostics();
        assertThat(bastidores.interpretedBy()).isEqualTo(InterpreterKind.RULES);
        assertThat(bastidores.intent()).isEqualTo(Intent.BOOK);
        assertThat(bastidores.confidence()).isCloseTo(0.9, within(1e-9));
        assertThat(bastidores.entities()).containsEntry("service", "Corte masculino")
                .containsEntry("professional", "João Batista")
                .containsEntry("date", "2026-10-14")
                .containsEntry("time", "15:00");
        assertThat(bastidores.stateBefore()).isEqualTo("IDLE");
        assertThat(bastidores.stateAfter()).isEqualTo("ASKING_NAME");
    }

    @Test
    @DisplayName("Pergunta só o que falta, um passo de cada vez")
    void devePerguntarSoOQueFalta() {
        Dialogo dialogo = Dialogo.naWeb();

        dialogo.cliente("quero agendar").atendenteDiz("Qual serviço você quer?").estadoE(ChoosingService.class);
        dialogo.cliente("barba").atendenteDiz("Com quem você prefere?").estadoE(ChoosingProfessional.class);
        dialogo.cliente("pedro").atendenteDiz("Para qual dia?").estadoE(ChoosingDate.class);
        dialogo.cliente("sexta")
                .atendenteDiz("Horários livres na *sexta-feira, 16/10* para *Barba* (30 min, R$ 35,00):")
                .atendenteDiz("1. 10:00")
                .estadoE(ChoosingSlot.class);
        dialogo.cliente("1").atendenteDiz("qual nome eu coloco").estadoE(AskingName.class);
        dialogo.cliente("Ana Souza")
                .atendenteDiz("*Barba* com *Pedro Henrique*\nSexta-feira, 16/10, às 10:00, R$ 35,00")
                .estadoE(ConfirmingBooking.class);
    }

    @Test
    @DisplayName("O nome é pedido só no primeiro agendamento")
    void devePedirONomeSoNaPrimeiraVez() {
        Dialogo dialogo = Dialogo.naWeb();
        dialogo.cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim");

        dialogo.cliente("barba sexta 11h com o pedro")
                .atendenteDiz("Confirma o agendamento?")
                .atendenteDiz("No nome de Gabriel.")
                .atendenteNaoDiz("qual nome")
                .estadoE(ConfirmingBooking.class);
    }

    @Nested
    @DisplayName("Nome sugerido pelo perfil do WhatsApp")
    class NomeSugerido {

        private final Dialogo dialogo = Dialogo.noWhatsApp("João Pereira")
                .cliente("corte amanha 10h com o joao");

        @Test
        @DisplayName("Oferece o primeiro nome do perfil")
        void deveOferecerOPrimeiroNomeDoPerfil() {
            dialogo.atendenteDiz("posso colocar no nome de *João*?\n1. Sim, no nome de João\n2. Outro nome")
                    .estadoE(AskingName.class);
        }

        @Test
        @DisplayName("\"1\" aceita o nome sugerido e o grava")
        void umDeveAceitarONomeSugerido() {
            dialogo.cliente("1").atendenteDiz("No nome de João.").estadoE(ConfirmingBooking.class);

            assertThat(dialogo.clienteSalvo().name()).isEqualTo("João");
        }

        @Test
        @DisplayName("\"sim\" também aceita o nome sugerido")
        void simDeveAceitarONomeSugerido() {
            dialogo.cliente("sim").atendenteDiz("No nome de João.").estadoE(ConfirmingBooking.class);
        }

        @Test
        @DisplayName("\"2\" pede outro nome")
        void doisDevePedirOutroNome() {
            dialogo.cliente("2").atendenteDiz("qual nome eu coloco no agendamento?").estadoE(AskingName.class);
            dialogo.cliente("Carlos Lima").atendenteDiz("No nome de Carlos Lima.");

            assertThat(dialogo.clienteSalvo().name()).isEqualTo("Carlos Lima");
        }

        @Test
        @DisplayName("O botão \"Outro nome\" pede outro nome e não vira o nome \"Outro Nome\"")
        void botaoOutroNomeDevePedirOutroNome() {
            dialogo.cliente("Outro nome").atendenteDiz("qual nome eu coloco no agendamento?").estadoE(AskingName.class);

            assertThat(dialogo.clienteSalvo().name()).isNull();
        }
    }

    @Nested
    @DisplayName("Na hora do nome, o nome vence os dados de agenda sem pista clara")
    class NomeContraAgenda {

        private final Dialogo dialogo = Dialogo.naWeb().cliente("corte amanha 10h com o joao");

        @Test
        @DisplayName("\"joão\" é o nome do cliente, não a troca de profissional")
        void joaoDeveSerONome() {
            dialogo.cliente("joão").atendenteDiz("No nome de João.").estadoE(ConfirmingBooking.class);
        }

        @Test
        @DisplayName("\"pedro silva\" é o nome do cliente")
        void pedroSilvaDeveSerONome() {
            dialogo.cliente("pedro silva")
                    .atendenteDiz("*Corte masculino* com *João Batista*")
                    .atendenteDiz("No nome de Pedro Silva.");
        }

        @Test
        @DisplayName("\"clara\" não é corrigida para \"claro\"")
        void claraDeveSerONome() {
            dialogo.cliente("clara").atendenteDiz("No nome de Clara.");
        }

        @Test
        @DisplayName("\"meu nome completo é ana souza\" não é o combo \"completo\"")
        void nomeCompletoDeveSerONome() {
            dialogo.cliente("meu nome completo é ana souza")
                    .atendenteDiz("*Corte masculino* com *João Batista*")
                    .atendenteDiz("No nome de Ana Souza.");
        }

        @Test
        @DisplayName("\"na verdade sexta\" é uma correção do dia")
        void naVerdadeSextaDeveCorrigirODia() {
            dialogo.cliente("na verdade sexta")
                    .atendenteDiz("*sexta-feira, 16/10, às 10:00*")
                    .atendenteDiz("qual nome eu coloco")
                    .estadoE(AskingName.class);

            assertThat(((AskingName) dialogo.estado()).draft().date()).isEqualTo(SEXTA);
        }
    }

    @Test
    @DisplayName("Horário ocupado oferece os três mais próximos, e a FAQ no meio não perde o passo")
    void deveOferecerOsTresHorariosMaisProximosQuandoOPedidoEstaOcupado() {
        Dialogo dialogo = Dialogo.naWeb();
        dialogo.agenda().ocupar(PEDRO, SEXTA.atTime(10, 0));

        dialogo.cliente("corte sexta 10h com o pedro")
                .atendenteDiz("Às 10:00 de *sexta-feira, 16/10*, o Pedro já tem cliente. Os horários mais próximos"
                        + " com ele são:\n1. 10:30\n2. 10:45\n3. 11:00")
                .notaE("REQUESTED_TAKEN")
                .estadoE(ChoosingSlot.class);
        dialogo.cliente("aceita pix?");
        assertThat(dialogo.respostas()).hasSize(2);
        dialogo.atendenteDiz("Aceitamos Pix, cartão de débito e crédito e dinheiro.")
                .atendenteDiz("Voltando ao seu agendamento: qual destes horários você prefere? (1 a 3)\n1. 10:30")
                .estadoE(ChoosingSlot.class);
        dialogo.cliente("10:30").cliente("Gabriel").atendenteDiz("Sexta-feira, 16/10, às 10:30");
    }

    @Test
    @DisplayName("Segunda fechada sugere o próximo dia, alternando os profissionais no \"tanto faz\"")
    void deveSugerirOProximoDiaQuandoODiaEstaFechado() {
        Dialogo dialogo = Dialogo.naWeb();

        dialogo.cliente("quero marcar um corte pra segunda").estadoE(ChoosingProfessional.class);
        dialogo.cliente("qualquer um")
                .atendenteDiz("Às segundas a barbearia não abre. O próximo dia com horários é *terça-feira, 20/10*:\n"
                        + "1. 09:00 com João\n2. 10:45 com Pedro\n3. 13:00 com João\n4. 14:45 com Pedro\n"
                        + "5. 16:45 com João\n6. 18:30 com Pedro")
                .notaE("MOVED_TO_NEXT_DAY");
    }

    @Test
    @DisplayName("Um feriado do business.yml é pulado, com o motivo")
    void devePularOFeriado() {
        Dialogo dialogo = Dialogo.naWeb().cliente("quero marcar um corte pra segunda").cliente("qualquer um");

        dialogo.cliente("dia 31/10")
                .atendenteDiz("Dia 31/10 (Confraternização da equipe) a barbearia estará fechada. O próximo dia com"
                        + " horários é *terça-feira, 03/11*:");
    }

    @Test
    @DisplayName("Dia lotado sugere o seguinte")
    void deveSugerirODiaSeguinteQuandoOPedidoEstaLotado() {
        Dialogo dialogo = Dialogo.naWeb();
        dialogo.agenda().lotar(JOAO, QUARTA);

        dialogo.cliente("corte quarta com o joao")
                .atendenteDiz("A quarta-feira, 14/10 já está lotada. O próximo dia com horários é *quinta-feira, 15/10*:");
    }

    @Test
    @DisplayName("Uma data além de 30 dias é recusada com o limite")
    void deveRecusarDataAlemDoLimite() {
        Dialogo.naWeb()
                .cliente("corte dia 15/11 com o joao")
                .atendenteDiz("Consigo agendar com até 30 dias de antecedência (até quinta-feira, 12/11). Para qual dia?")
                .estadoE(ChoosingDate.class);
    }

    @Test
    @DisplayName("\"não\" no resumo volta para o dia, mantendo serviço e profissional")
    void naoDeveManterServicoEProfissional() {
        Dialogo dialogo = Dialogo.naWeb().cliente("barba sexta 10h com o pedro").cliente("Gabriel");

        dialogo.cliente("não").atendenteDiz("Sem problemas, não marquei. Para qual dia você prefere?")
                .estadoE(ChoosingDate.class);
        dialogo.cliente("sábado")
                .atendenteDiz("Horários livres no *sábado, 17/10* para *Barba*")
                .atendenteNaoDiz("com João");

        assertThat(dialogo.agenda().postCount()).isZero();
    }

    @Test
    @DisplayName("\"na verdade prefiro às 16h\" no resumo confere o novo horário e mostra outro resumo")
    void deveCorrigirOHorarioNaConfirmacao() {
        Dialogo dialogo = Dialogo.naWeb().cliente("corte amanha as 3 da tarde com o joao").cliente("Gabriel");

        dialogo.cliente("na verdade prefiro as 16h")
                .atendenteDiz("Boa notícia: o *João Batista* tem horário livre na *quarta-feira, 14/10, às 16:00*")
                .atendenteDiz("Confirma o agendamento?")
                .atendenteDiz("Quarta-feira, 14/10, às 16:00")
                .estadoE(ConfirmingBooking.class);
    }

    @Test
    @DisplayName("Período: \"sábado de manhã\" com \"tanto faz\" espalha a manhã entre os dois")
    void deveOferecerAManhaDeSabado() {
        Dialogo dialogo = Dialogo.naWeb().cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim");

        dialogo.cliente("tem horario sabado de manha pra barba?").atendenteDiz("Com quem você prefere?");
        dialogo.cliente("tanto faz")
                .atendenteDiz("Horários livres no *sábado, 17/10* de manhã para *Barba* (30 min, R$ 35,00):\n"
                        + "1. 08:00 com João\n2. 08:45 com Pedro\n3. 09:30 com João\n4. 10:15 com Pedro\n"
                        + "5. 11:00 com João\n6. 11:45 com Pedro");
        dialogo.cliente("4")
                .atendenteDiz("*Barba* com *Pedro Henrique*\nSábado, 17/10, às 10:15")
                .estadoE(ConfirmingBooking.class);
    }

    @Test
    @DisplayName("\"tanto faz\" já na primeira frase vai direto à lista")
    void tantoFazNaPrimeiraFraseDeveIrDiretoALista() {
        Dialogo.naWeb()
                .cliente("corte amanha com qualquer um")
                .atendenteDiz("Horários livres na *quarta-feira, 14/10* para *Corte masculino*")
                .atendenteDiz("1. 09:00 com João\n2. 10:45 com Pedro")
                .estadoE(ChoosingSlot.class);
    }

    @Nested
    @DisplayName("Horário digitado fora da lista")
    class HorarioForaDaLista {

        private final Dialogo dialogo = Dialogo.naWeb().cliente("corte sexta com o joao");

        @Test
        @DisplayName("Livre: é conferido de novo na agenda e segue")
        void horarioLivreDeveSeguir() {
            dialogo.cliente("14h")
                    .atendenteDiz("Boa notícia: o *João Batista* tem horário livre na *sexta-feira, 16/10, às 14:00*")
                    .estadoE(AskingName.class);
        }

        @Test
        @DisplayName("Ocupado: oferece os mais próximos")
        void horarioOcupadoDeveOferecerOsMaisProximos() {
            dialogo.agenda().ocupar(JOAO, SEXTA.atTime(14, 0));

            dialogo.cliente("14h")
                    .atendenteDiz("Às 14:00 de *sexta-feira, 16/10*, o João já tem cliente.")
                    .estadoE(ChoosingSlot.class);
        }

        @Test
        @DisplayName("Fora do expediente: diz quando o profissional atende, sem inventar cliente")
        void foraDoExpedienteDeveDizerOExpediente() {
            dialogo.cliente("as 20h")
                    .atendenteDiz("O João atende na sexta das 09:00 às 12:00 e das 13:00 às 19:00. Os mais próximos são:")
                    .atendenteNaoDiz("já tem cliente")
                    .notaE("OUTSIDE_HOURS");
        }

        @Test
        @DisplayName("No almoço do profissional também é fora do expediente")
        void horarioDeAlmocoDeveSerForaDoExpediente() {
            dialogo.cliente("12:30").notaE("OUTSIDE_HOURS").atendenteNaoDiz("já tem cliente");
        }

        @Test
        @DisplayName("Um serviço que passaria do fim do expediente também é fora do expediente")
        void servicoQuePassaDoExpedienteDeveSerForaDoExpediente() {
            dialogo.cliente("18:45").notaE("OUTSIDE_HOURS").atendenteNaoDiz("já tem cliente");
        }

        @Test
        @DisplayName("Fora da grade de 15 minutos: explica a grade e oferece os mais próximos")
        void horarioForaDaGradeDeveExplicarAGrade() {
            dialogo.cliente("10:10")
                    .atendenteDiz("Os horários são de 15 em 15 minutos. Os mais próximos de 10:10 são:\n"
                            + "1. 10:00\n2. 10:15\n3. 10:30")
                    .atendenteNaoDiz("já tem cliente")
                    .notaE("OFF_GRID");
        }
    }

    @Test
    @DisplayName("Um horário de hoje que já passou ou está em cima avisa por quê")
    void horarioEmCimaDeveAvisar() {
        Dialogo.naWeb()
                .cliente("corte hoje as 14:15 com o joao")
                .atendenteDiz("Esse horário já passou ou está muito em cima. Os próximos livres são:\n"
                        + "1. 14:30\n2. 14:45\n3. 15:00")
                .notaE("TOO_SOON");
    }

    @Test
    @DisplayName("Com mais de 10 serviços, mostra os 10 primeiros e aceita o nome dos outros")
    void deveMostrarNoMaximoDezServicos() {
        List<ServiceType> servicos = new ArrayList<>(Barbearia.servicosDaAgenda());
        IntStream.rangeClosed(5, 11).forEach(id -> servicos.add(
                new ServiceType(id, "Tratamento " + (char) ('A' + id - 5), 30, new BigDecimal("50.00"), List.of())));
        Dialogo dialogo = new Ambiente(Barbearia.profissionais(), servicos).clienteNaWeb();

        dialogo.cliente("quero agendar")
                .atendenteDiz("10. Tratamento F (30 min, R$ 50,00)\nOu escreva o nome do serviço.")
                .atendenteNaoDiz("Tratamento G");
        assertThat(((ChoosingService) dialogo.estado()).offeredServiceIds()).hasSize(10);

        dialogo.cliente("tratamento g").atendenteDiz("Com quem você prefere?");
    }

    @Test
    @DisplayName("Com quatro profissionais e a agenda cheia, a busca para no teto de chamadas")
    void deveRespeitarOTetoDeChamadasPorTurno() {
        List<Professional> profissionais = List.of(JOAO, PEDRO, Barbearia.profissional(3, "Lucas Prado"),
                Barbearia.profissional(4, "Rafael Dias"));
        Ambiente ambiente = new Ambiente(profissionais, Barbearia.servicosDaAgenda());
        for (LocalDate dia = QUARTA; dia.isBefore(QUARTA.plusDays(7)); dia = dia.plusDays(1)) {
            for (Professional profissional : profissionais) {
                ambiente.agenda().lotar(profissional, dia);
            }
        }

        // 14 chamadas cobrem quarta, quinta e sexta (4 + 4 + 4); sábado já passaria do teto.
        ambiente.clienteNaWeb()
                .cliente("corte quarta com qualquer um")
                .atendenteDiz("Não encontrei horários livres até sexta-feira, 16/10. Quer tentar outra data?")
                .estadoE(ChoosingDate.class);
    }

    @Test
    @DisplayName("Com a agenda lenta, o turno para antes de passar do prazo")
    void agendaLentaNaoDevePassarDoPrazoDoTurno() {
        Dialogo dialogo = Dialogo.naWeb().cliente("oi");
        // Lotados, quarta e quinta gastam duas consultas lentas, e a de sexta já não caberia no prazo.
        dialogo.agenda().lotar(JOAO, QUARTA);
        dialogo.agenda().lotar(JOAO, QUARTA.plusDays(1));
        dialogo.agenda().latenciaDaAgenda(Duration.ofMillis(4900));
        Instant antes = dialogo.ambiente().relogio().instant();

        dialogo.cliente("corte quarta com o joao")
                .atendenteDiz("Não consegui consultar a agenda agora.")
                .atendenteDiz("(48) 3333-0000");

        Duration duracaoDoTurno = Duration.between(antes, dialogo.ambiente().relogio().instant());
        assertThat(duracaoDoTurno).isLessThanOrEqualTo(Barbearia.configuracao().turnBudget());
    }
}
