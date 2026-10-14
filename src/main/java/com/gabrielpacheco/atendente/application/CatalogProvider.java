package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.reply.PtBr;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Serviços e profissionais do agendamento-api, com os apelidos do business.yml. Só a subida espera a carga: nos
 * turnos, que seguram a trava da conversa, um catálogo vencido ou invalidado continua valendo enquanto outro é
 * buscado em segundo plano, e o antigo também fica se a recarga falha. Sem nenhuma carga bem-sucedida, o catálogo
 * é offline até a primeira recarga dar certo.
 */
public class CatalogProvider {

    private static final Logger log = LoggerFactory.getLogger(CatalogProvider.class);

    private record Snapshot(Catalog catalog, Instant loadedAt, boolean invalidated) {
    }

    private final SchedulingPort scheduling;
    private final BusinessProfile business;
    private final Clock clock;
    private final Duration ttl;
    private final Executor refresher;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public CatalogProvider(SchedulingPort scheduling, BusinessProfile business, Clock clock, Duration ttl,
                           Executor refresher) {
        this.scheduling = scheduling;
        this.business = business;
        this.clock = clock;
        this.ttl = ttl;
        this.refresher = refresher;
    }

    public Catalog current() {
        Snapshot current = snapshot.get();
        boolean stale = current == null || current.invalidated()
                || current.loadedAt().plus(ttl).isBefore(clock.instant());
        if (!stale) {
            return current.catalog();
        }
        refreshInBackground();
        // Uma recarga que já terminou (ou que rodou nesta thread) vale desde já.
        Snapshot latest = snapshot.get();
        return latest == null ? Catalog.offline() : latest.catalog();
    }

    /** Carrega na hora, esperando a agenda. Só para a subida, fora de qualquer turno. */
    public void warmUp() {
        try {
            store(load());
        } catch (SchedulingException e) {
            log.warn("Não consegui carregar o catálogo da agenda na subida ({}); começando no modo offline",
                    e.getClass().getSimpleName());
        }
    }

    /** O catálogo está errado (a agenda não conhece um id dele): a próxima consulta já pede outro. */
    public void invalidate() {
        snapshot.updateAndGet(current -> current == null ? null
                : new Snapshot(current.catalog(), current.loadedAt(), true));
    }

    private void refreshInBackground() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        refresher.execute(() -> {
            try {
                store(load());
            } catch (SchedulingException e) {
                log.warn("Não consegui recarregar o catálogo da agenda ({}); mantendo o {}",
                        e.getClass().getSimpleName(), snapshot.get() == null ? "modo offline" : "anterior");
            } finally {
                refreshing.set(false);
            }
        });
    }

    private Catalog store(Catalog catalog) {
        snapshot.set(new Snapshot(catalog, clock.instant(), false));
        return catalog;
    }

    private Catalog load() {
        List<Professional> professionals = scheduling.professionals();
        Map<String, List<String>> aliases = aliasesByNormalizedName();
        List<ServiceType> services = scheduling.services().stream()
                .map(service -> new ServiceType(service.id(), service.name(), service.durationMinutes(),
                        service.price(), aliases.getOrDefault(normalized(service.name()), List.of())))
                .toList();
        warnAboutUnknownAliases(business.serviceAliases().keySet(), services);
        warnAboutClosedWorkingDays(professionals);
        return new Catalog(services, professionals, true);
    }

    private Map<String, List<String>> aliasesByNormalizedName() {
        return business.serviceAliases().entrySet().stream()
                .collect(Collectors.toMap(entry -> normalized(entry.getKey()), Map.Entry::getValue));
    }

    private static void warnAboutUnknownAliases(Set<String> configured, List<ServiceType> services) {
        Set<String> known = services.stream().map(service -> normalized(service.name())).collect(Collectors.toSet());
        configured.stream()
                .filter(name -> !known.contains(normalized(name)))
                .forEach(name -> log.warn("O business.yml tem apelidos para '{}', mas a agenda não tem esse serviço",
                        name));
    }

    /** O horário de funcionamento fica em dois lugares (business.yml e expediente da agenda) e pode divergir. */
    private void warnAboutClosedWorkingDays(List<Professional> professionals) {
        for (Professional professional : professionals) {
            for (DayOfWeek day : professional.workingDays()) {
                if (!business.opensOn(day)) {
                    log.warn("O profissional #{} trabalha {}, mas o business.yml diz que o estabelecimento não abre",
                            professional.id(), PtBr.weekdayPlural(day, false));
                }
            }
        }
    }

    private static String normalized(String name) {
        return TextNormalizer.normalize(name).text();
    }
}
