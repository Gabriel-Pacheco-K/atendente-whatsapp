package com.gabrielpacheco.atendente.domain.catalog;

import java.math.BigDecimal;
import java.util.List;

public record ServiceType(long id, String name, int durationMinutes, BigDecimal price, List<String> aliases) {

    public ServiceType {
        aliases = List.copyOf(aliases);
    }
}
