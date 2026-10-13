package com.gabrielpacheco.atendente.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/** As propriedades {@code app.*}. Os records que guardam segredos nunca mostram o valor no {@code toString}. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotNull ZoneId timezone,
                            @Valid @NotNull Conversation conversation,
                            @Valid @NotNull Booking booking,
                            @Valid @NotNull SchedulingApi schedulingApi,
                            @Valid @NotNull Web web,
                            @Valid @NotNull WhatsApp whatsapp,
                            @Valid @NotNull Ai ai,
                            @Valid @NotNull Retention retention) {

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

    public record Web(boolean enabled,
                      boolean showDiagnostics,
                      @Positive int messagesPerMinute,
                      @Positive int requestsPerMinutePerIp,
                      @Positive int sessionsPerHourPerIp,
                      @NotNull DataSize maxBody) {
    }

    public record WhatsApp(boolean enabled,
                           String verifyToken,
                           String appSecret,
                           String phoneNumberId,
                           @Positive int messagesPerMinute,
                           @NotNull Duration maxMessageAge,
                           @Positive int maxMessagesPerPayload,
                           @NotNull DataSize maxBody,
                           @Valid @NotNull Sending sending) {

        @Override
        public String toString() {
            return "WhatsApp[enabled=" + enabled + ", verifyToken=" + mask(verifyToken) + ", appSecret="
                    + mask(appSecret) + ", phoneNumberId=" + phoneNumberId + ", messagesPerMinute=" + messagesPerMinute
                    + ", maxMessageAge=" + maxMessageAge + ", maxMessagesPerPayload=" + maxMessagesPerPayload
                    + ", maxBody=" + maxBody + ", sending=" + sending + "]";
        }
    }

    public record Sending(boolean enabled,
                          String accessToken,
                          @NotBlank String graphUrl,
                          @NotBlank String graphApiVersion,
                          @NotNull Duration connectTimeout,
                          @NotNull Duration readTimeout) {

        @Override
        public String toString() {
            return "Sending[enabled=" + enabled + ", accessToken=" + mask(accessToken) + ", graphUrl=" + graphUrl
                    + ", graphApiVersion=" + graphApiVersion + ", connectTimeout=" + connectTimeout
                    + ", readTimeout=" + readTimeout + "]";
        }
    }

    public record Ai(@Valid @NotNull Claude claude) {
    }

    public record Claude(boolean enabled,
                         String apiKey,
                         @NotBlank String model,
                         @NotBlank String effort,
                         @Positive int maxTokens,
                         @NotNull Duration timeout,
                         String baseUrl,
                         @DecimalMin("0") @DecimalMax("1") double rulesConfidenceThreshold) {

        @Override
        public String toString() {
            return "Claude[enabled=" + enabled + ", apiKey=" + mask(apiKey) + ", model=" + model + ", effort="
                    + effort + ", maxTokens=" + maxTokens + ", timeout=" + timeout + ", baseUrl=" + baseUrl
                    + ", rulesConfidenceThreshold=" + rulesConfidenceThreshold + "]";
        }
    }

    /**
     * @param webReplies        por quanto tempo a resposta da web fica guardada para o reenvio idempotente
     * @param whatsappCustomers cliente do WhatsApp sem mensagens há mais que isso, e sem agendamento futuro, é apagado
     */
    public record Retention(boolean enabled,
                            @NotNull Duration processedMessages,
                            @NotNull Duration webReplies,
                            @NotNull Duration webCustomers,
                            @NotNull Duration whatsappCustomers) {
    }

    private static String mask(String secret) {
        return secret == null || secret.isBlank() ? "(vazio)" : "***";
    }
}
