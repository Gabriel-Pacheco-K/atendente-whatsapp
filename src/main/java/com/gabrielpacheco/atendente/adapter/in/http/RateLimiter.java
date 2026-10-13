package com.gabrielpacheco.atendente.adapter.in.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limite de pedidos por chave (sessão, IP, número de WhatsApp) em janelas fixas. Vale por instância, em memória.
 * As janelas ficam em ordem de abertura: a cada pedido, as vencidas saem do começo, e acima de
 * {@value #MAX_KEYS} chaves a mais antiga sai, para um flood de IPs não esgotar a memória.
 */
public class RateLimiter {

    static final int MAX_KEYS = 10_000;

    public enum Outcome {
        ALLOWED,
        /** Primeira recusa da janela: o momento de avisar o cliente uma vez. */
        DENIED_NOTIFY,
        DENIED
    }

    public record Decision(Outcome outcome, Duration retryAfter) {

        public boolean allowed() {
            return outcome == Outcome.ALLOWED;
        }

        /** Para o cabeçalho Retry-After: segundos inteiros, arredondados para cima. */
        public long retryAfterSeconds() {
            return Math.max(1, retryAfter.plusMillis(999).toSeconds());
        }
    }

    private static final class Window {

        private final Instant start;
        private int count;
        private boolean notified;

        private Window(Instant start) {
            this.start = start;
        }
    }

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>();

    public RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    public synchronized Decision tryAcquire(String key) {
        Instant now = clock.instant();
        evictExpired(now);
        Window current = windows.get(key);
        if (current == null) {
            current = openWindow(key, now);
        }
        if (current.count < limit) {
            current.count++;
            return new Decision(Outcome.ALLOWED, Duration.ZERO);
        }
        Duration retryAfter = Duration.between(now, current.start.plus(window));
        Outcome outcome = current.notified ? Outcome.DENIED : Outcome.DENIED_NOTIFY;
        current.notified = true;
        return new Decision(outcome, retryAfter);
    }

    public synchronized void reset() {
        windows.clear();
    }

    synchronized int trackedKeys() {
        return windows.size();
    }

    private Window openWindow(String key, Instant now) {
        if (windows.size() >= MAX_KEYS) {
            Iterator<Window> oldest = windows.values().iterator();
            oldest.next();
            oldest.remove();
        }
        Window opened = new Window(now);
        windows.put(key, opened);
        return opened;
    }

    private void evictExpired(Instant now) {
        Iterator<Window> open = windows.values().iterator();
        while (open.hasNext() && !now.isBefore(open.next().start.plus(window))) {
            open.remove();
        }
    }
}
