package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Confere o cabeçalho {@code X-Hub-Signature-256} da Meta: HMAC-SHA256 do corpo com o app secret. O cálculo é sobre
 * os bytes exatamente como chegaram: o mesmo JSON reserializado (outra ordem de campos, acento escrito como escape
 * unicode ou não) teria outra assinatura. A comparação leva o mesmo tempo acerte ou erre, para não vazar a
 * assinatura byte a byte.
 */
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";
    private static final int HEADER_LENGTH = PREFIX.length() + 64;

    private final SecretKeySpec key;

    public WebhookSignatureVerifier(String appSecret) {
        this.key = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public boolean isValid(byte[] rawBody, String header) {
        if (header == null || header.length() != HEADER_LENGTH || !header.startsWith(PREFIX)) {
            return false;
        }
        byte[] received;
        try {
            received = HexFormat.of().parseHex(header, PREFIX.length(), HEADER_LENGTH);
        } catch (IllegalArgumentException notHex) {
            return false;
        }
        return MessageDigest.isEqual(hmac(rawBody), received);
    }

    private byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
        }
    }
}
