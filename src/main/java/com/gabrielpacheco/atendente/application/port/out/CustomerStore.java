package com.gabrielpacheco.atendente.application.port.out;

import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;

import java.util.Optional;
import java.util.UUID;

public interface CustomerStore {

    /** Cria um cliente da web com um UUID novo como identidade. */
    Customer createWeb(String phone);

    Optional<Customer> findWeb(UUID sessionId);

    /** Cria o cliente na primeira mensagem e o devolve nas seguintes, mesmo com mensagens simultâneas. */
    Customer upsert(CustomerRef ref);

    void saveName(long customerId, String name);
}
