package com.gabrielpacheco.atendente.domain.business;

import java.time.LocalDate;

public record ClosedDate(LocalDate date, String reason) {
}
