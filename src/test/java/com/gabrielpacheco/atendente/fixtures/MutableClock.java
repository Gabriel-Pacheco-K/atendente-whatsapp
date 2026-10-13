package com.gabrielpacheco.atendente.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio que o teste controla: começa na terça, 13/10/2026, 14:00 em São Paulo. */
public final class MutableClock extends Clock {

    public static final Instant REFERENCIA = Instant.parse("2026-10-13T17:00:00Z");
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public static MutableClock naReferencia() {
        return new MutableClock(REFERENCIA, FUSO);
    }

    public void ajustar(Instant novoInstante) {
        this.instant = novoInstante;
    }

    public void avancar(Duration duracao) {
        this.instant = instant.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId outroFuso) {
        return new MutableClock(instant, outroFuso);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
