package com.gabrielpacheco.atendente.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O que entrou num dia: só os atendimentos concluídos contam. */
public record DailyRevenue(LocalDate date, int completed, BigDecimal revenue) {
}
