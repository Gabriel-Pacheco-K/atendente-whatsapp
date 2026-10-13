package com.gabrielpacheco.atendente.domain.conversation;

/** {@code name} fica nulo até o cliente dizer como quer ser chamado no agendamento. */
public record Customer(long id, Channel channel, String externalId, String phone, String name) {
}
