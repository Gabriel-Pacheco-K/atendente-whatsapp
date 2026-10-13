package com.gabrielpacheco.atendente.domain.conversation;

import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("Invariantes dos estados da conversa")
class ConversationStateInvariantsTest {

    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    private static final SlotOption SLOT = new SlotOption(QUARTA.atTime(15, 0), 1, "João Batista");
    private static final BookingDraft SEM_HORARIO = new BookingDraft(1L, 1L, false, QUARTA, null, null);
    private static final BookingDraft COM_HORARIO = SEM_HORARIO.withChosen(SLOT);
    private static final BookingRef REF = new BookingRef(7, "Corte masculino", "João Batista", QUARTA.atTime(15, 0));

    @Test
    @DisplayName("A lista de horários tem de 1 a 6 opções")
    void listaDeHorariosDeveTerDeUmASeisOpcoes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChoosingSlot(SEM_HORARIO, QUARTA, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChoosingSlot(SEM_HORARIO, QUARTA, slots(7)));
        assertThatNoException().isThrownBy(() -> new ChoosingSlot(SEM_HORARIO, QUARTA, slots(6)));
    }

    @Test
    @DisplayName("O nome e a confirmação exigem um horário escolhido")
    void nomeEConfirmacaoDevemExigirHorarioEscolhido() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AskingName(SEM_HORARIO, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConfirmingBooking(SEM_HORARIO, null));
        assertThatNoException().isThrownBy(() -> new AskingName(COM_HORARIO, "João"));
    }

    @Test
    @DisplayName("A escolha do que cancelar mostra de 2 a 5 agendamentos")
    void escolhaDoCancelamentoDeveTerDeDoisACinco() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChoosingBookingToCancel(List.of(REF)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChoosingBookingToCancel(List.of(REF, REF, REF, REF, REF, REF)));
        assertThatNoException().isThrownBy(() -> new ChoosingBookingToCancel(List.of(REF, REF)));
    }

    @Test
    @DisplayName("A escolha do serviço mostra de 1 a 10, mesmo que o catálogo tenha mais")
    void escolhaDoServicoDeveTerDeUmADez() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChoosingService(BookingDraft.EMPTY, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChoosingService(BookingDraft.EMPTY, ids(11)));
        assertThatNoException().isThrownBy(() -> new ChoosingService(BookingDraft.EMPTY, ids(10)));
    }

    @Test
    @DisplayName("A escolha do profissional tem pelo menos um e deixa a última opção para o \"tanto faz\"")
    void escolhaDoProfissionalDeveReservarOTantoFaz() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChoosingProfessional(BookingDraft.EMPTY, List.of()));
        assertThat(new ChoosingProfessional(BookingDraft.EMPTY, List.of(1L, 2L)).anyProfessionalOption()).isEqualTo(3);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("estados")
    @DisplayName("Cada estado tem o seu código e o que ele espera do cliente")
    void cadaEstadoDeveTerCodigoEExpectativa(ConversationState estado, String codigo, Expectation expectativa) {
        assertThat(estado.code()).isEqualTo(codigo);
        assertThat(estado.expectation()).isEqualTo(expectativa);
    }

    static Stream<Arguments> estados() {
        return Stream.of(
                Arguments.of(Idle.EMPTY, "IDLE", Expectation.NONE),
                Arguments.of(new ChoosingService(BookingDraft.EMPTY, ids(4)), "CHOOSING_SERVICE", Expectation.OPTION),
                Arguments.of(new ChoosingProfessional(SEM_HORARIO, ids(2)), "CHOOSING_PROFESSIONAL", Expectation.OPTION),
                Arguments.of(new ChoosingDate(SEM_HORARIO), "CHOOSING_DATE", Expectation.DATE),
                Arguments.of(new ChoosingSlot(SEM_HORARIO, QUARTA, slots(3)), "CHOOSING_SLOT", Expectation.OPTION),
                Arguments.of(new AskingName(COM_HORARIO, null), "ASKING_NAME", Expectation.NAME),
                Arguments.of(new ConfirmingBooking(COM_HORARIO, null), "CONFIRMING_BOOKING", Expectation.YES_NO),
                Arguments.of(new ChoosingBookingToCancel(List.of(REF, REF)), "CHOOSING_BOOKING_TO_CANCEL",
                        Expectation.OPTION),
                Arguments.of(new ConfirmingCancellation(REF), "CONFIRMING_CANCELLATION", Expectation.YES_NO));
    }

    @Test
    @DisplayName("A conversa só expira no meio de um passo, nunca no início")
    void conversaDeveExpirarSoNoMeioDeUmPasso() {
        Instant antes = Instant.parse("2026-10-13T17:00:00Z");
        Instant depois = antes.plus(Duration.ofMinutes(31));
        Duration validade = Duration.ofMinutes(30);

        assertThat(new Conversation(1, Idle.EMPTY, 0, antes).isExpired(depois, validade)).isFalse();
        assertThat(new Conversation(1, new ChoosingDate(SEM_HORARIO), 0, antes).isExpired(depois, validade)).isTrue();
        assertThat(new Conversation(1, new ChoosingDate(SEM_HORARIO), 0, antes)
                .isExpired(antes.plus(validade), validade)).isFalse();
    }

    @Test
    @DisplayName("O contador de mensagens não entendidas sobe e zera")
    void contadorDeveSubirEZerar() {
        Conversation conversa = new Conversation(1, Idle.EMPTY, 0, Instant.EPOCH);

        assertThat(conversa.registerMisunderstanding()).isEqualTo(1);
        assertThat(conversa.registerMisunderstanding()).isEqualTo(2);
        conversa.clearMisunderstandings();
        assertThat(conversa.misunderstandings()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> new Conversation(1, Idle.EMPTY, -1, Instant.EPOCH));
    }

    private static List<SlotOption> slots(int quantidade) {
        LocalDateTime inicio = QUARTA.atTime(9, 0);
        return LongStream.range(0, quantidade)
                .mapToObj(i -> new SlotOption(inicio.plusMinutes(30 * i), 1, "João Batista"))
                .toList();
    }

    private static List<Long> ids(int quantidade) {
        return LongStream.rangeClosed(1, quantidade).boxed().toList();
    }
}
