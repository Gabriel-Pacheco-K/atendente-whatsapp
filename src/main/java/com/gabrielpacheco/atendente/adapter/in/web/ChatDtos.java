package com.gabrielpacheco.atendente.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gabrielpacheco.atendente.application.port.in.Diagnostics;
import com.gabrielpacheco.atendente.domain.conversation.PhoneNumber;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** O JSON da API do chat. */
final class ChatDtos {

    private ChatDtos() {
    }

    record CreateSessionRequest(
            @NotBlank(message = "é obrigatório")
            @Size(max = PhoneNumber.MAX_LENGTH, message = "deve ter no máximo {max} caracteres")
            @Pattern(regexp = PhoneNumber.PATTERN, message = "telefone inválido") String phone) {
    }

    record CreateSessionResponse(UUID sessionId, String businessName, Reply welcome, List<String> suggestions) {
    }

    /** {@code clientMessageId} é gerado pela página e repetido nas retentativas: é ele que evita processar duas vezes. */
    record SendMessageRequest(
            @NotNull(message = "é obrigatório") UUID clientMessageId,
            @NotBlank(message = "é obrigatório")
            @Size(max = 1000, message = "deve ter no máximo {max} caracteres") String text) {
    }

    /** {@code debug} são os bastidores do turno, só quando ligados em {@code app.web.show-diagnostics}. */
    record SendMessageResponse(List<Reply> replies, @JsonInclude(JsonInclude.Include.NON_NULL) Diagnostics debug) {
    }

    record BusinessResponse(String name, String address, String openingHours, String interpreter) {
    }
}
