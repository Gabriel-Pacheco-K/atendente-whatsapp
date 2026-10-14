package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingCancellation;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sim e não nas confirmações: só um sim de verdade agenda ou cancela")
class YesNoAnswersDialogTest {

    private final Dialogo dialogo = Dialogo.naWeb();

    @Nested
    @DisplayName("No resumo do agendamento")
    class NoResumo {

        private Dialogo noResumo() {
            return dialogo.cliente("corte amanha 15h com o joao").cliente("Gabriel Pacheco")
                    .estadoE(ConfirmingBooking.class);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"pode cancelar", "pode desmarcar", "ok, esquece", "pode deixar",
                "pode ser outro horario?", "pode ser outro dia?", "ok mas quero mudar o horario",
                "ok, me manda o menu"})
        @DisplayName("Desistir, pedir outra coisa ou mudar não agenda, mesmo com \"pode\" ou \"ok\" na frase")
        void desistenciaNaoDeveAgendar(String fala) {
            noResumo().cliente(fala).atendenteNaoDiz("Agendado");

            assertThat(dialogo.agenda().postCount()).isZero();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"sim", "pode ser", "sim, pode marcar", "ok, valeu", "sim, amanha as 15h"})
        @DisplayName("Um sim, sozinho ou com o que vai junto dele, agenda")
        void simDeveAgendar(String fala) {
            noResumo().cliente(fala).atendenteDiz("Agendado, Gabriel!");

            assertThat(dialogo.agenda().postCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Uma pergunta junto do \"ok\" é respondida, e o resumo se repete")
        void perguntaJuntoDoOkDeveSerRespondida() {
            noResumo().cliente("ok, aceita pix?")
                    .atendenteDiz("Aceitamos Pix")
                    .atendenteDiz("Voltando ao seu agendamento: confirma o agendamento?")
                    .estadoE(ConfirmingBooking.class);
        }
    }

    @Nested
    @DisplayName("Na confirmação do cancelamento")
    class NoCancelamento {

        private Dialogo naConfirmacao() {
            dialogo.cliente("corte amanha 15h com o joao").cliente("Gabriel").cliente("sim").atendenteDiz("Agendado");
            return dialogo.avancarRelogio(Duration.ofMinutes(3)).cliente("cancelar")
                    .estadoE(ConfirmingCancellation.class);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"pode manter", "pode deixar", "ok, deixa quieto", "nao cancela", "nao quero cancelar"})
        @DisplayName("Manter, deixar ou um \"não\" com \"cancela\" não cancelam")
        void manterNaoDeveCancelar(String fala) {
            naConfirmacao().cliente(fala).atendenteNaoDiz("cancelei");

            assertThat(dialogo.agenda().agendamento(1).status()).isEqualTo(BookingStatus.SCHEDULED);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"sim", "cancela", "cancelar", "quero cancelar", "sim, pode cancelar", "pode desmarcar"})
        @DisplayName("\"cancela\" em resposta a \"você quer cancelar?\" também é um sim")
        void cancelaDeveCancelar(String fala) {
            naConfirmacao().cliente(fala).atendenteDiz("Pronto, cancelei.").estadoE(Idle.class);

            assertThat(dialogo.agenda().agendamento(1).status()).isEqualTo(BookingStatus.CANCELLED);
        }
    }
}
