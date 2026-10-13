package com.gabrielpacheco.atendente.application.port.in;

/** Processa uma mensagem de cliente, de qualquer canal, e devolve o que responder. */
public interface HandleIncomingMessage {

    /** Um {@code messageId} já processado para o mesmo cliente devolve a resposta guardada, sem efeito novo. */
    ConversationReply handle(IncomingMessage message);
}
