package com.gabrielpacheco.atendente.adapter.in.web;

import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;

/** Os limites do chat: mensagens por sessão e por IP, e sessões novas por IP. */
public record ChatRateLimits(RateLimiter messagesPerSession, RateLimiter requestsPerIp, RateLimiter sessionsPerIp) {

    public void reset() {
        messagesPerSession.reset();
        requestsPerIp.reset();
        sessionsPerIp.reset();
    }
}
