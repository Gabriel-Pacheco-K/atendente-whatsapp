package com.gabrielpacheco.atendente.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login do painel do dono com uma senha só, vinda de {@code PAINEL_SENHA}. A sessão é um token aleatório guardado em
 * memória: reiniciar o atendente pede login de novo, o que é aceitável para um painel de uma pessoa.
 */
class PainelSessions {

    static final Duration LIFETIME = Duration.ofHours(12);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] passwordDigest;
    private final Clock clock;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    PainelSessions(String password, Clock clock) {
        this.passwordDigest = sha256(password);
        this.clock = clock;
    }

    /** Compara em tempo constante: comparar os resumos evita vazar o tamanho da senha. */
    boolean passwordMatches(String attempt) {
        return attempt != null && MessageDigest.isEqual(passwordDigest, sha256(attempt));
    }

    String open() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, clock.instant().plus(LIFETIME));
        sessions.entrySet().removeIf(entry -> entry.getValue().isBefore(clock.instant()));
        return token;
    }

    boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        Instant expiresAt = sessions.get(token);
        if (expiresAt == null || expiresAt.isBefore(clock.instant())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    void close(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
