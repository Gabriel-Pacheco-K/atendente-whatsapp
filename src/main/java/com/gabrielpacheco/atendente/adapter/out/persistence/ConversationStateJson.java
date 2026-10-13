package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
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
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Grava o estado da conversa como JSON, com o tipo no campo "type". O domínio não conhece o Jackson: o mapeamento
 * fica nos mixins abaixo. Um JSON que não dá para ler (corrompido ou de uma versão antiga) recomeça a conversa.
 */
@Component
public class ConversationStateJson {

    private static final Logger log = LoggerFactory.getLogger(ConversationStateJson.class);

    private final ObjectMapper mapper;

    public ConversationStateJson(ObjectMapper springMapper) {
        this.mapper = springMapper.copy()
                .registerModule(new JavaTimeModule())
                .registerModule(minutePrecisionTimes())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addMixIn(ConversationState.class, ConversationStateMixin.class)
                .addMixIn(TimePreference.class, TimePreferenceMixin.class);
    }

    public String write(ConversationState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JacksonException e) {
            throw new IllegalStateException("Não consegui gravar o estado " + state.code(), e);
        }
    }

    public ConversationState read(String json) {
        try {
            return mapper.readValue(json, ConversationState.class);
        } catch (JacksonException e) {
            log.warn("Estado da conversa ilegível ({}); recomeçando do início", e.getClass().getSimpleName());
            return Idle.EMPTY;
        }
    }

    /** Horários da agenda nunca têm segundos: "10:30" e "2026-10-16T10:30" deixam o JSON mais fácil de ler. */
    private static SimpleModule minutePrecisionTimes() {
        SimpleModule module = new SimpleModule("horarios-sem-segundos");
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
        module.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        return module;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Idle.class, name = "IDLE"),
            @JsonSubTypes.Type(value = ChoosingService.class, name = "CHOOSING_SERVICE"),
            @JsonSubTypes.Type(value = ChoosingProfessional.class, name = "CHOOSING_PROFESSIONAL"),
            @JsonSubTypes.Type(value = ChoosingDate.class, name = "CHOOSING_DATE"),
            @JsonSubTypes.Type(value = ChoosingSlot.class, name = "CHOOSING_SLOT"),
            @JsonSubTypes.Type(value = AskingName.class, name = "ASKING_NAME"),
            @JsonSubTypes.Type(value = ConfirmingBooking.class, name = "CONFIRMING_BOOKING"),
            @JsonSubTypes.Type(value = ChoosingBookingToCancel.class, name = "CHOOSING_BOOKING_TO_CANCEL"),
            @JsonSubTypes.Type(value = ConfirmingCancellation.class, name = "CONFIRMING_CANCELLATION")})
    interface ConversationStateMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TimePreference.Exact.class, name = "EXACT"),
            @JsonSubTypes.Type(value = TimePreference.Period.class, name = "PERIOD")})
    interface TimePreferenceMixin {
    }
}
