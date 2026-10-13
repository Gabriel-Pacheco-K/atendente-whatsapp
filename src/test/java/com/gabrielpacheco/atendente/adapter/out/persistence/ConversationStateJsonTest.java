package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.gabrielpacheco.atendente.domain.booking.BookingDraft;
import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.booking.LastBooking;
import com.gabrielpacheco.atendente.domain.booking.PendingAttempt;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.conversation.AskingName;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingBookingToCancel;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingDate;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingProfessional;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingService;
import com.gabrielpacheco.atendente.domain.conversation.ChoosingSlot;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingBooking;
import com.gabrielpacheco.atendente.domain.conversation.ConfirmingCancellation;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Idle;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Estado da conversa em JSON")
class ConversationStateJsonTest {

    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);
    private static final SlotOption SEXTA_10_30 = new SlotOption(SEXTA.atTime(10, 30), 2, "Pedro Henrique");
    private static final BookingDraft RASCUNHO =
            new BookingDraft(1L, 2L, false, SEXTA, new TimePreference.Exact(LocalTime.of(10, 0)), null);
    private static final BookingRef REF = new BookingRef(7, "Corte masculino", "Pedro Henrique", SEXTA.atTime(10, 30));
    private static final Instant AGORA = Instant.parse("2026-10-13T17:00:00Z");

    private final ConversationStateJson json = new ConversationStateJson(Jackson2ObjectMapperBuilder.json().build());

    static Stream<ConversationState> estados() {
        BookingDraft tantoFazDeManha = new BookingDraft(2L, null, true, SEXTA,
                new TimePreference.Period(PartOfDay.MORNING), null);
        return Stream.of(
                Idle.EMPTY,
                new Idle(new LastBooking(REF, AGORA)),
                new ChoosingService(BookingDraft.EMPTY, List.of(1L, 2L, 3L, 4L)),
                new ChoosingProfessional(tantoFazDeManha, List.of(1L, 2L)),
                new ChoosingDate(RASCUNHO),
                new ChoosingSlot(RASCUNHO, SEXTA, List.of(SEXTA_10_30)),
                new AskingName(RASCUNHO.withChosen(SEXTA_10_30), "João"),
                new ConfirmingBooking(RASCUNHO.withChosen(SEXTA_10_30),
                        new PendingAttempt(2, SEXTA_10_30.startAt(), AGORA)),
                new ChoosingBookingToCancel(List.of(REF, REF)),
                new ConfirmingCancellation(REF));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("estados")
    @DisplayName("Cada estado faz a ida e a volta sem perder nada")
    void cadaEstadoDeveFazerIdaEVolta(ConversationState estado) {
        assertThat(json.read(json.write(estado))).isEqualTo(estado);
    }

    @Test
    @DisplayName("O JSON gravado é legível e leva o tipo em \"type\"")
    void jsonGravadoDeveSerLegivel() {
        ChoosingSlot estado = new ChoosingSlot(RASCUNHO, SEXTA, List.of(SEXTA_10_30));

        assertThat(json.write(estado)).isEqualTo("""
                {"type":"CHOOSING_SLOT","draft":{"serviceId":1,"professionalId":2,"anyProfessional":false,\
                "date":"2026-10-16","time":{"type":"EXACT","time":"10:00"},"chosen":null},"date":"2026-10-16",\
                "options":[{"startAt":"2026-10-16T10:30","professionalId":2,"professionalName":"Pedro Henrique"}]}""");
    }

    @Test
    @DisplayName("Todo estado da sealed interface está registrado no mapeamento")
    void todoEstadoDeveEstarRegistrado() {
        JsonSubTypes registrados = ConversationStateJson.ConversationStateMixin.class.getAnnotation(JsonSubTypes.class);

        assertThat(Arrays.stream(registrados.value()).map(JsonSubTypes.Type::value))
                .containsExactlyInAnyOrder(ConversationState.class.getPermittedSubclasses());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{nao e json",
            "{\"type\":\"ESTADO_QUE_NAO_EXISTE\"}",
            "{\"type\":\"CHOOSING_SLOT\",\"draft\":{},\"date\":\"2026-10-16\",\"options\":[]}"})
    @DisplayName("JSON corrompido, de outra versão ou com estado inválido recomeça a conversa")
    void jsonIlegivelDeveVirarIdle(String ilegivel) {
        assertThat(json.read(ilegivel)).isEqualTo(Idle.EMPTY);
    }
}
