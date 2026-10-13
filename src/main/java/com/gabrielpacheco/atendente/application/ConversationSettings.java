package com.gabrielpacheco.atendente.application;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Os parâmetros da conversa e da agenda.
 *
 * @param expiresAfter                conversa parada no meio de um passo por mais que isso recomeça
 * @param duplicateConfirmationWindow por quanto tempo um "sim" repetido recebe "já está confirmado"
 * @param nextDaySearchDays           quantos dias depois do pedido procurar quando ele não tem horário
 * @param daysAhead                   até quantos dias à frente oferecer horários
 * @param turnBudget                  tempo máximo de um turno; nenhuma chamada à agenda começa sem caber nele
 * @param schedulingCallTimeout       o quanto uma chamada à agenda pode levar no pior caso (o read timeout)
 * @param maxCallsPerTurn             teto de consultas de disponibilidade num turno
 * @param minAdvance                  antecedência mínima que a agenda exige para marcar
 */
public record ConversationSettings(ZoneId zone, Duration expiresAfter, Duration duplicateConfirmationWindow,
                                   int maxSlotOptions, int nextDaySearchDays, int daysAhead, int maxActivePerCustomer,
                                   Duration reconciliationRetryDelay, Duration turnBudget,
                                   Duration schedulingCallTimeout, int maxCallsPerTurn, Duration minAdvance) {
}
