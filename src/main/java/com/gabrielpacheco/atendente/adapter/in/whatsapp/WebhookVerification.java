package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A verificação que o painel da Meta faz ao cadastrar o webhook: o challenge só é devolvido se o modo for
 * "subscribe" e o token bater. A comparação leva o mesmo tempo acerte ou erre. Como o challenge volta no corpo da
 * resposta, ele precisa ser um token simples: nunca HTML ou script refletido.
 */
public class WebhookVerification {

    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final byte[] verifyToken;

    public WebhookVerification(String verifyToken) {
        this.verifyToken = verifyToken.getBytes(StandardCharsets.UTF_8);
    }

    public Optional<String> challengeToEcho(String mode, String token, String challenge) {
        boolean accepted = "subscribe".equals(mode)
                && token != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), verifyToken)
                && challenge != null && CHALLENGE.matcher(challenge).matches();
        return accepted ? Optional.of(challenge) : Optional.empty();
    }
}
