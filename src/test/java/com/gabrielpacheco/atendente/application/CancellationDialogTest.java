package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.out.UnknownBookingOutcomeException;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingBookingToCancel;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingCancellation;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.fixtures.Ambiente;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import com.gabrielpacheco.atendente.fixtures.FakeSchedulingPort.Operacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Diálogos de cancelamento")
class CancellationDialogTest {

    private final Ambiente ambiente = new Ambiente();
    private final Dialogo dialogo = ambiente.clienteNaWeb();

    private void agendarCorteAmanhaAs10() {
        dialogo.cliente("corte amanha 10h com o joao").cliente("Gabriel").cliente("sim").atendenteDiz("Agendado");
    }

    private void agendarBarbaSextaAs11() {
        dialogo.cliente("barba sexta 11h com o pedro").cliente("sim").atendenteDiz("Agendado");
    }

    private BookingStatus statusLocal(long id) {
        return ambiente.agendamentos().doAgendamento(id).status();
    }

    @Test
    @DisplayName("Com um agendamento só, vai direto à confirmação e cancela no \"sim\"")
    void umAgendamentoDeveIrDiretoAConfirmacao() {
        agendarCorteAmanhaAs10();

        dialogo.cliente("cancelar")
                .atendenteDiz("Você quer cancelar *Corte masculino* com *João Batista* na *quarta-feira, 14/10, às 10:00*?"
                        + " Responda *sim* ou *não*.")
                .estadoE(ConfirmingCancellation.class);
        dialogo.cliente("sim").atendenteDiz("Pronto, cancelei. O horário foi liberado.").estadoE(Idle.class);

        assertThat(ambiente.agenda().agendamento(1).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(statusLocal(1)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Com vários, pede a escolha pelo número da lista")
    void variosAgendamentosDevemPedirAEscolha() {
        agendarCorteAmanhaAs10();
        agendarBarbaSextaAs11();

        dialogo.cliente("quero desmarcar")
                .atendenteDiz("Qual agendamento você quer cancelar?\n"
                        + "1. Corte masculino com João Batista, quarta-feira, 14/10, às 10:00\n"
                        + "2. Barba com Pedro Henrique, sexta-feira, 16/10, às 11:00")
                .estadoE(ChoosingBookingToCancel.class);
        dialogo.cliente("2").atendenteDiz("Você quer cancelar *Barba* com *Pedro Henrique*");
        dialogo.cliente("sim").atendenteDiz("Pronto, cancelei.");

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.SCHEDULED);
        assertThat(statusLocal(2)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("\"não\" mantém o horário marcado")
    void naoDeveManterOHorario() {
        agendarCorteAmanhaAs10();

        dialogo.cliente("cancelar").cliente("não").atendenteDiz("Combinado, seu horário continua marcado.");

        assertThat(ambiente.agenda().agendamento(1).status()).isEqualTo(BookingStatus.SCHEDULED);
    }

    @Test
    @DisplayName("O que a barbearia cancelou some da lista e tem o status atualizado")
    void canceladoPelaBarbeariaDeveSumirDaLista() {
        agendarCorteAmanhaAs10();
        agendarBarbaSextaAs11();
        ambiente.agenda().mudarStatus(1, BookingStatus.CANCELLED);

        dialogo.cliente("cancelar")
                .atendenteDiz("Você quer cancelar *Barba* com *Pedro Henrique*")
                .estadoE(ConfirmingCancellation.class);

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Um agendamento que a agenda não encontra vira cancelado localmente")
    void agendamentoQueSumiuDaAgendaDeveVirarCancelado() {
        agendarCorteAmanhaAs10();
        ambiente.agenda().remover(1);

        dialogo.cliente("cancelar").atendenteDiz("Não encontrei agendamentos futuros feitos por aqui.");

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Um status novo da agenda nunca é gravado e o agendamento não é oferecido para cancelar")
    void statusDesconhecidoDeveSerMantidoESairDaLista() {
        agendarCorteAmanhaAs10();
        agendarBarbaSextaAs11();
        ambiente.agenda().mudarStatus(1, BookingStatus.parse("RESCHEDULED"));

        dialogo.cliente("cancelar").atendenteDiz("Você quer cancelar *Barba*");

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.SCHEDULED);
    }

    @Test
    @DisplayName("Fora do prazo, repassa o motivo da agenda e o telefone")
    void foraDoPrazoDeveRepassarOMotivo() {
        dialogo.cliente("corte hoje 15:30 com o joao").cliente("Gabriel").cliente("sim").atendenteDiz("Agendado");

        dialogo.cliente("preciso desmarcar").estadoE(ConfirmingCancellation.class);
        dialogo.cliente("sim")
                .atendenteDiz("Não consegui cancelar: O cancelamento só é permitido até 2h antes do horário. Se precisar,"
                        + " fale com a gente pelo (48) 3333-0000.")
                .estadoE(Idle.class);

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.SCHEDULED);
    }

    @Test
    @DisplayName("Recusa de algo que já estava cancelado diz que já estava cancelado")
    void recusaDeAlgoJaCanceladoDeveDizerQueJaEstava() {
        agendarCorteAmanhaAs10();
        dialogo.cliente("cancelar");
        ambiente.agenda().mudarStatus(1, BookingStatus.CANCELLED);

        dialogo.cliente("sim").atendenteDiz("Esse agendamento já estava cancelado.");

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Timeout no cancelamento que a agenda gravou conta como sucesso")
    void timeoutQueCancelouDeveContarComoSucesso() {
        agendarCorteAmanhaAs10();
        dialogo.cliente("cancelar");
        ambiente.agenda().cancelarEFalharProximoPatchCom(new UnknownBookingOutcomeException("timeout"));

        dialogo.cliente("sim").atendenteDiz("Pronto, cancelei.");

        assertThat(statusLocal(1)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Timeout no cancelamento que não gravou mantém a confirmação")
    void timeoutQueNaoCancelouDeveManterAConfirmacao() {
        agendarCorteAmanhaAs10();
        dialogo.cliente("cancelar");
        ambiente.agenda().falharProximaCom(Operacao.CANCELAMENTO, new UnknownBookingOutcomeException("timeout"));

        dialogo.cliente("sim")
                .atendenteDiz("Nosso sistema de agenda está fora do ar agora.")
                .estadoE(ConfirmingCancellation.class);
        dialogo.cliente("sim").atendenteDiz("Pronto, cancelei.");
    }

    @Test
    @DisplayName("\"remarcar\" cancela e explica como marcar o novo horário")
    void remarcarDeveAcrescentarADica() {
        agendarCorteAmanhaAs10();

        dialogo.cliente("quero remarcar")
                .atendenteDiz("Você quer cancelar *Corte masculino*")
                .atendenteDiz("Depois de cancelar, me diga o novo dia que eu procuro um horário.");
    }

    @Test
    @DisplayName("Lista os próximos agendamentos com o código")
    void deveListarOsProximosAgendamentos() {
        agendarCorteAmanhaAs10();

        dialogo.cliente("meus agendamentos")
                .atendenteDiz("Seus próximos agendamentos:\n- *Corte masculino* com João Batista, quarta-feira, 14/10,"
                        + " às 10:00 (código #1)\nPara cancelar, mande *cancelar*.");
    }

    @Test
    @DisplayName("Não mostra nem cancela o agendamento de outro cliente")
    void naoDeveMostrarAgendamentoDeOutroCliente() {
        agendarCorteAmanhaAs10();
        Dialogo outro = ambiente.clienteNaWeb("+55 48 98888-0002");

        outro.cliente("meus agendamentos").atendenteDiz("Não encontrei agendamentos futuros feitos por aqui.");
        outro.cliente("cancelar 1").atendenteDiz("Não encontrei agendamentos futuros feitos por aqui.");

        assertThat(ambiente.agenda().agendamento(1).status()).isEqualTo(BookingStatus.SCHEDULED);
    }
}
