package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.out.CustomerStore;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryCustomerStore implements CustomerStore {

    private final Map<Long, Customer> clientes = new LinkedHashMap<>();
    private long proximoId = 1;

    @Override
    public synchronized Customer createWeb(String phone) {
        return criar(new CustomerRef(Channel.WEB, UUID.randomUUID().toString(), phone, null));
    }

    @Override
    public synchronized Optional<Customer> findWeb(UUID sessionId) {
        return buscar(Channel.WEB, sessionId.toString());
    }

    @Override
    public synchronized Customer upsert(CustomerRef ref) {
        return buscar(ref.channel(), ref.externalId()).orElseGet(() -> criar(ref));
    }

    @Override
    public synchronized void saveName(long customerId, String name) {
        Customer atual = clientes.get(customerId);
        clientes.put(customerId, new Customer(atual.id(), atual.channel(), atual.externalId(), atual.phone(), name));
    }

    private Optional<Customer> buscar(Channel canal, String externalId) {
        return clientes.values().stream()
                .filter(c -> c.channel() == canal && c.externalId().equals(externalId))
                .findFirst();
    }

    private Customer criar(CustomerRef ref) {
        Customer novo = new Customer(proximoId++, ref.channel(), ref.externalId(), ref.phone(), null);
        clientes.put(novo.id(), novo);
        return novo;
    }
}
