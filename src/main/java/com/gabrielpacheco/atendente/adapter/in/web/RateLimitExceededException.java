package com.gabrielpacheco.atendente.adapter.in.web;

/** Um limite de taxa foi atingido; {@code title} diz qual, e {@code retryAfterSeconds} vai no Retry-After. */
class RateLimitExceededException extends RuntimeException {

    private final String title;
    private final long retryAfterSeconds;

    RateLimitExceededException(String title, long retryAfterSeconds) {
        super(title);
        this.title = title;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    String title() {
        return title;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
