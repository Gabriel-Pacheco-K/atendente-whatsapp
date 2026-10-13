package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.in.ChatSession;
import com.gabrielpacheco.atendente.application.port.in.ChatSessions;
import com.gabrielpacheco.atendente.application.port.in.InvalidPhoneException;
import com.gabrielpacheco.atendente.application.port.out.CustomerStore;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import com.gabrielpacheco.atendente.domain.conversation.PhoneNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Sessões do chat web. A identidade é o UUID que o servidor sorteia, nunca o telefone, que é digitado e não é
 * verificado: duas sessões com o mesmo telefone são dois clientes diferentes.
 */
@Service
public class ChatSessionService implements ChatSessions {

    private final CustomerStore customers;

    public ChatSessionService(CustomerStore customers) {
        this.customers = customers;
    }

    @Override
    @Transactional
    public ChatSession start(String phone) {
        if (!PhoneNumber.isValid(phone)) {
            throw new InvalidPhoneException();
        }
        return new ChatSession(UUID.fromString(customers.createWeb(phone).externalId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerRef> find(UUID sessionId) {
        return customers.findWeb(sessionId)
                .map(customer -> new CustomerRef(Channel.WEB, customer.externalId(), customer.phone(), null));
    }
}
