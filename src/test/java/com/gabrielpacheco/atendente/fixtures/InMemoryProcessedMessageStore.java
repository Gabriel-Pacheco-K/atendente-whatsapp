package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessage;
import com.gabrielpacheco.atendente.application.port.out.ProcessedMessageStore;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProcessedMessageStore implements ProcessedMessageStore {

    private record Chave(long customerId, String messageId) {
    }

    private final Map<Chave, ProcessedMessage> processadas = new HashMap<>();
    private final Clock relogio;

    public InMemoryProcessedMessageStore(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public synchronized Optional<ProcessedMessage> find(long customerId, String messageId) {
        return Optional.ofNullable(processadas.get(new Chave(customerId, messageId)));
    }

    @Override
    public synchronized void register(long customerId, String messageId, ConversationReply replyOrNull) {
        processadas.putIfAbsent(new Chave(customerId, messageId), new ProcessedMessage(relogio.instant(), replyOrNull));
    }
}
