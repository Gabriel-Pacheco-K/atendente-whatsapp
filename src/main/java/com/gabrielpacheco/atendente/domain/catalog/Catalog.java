package com.gabrielpacheco.atendente.domain.catalog;

import java.util.List;
import java.util.Optional;

/** Serviços e profissionais vindos do agendamento-api. Offline quando a agenda nunca respondeu. */
public record Catalog(List<ServiceType> services, List<Professional> professionals, boolean online) {

    public Catalog {
        services = List.copyOf(services);
        professionals = List.copyOf(professionals);
    }

    public static Catalog offline() {
        return new Catalog(List.of(), List.of(), false);
    }

    public Optional<ServiceType> service(long id) {
        return services.stream().filter(service -> service.id() == id).findFirst();
    }

    public Optional<Professional> professional(long id) {
        return professionals.stream().filter(professional -> professional.id() == id).findFirst();
    }
}
