package com.gabrielpacheco.atendente.domain.nlu;

import java.time.LocalTime;

public sealed interface TimePreference permits TimePreference.Exact, TimePreference.Period {

    record Exact(LocalTime time) implements TimePreference {
    }

    record Period(PartOfDay part) implements TimePreference {
    }
}
