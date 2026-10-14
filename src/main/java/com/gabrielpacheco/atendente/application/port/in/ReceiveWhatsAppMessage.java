package com.gabrielpacheco.atendente.application.port.in;

/** Mensagens que chegam pelo webhook do WhatsApp. As respostas saem pelo envio do canal, depois do commit. */
public interface ReceiveWhatsAppMessage {

    /**
     * Processa a mensagem e envia as respostas. Uma mensagem que falha por bug recebe um pedido de desculpas e fica
     * marcada como processada, para a Meta não reenviá-la por dias.
     *
     * @throws RetryLaterException se uma falha passageira (banco fora, conversa travada) impediu o processamento
     */
    void receive(IncomingMessage message);

    /** Avisa quem passou do limite de mensagens; quem chama garante que é uma vez só por janela. */
    void warnRateLimited(String waId);
}
