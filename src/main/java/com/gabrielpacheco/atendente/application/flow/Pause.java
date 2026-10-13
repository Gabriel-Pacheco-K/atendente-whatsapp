package com.gabrielpacheco.atendente.application.flow;

import java.time.Duration;

/** Espera entre tentativas. Nos testes não espera nada. */
@FunctionalInterface
public interface Pause {

    void pause(Duration duration);
}
