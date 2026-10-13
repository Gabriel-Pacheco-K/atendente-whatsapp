package com.gabrielpacheco.atendente.adapter.out.persistence;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import org.springframework.stereotype.Component;

/** A resposta de um turno da web em JSON, guardada para devolver o mesmo corpo num reenvio. */
@Component
class ReplyJson {

    private final ObjectMapper mapper;

    ReplyJson(ObjectMapper springMapper) {
        this.mapper = springMapper.copy();
    }

    String write(ConversationReply reply) {
        try {
            return mapper.writeValueAsString(reply);
        } catch (JacksonException e) {
            throw new IllegalStateException("Não consegui gravar a resposta do turno", e);
        }
    }

    ConversationReply read(String json) {
        try {
            return mapper.readValue(json, ConversationReply.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Resposta guardada ilegível", e);
        }
    }
}
