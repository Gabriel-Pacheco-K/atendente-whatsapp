package com.gabrielpacheco.atendente.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Apaga, de madrugada, o que já não serve: idempotências velhas, respostas guardadas da web, sessões da web
 * abandonadas e clientes do WhatsApp sumidos. Apagar o cliente leva junto a conversa, os agendamentos guardados e as
 * idempotências; o agendamento no agendamento-api continua existindo.
 */
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    /**
     * @param webReplies        por quanto tempo a resposta da web fica para o reenvio idempotente
     * @param whatsappCustomers sem mensagem há mais que isso, e sem agendamento pela frente, o cliente é apagado
     */
    public record Windows(Duration processedMessages, Duration webReplies, Duration webCustomers,
                          Duration whatsappCustomers) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Windows windows;

    public RetentionJob(JdbcClient jdbc, Clock clock, Windows windows) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.windows = windows;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${app.timezone}")
    public void purge() {
        Instant now = clock.instant();
        int messages = jdbc.sql("DELETE FROM processed_messages WHERE processed_at < :limit")
                .param("limit", Timestamps.of(now.minus(windows.processedMessages())))
                .update();
        int replies = jdbc.sql("UPDATE processed_messages SET reply = NULL WHERE reply IS NOT NULL AND processed_at < :limit")
                .param("limit", Timestamps.of(now.minus(windows.webReplies())))
                .update();
        int webCustomers = jdbc.sql("DELETE FROM customers WHERE channel = 'WEB' AND created_at < :limit")
                .param("limit", Timestamps.of(now.minus(windows.webCustomers())))
                .update();
        int whatsappCustomers = jdbc.sql("""
                        DELETE FROM customers c
                        WHERE c.channel = 'WHATSAPP' AND c.last_seen_at < :limit
                          AND NOT EXISTS (SELECT 1 FROM bookings b
                                          WHERE b.customer_id = c.id AND b.start_at > :localNow
                                            AND b.status IN ('SCHEDULED', 'CONFIRMED'))""")
                .param("limit", Timestamps.of(now.minus(windows.whatsappCustomers())))
                .param("localNow", LocalDateTime.ofInstant(now, clock.getZone()))
                .update();
        log.info("Retenção: {} idempotências apagadas, {} respostas descartadas, {} clientes da web e {} do WhatsApp"
                + " apagados", messages, replies, webCustomers, whatsappCustomers);
    }
}
