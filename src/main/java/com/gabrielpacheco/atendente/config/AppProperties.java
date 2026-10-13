package com.gabrielpacheco.atendente.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotNull ZoneId timezone,
                            @Valid @NotNull Conversation conversation,
                            @Valid @NotNull Booking booking,
                            @Valid @NotNull SchedulingApi schedulingApi) {

    /**
     * @param turnBudget tempo máximo de um turno com a conversa travada; precisa ser maior que o read timeout da
     *                   agenda, ou nenhuma consulta caberia nele
     */
    public record Conversation(@NotNull Duration expiresAfter,
                               @NotNull Duration duplicateConfirmationWindow,
                               @Min(2) @Max(6) int maxSlotOptions,
                               @PositiveOrZero int nextDaySearchDays,
                               @NotNull Duration turnBudget) {
    }

    /** @param minAdvance a antecedência mínima que o agendamento-api exige (lá, app.booking.min-advance) */
    public record Booking(@Positive int daysAhead,
                          @Positive int maxActivePerCustomer,
                          @NotNull Duration minAdvance) {
    }

    public record SchedulingApi(@NotBlank String baseUrl,
                                @NotNull Duration connectTimeout,
                                @NotNull Duration readTimeout,
                                @NotNull Duration catalogTtl,
                                @NotNull Duration reconciliationRetryDelay,
                                @Positive int maxCallsPerTurn) {
    }
}
