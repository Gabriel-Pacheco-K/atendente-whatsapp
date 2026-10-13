package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.UnknownBookingOutcomeException;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.fixtures.Ambiente;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import com.gabrielpacheco.atendente.fixtures.FakeSchedulingPort.Operacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Diálogos de agendamento quando a agenda falha")
class BookingFailuresDialogTest {

    private static final LocalDateTime SEXTA_10_30 = LocalDate.of(2026, 10, 16).atTime(10, 30);

    private final Ambiente ambiente = new Ambiente();
    private final Dialogo dialogo = ambiente.clienteNaWeb();

    /** Leva a conversa até o resumo de um corte com o Pedro na sexta às 10:30. */
    private Dialogo noResumo() {
        return dialogo.cliente("corte sexta 10:30 com o pedro").cliente("Gabriel Pacheco")
                .estadoE(ConfirmingBooking.class);
    }

    @Test
    @DisplayName("409 de outra pessoa: recalcula e volta para a escolha do horário")
    void conflitoDeOutraPessoaDeveOferecerOutrosHorarios() {
        ambiente.agenda().ocupar(PEDRO, SEXTA_10_30.minusMinutes(30));
        noResumo();
        ambiente.agenda().ocupar(PEDRO, SEXTA_10_30);

        dialogo.cliente("sim")
                .atendenteDiz("Puxa, alguém acabou de reservar esse horário. Às 10:30 de *sexta-feira, 16/10*, o Pedro"
                        + " já tem cliente. Os horários mais próximos com ele são:\n1. 11:00\n2. 11:15\n3. 11:30")
                .estadoE(ChoosingSlot.class);
    }

    @Test
    @DisplayName("422 repassa o motivo da agenda e pede outro dia")
    void regraDeNegocioDeveRepassarOMotivo() {
        noResumo();
        ambiente.agenda().falharProximaCom(Operacao.AGENDAMENTO,
                new BookingRejectedException("Agendamentos precisam de pelo menos 30 minutos de antecedência."));

        dialogo.cliente("sim")
                .atendenteDiz("Não consegui agendar: Agendamentos precisam de pelo menos 30 minutos de antecedência.\n"
                        + "Para qual dia você prefere?")
                .estadoE(ChoosingDate.class);
    }

    @Test
    @DisplayName("Erro definitivo da agenda mantém o resumo, para o cliente tentar de novo")
    void agendaIndisponivelDeveManterOResumo() {
        noResumo();
        ambiente.agenda().falharProximaCom(Operacao.AGENDAMENTO, new SchedulingUnavailableException("HTTP 500"));

        dialogo.cliente("sim")
                .atendenteDiz("Não consegui acessar a agenda agora. Guardei seus dados")
                .atendenteDiz("(48) 3333-0000")
                .estadoE(ConfirmingBooking.class);
        dialogo.cliente("sim").atendenteDiz("Agendado, Gabriel!");
    }

    @Test
    @DisplayName("Resultado incerto que a agenda gravou: reconcilia e responde \"Agendado\" com um POST só")
    void resultadoIncertoGravadoDeveSerReconciliado() {
        noResumo();
        ambiente.agenda().gravarEFalharProximoPostCom(new UnknownBookingOutcomeException("timeout"));

        dialogo.cliente("sim")
                .atendenteDiz("Agendado, Gabriel! Seu código é *#1*.")
                .notaE("RECONCILED")
                .estadoE(Idle.class);

        assertThat(ambiente.agenda().postCount()).isEqualTo(1);
        assertThat(ambiente.agendamentos().doAgendamento(1).customerId()).isEqualTo(dialogo.clienteSalvo().id());
    }

    @Test
    @DisplayName("Resultado incerto sem confirmação: guarda a tentativa, e o 409 do \"sim\" seguinte reconcilia")
    void tentativaPendenteDeveReconciliarNoConflitoSeguinte() {
        noResumo();
        ambiente.agenda().gravarEFalharProximoPostCom(new UnknownBookingOutcomeException("timeout"));
        ambiente.agenda().falharProximaCom(Operacao.AGENDA_DO_DIA, new SchedulingUnavailableException("HTTP 503"));
        ambiente.agenda().falharProximaCom(Operacao.AGENDA_DO_DIA, new SchedulingUnavailableException("HTTP 503"));

        dialogo.cliente("sim")
                .atendenteDiz("A agenda demorou para responder e não consegui confirmar se o horário foi marcado.")
                .estadoE(ConfirmingBooking.class);
        assertThat(((ConfirmingBooking) dialogo.estado()).pendingAttempt().startAt()).isEqualTo(SEXTA_10_30);

        dialogo.cliente("sim")
                .atendenteDiz("Agendado, Gabriel! Seu código é *#1*.")
                .notaE("RECONCILED");
        assertThat(ambiente.agenda().postCount()).isEqualTo(2);
        assertThat(ambiente.agenda().agendamentos()).hasSize(1);
    }

    @Test
    @DisplayName("409 sem tentativa pendente não reconcilia, mesmo com o telefone e o nome de quem marcou")
    void conflitoSemTentativaPendenteNaoDeveReconciliar() {
        noResumo();
        long deOutraPessoa = ambiente.agenda().ocupar(PEDRO, SEXTA_10_30, "Gabriel Pacheco",
                Ambiente.TELEFONE_DO_CLIENTE);

        dialogo.cliente("sim")
                .atendenteDiz("Puxa, alguém acabou de reservar esse horário.")
                .atendenteNaoDiz("Agendado")
                .estadoE(ChoosingSlot.class);

        assertThat(ambiente.agendamentos().exists(deOutraPessoa)).isFalse();
    }

    @Test
    @DisplayName("Resultado incerto não toma posse do agendamento que já é de outro cliente")
    void resultadoIncertoNaoDeveTomarPosseDeAgendamentoAlheio() {
        noResumo();
        Dialogo outraSessao = ambiente.clienteNaWeb(Ambiente.TELEFONE_DO_CLIENTE);
        outraSessao.cliente("corte sexta 10:30 com o pedro").cliente("Gabriel Pacheco").cliente("sim")
                .atendenteDiz("Seu código é *#1*.");
        ambiente.agenda().falharProximaCom(Operacao.AGENDAMENTO, new UnknownBookingOutcomeException("timeout"));

        dialogo.cliente("sim")
                .atendenteNaoDiz("Agendado")
                .atendenteDiz("não consegui confirmar se o horário foi marcado");

        assertThat(ambiente.agendamentos().belongsTo(1, outraSessao.clienteSalvo().id())).isTrue();
        assertThat(ambiente.agendamentos().belongsTo(1, dialogo.clienteSalvo().id())).isFalse();
    }

    @Test
    @DisplayName("Com 3 agendamentos ativos, não começa um quarto")
    void deveRespeitarOLimiteDeAgendamentosAtivos() {
        dialogo.cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim");
        dialogo.cliente("corte amanha 11h com o joao").cliente("sim");
        dialogo.cliente("corte amanha 14h com o joao").cliente("sim");

        dialogo.cliente("quero agendar uma barba")
                .atendenteDiz("Você já tem 3 agendamentos marcados por aqui. Para marcar outro, cancele um deles ou"
                        + " fale com a gente pelo (48) 3333-0000.")
                .estadoE(Idle.class);
        assertThat(ambiente.agenda().postCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Resposta fora do contrato vira um pedido de desculpas e recomeça")
    void respostaForaDoContratoDeveVirarErroGenerico() {
        noResumo();
        ambiente.agenda().falharProximaCom(Operacao.AGENDAMENTO, new SchedulingContractException("HTTP 400"));

        dialogo.cliente("sim")
                .atendenteDiz("Tive um problema aqui do meu lado. Tente de novo em instantes ou fale com a gente pelo"
                        + " (48) 3333-0000.")
                .estadoE(Idle.class);
    }

    @Test
    @DisplayName("Conversa expirada com tentativa pendente confere a agenda antes de recomeçar")
    void conversaExpiradaComTentativaPendenteDeveConferirAAgenda() {
        noResumo();
        ambiente.agenda().gravarEFalharProximoPostCom(new UnknownBookingOutcomeException("timeout"));
        ambiente.agenda().falharProximaCom(Operacao.AGENDA_DO_DIA, new SchedulingUnavailableException("HTTP 503"));
        ambiente.agenda().falharProximaCom(Operacao.AGENDA_DO_DIA, new SchedulingUnavailableException("HTTP 503"));
        dialogo.cliente("sim").estadoE(ConfirmingBooking.class);

        dialogo.avancarRelogio(Duration.ofMinutes(31)).cliente("sim")
                .atendenteDiz("conferi a agenda: seu horário de *Corte masculino* com *Pedro Henrique* na sexta-feira,"
                        + " 16/10, às 10:30 ficou marcado (código *#1*).")
                .notaE("RECONCILED")
                .estadoE(Idle.class);

        assertThat(ambiente.agenda().postCount()).isEqualTo(1);
        assertThat(ambiente.agendamentos().belongsTo(1, dialogo.clienteSalvo().id())).isTrue();
    }
}
