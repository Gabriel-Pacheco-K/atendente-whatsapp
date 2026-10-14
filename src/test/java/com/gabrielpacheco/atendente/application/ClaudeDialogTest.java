package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.out.InterpretationUnavailableException;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Intent;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.fixtures.Ambiente;
import com.gabrielpacheco.atendente.fixtures.Dialogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static org.assertj.core.api.Assertions.assertThat;

/** A conversa inteira com o interpretador híbrido, e um Claude falso no lugar do de verdade. */
@DisplayName("Diálogos com o Claude ligado")
class ClaudeDialogTest {

    private static final String GIRIA = "queria dar um trato no visual";
    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);

    private final ClaudeFalso claude = new ClaudeFalso();
    private final Ambiente ambiente = Ambiente.comClaude(claude);
    private final Dialogo dialogo = ambiente.clienteNaWeb();

    @Test
    @DisplayName("O que as regras não entendem segue com o que o Claude entendeu")
    void fraseQueAsRegrasNaoEntendemDeveSeguirComOClaude() {
        claude.entende(new Entities(CORTE.id(), null, false, null, null, null, null, null, null, false, false));

        dialogo.cliente(GIRIA).atendenteDiz("Com quem você prefere?").estadoE(ChoosingProfessional.class);

        assertThat(dialogo.resposta().diagnostics().interpretedBy()).isEqualTo(InterpreterKind.CLAUDE);
        assertThat(claude.textosRecebidos).containsExactly(GIRIA);
    }

    @Test
    @DisplayName("Um Claude lento não tira o tempo da agenda: os horários ainda são consultados")
    void claudeLentoNaoDeveTirarOTempoDaAgenda() {
        claude.demora(Duration.ofSeconds(6));
        claude.entende(new Entities(CORTE.id(), JOAO.id(), false, QUARTA, null, null, null, null, null, false, false));

        dialogo.cliente(GIRIA).atendenteDiz("Horários livres na *quarta-feira, 14/10*").estadoE(ChoosingSlot.class);
    }

    @Test
    @DisplayName("Na confirmação, mesmo uma resposta confusa fica com as regras, e o \"sim\" agenda sem o Claude")
    void confirmacaoNaoDeveChamarOClaude() {
        claude.entende(new Entities(CORTE.id(), JOAO.id(), false, QUARTA,
                null, new TimePreference.Exact(LocalTime.of(15, 0)), null, null, null, false, false));
        dialogo.cliente(GIRIA).cliente("Gabriel Pacheco").estadoE(ConfirmingBooking.class);

        dialogo.cliente("quem sabe, talvez").atendenteDiz("Responda *sim* ou *não*").estadoE(ConfirmingBooking.class);
        dialogo.cliente("sim").atendenteDiz("Agendado, Gabriel!").estadoE(Idle.class);

        assertThat(claude.textosRecebidos).containsExactly(GIRIA);
        assertThat(dialogo.agenda().postCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Se o Claude falhar, o atendente responde com o que as regras entenderam")
    void falhaDoClaudeDeveCairNasRegras() {
        claude.falha();

        dialogo.cliente("kkkkk xpto").atendenteDiz("Desculpe, não entendi.").estadoE(Idle.class);

        assertThat(dialogo.resposta().diagnostics().interpretedBy()).isEqualTo(InterpreterKind.RULES_AFTER_FAILURE);
    }

    /** No lugar do Claude: entende o que o teste mandar, demora o quanto o teste mandar e guarda o que recebeu. */
    private final class ClaudeFalso implements MessageInterpreter {

        private final List<String> textosRecebidos = new ArrayList<>();
        private Entities entidades = Entities.none();
        private Duration demora = Duration.ZERO;
        private boolean falha;

        void entende(Entities entidades) {
            this.entidades = entidades;
        }

        void demora(Duration quanto) {
            this.demora = quanto;
        }

        void falha() {
            this.falha = true;
        }

        @Override
        public Interpretation interpret(String text, InterpretationContext context) {
            textosRecebidos.add(text);
            ambiente.relogio().avancar(demora);
            if (falha) {
                throw new InterpretationUnavailableException("io");
            }
            return new Interpretation(Intent.BOOK, 0.85, entidades, InterpreterKind.CLAUDE);
        }
    }
}
