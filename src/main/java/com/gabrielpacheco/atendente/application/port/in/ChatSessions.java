package com.gabrielpacheco.atendente.application.port.in;

import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;

import java.util.Optional;
import java.util.UUID;

/** As sessões do chat web: a identidade do cliente é um UUID emitido pelo servidor. */
public interface ChatSessions {

    ChatSession start(String phone);

    Optional<CustomerRef> find(UUID sessionId);
}
