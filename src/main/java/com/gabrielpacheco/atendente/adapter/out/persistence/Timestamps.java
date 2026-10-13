package com.gabrielpacheco.atendente.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Instantes para as colunas TIMESTAMPTZ pelo JDBC, sem depender do fuso da JVM. */
final class Timestamps {

    private Timestamps() {
    }

    static OffsetDateTime of(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
