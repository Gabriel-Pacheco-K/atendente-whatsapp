package com.gabrielpacheco.atendente.adapter.in.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Confere o cabeçalho {@code X-Twilio-Signature}: HMAC-SHA1, com o Auth Token, da URL pública do webhook seguida de
 * cada parâmetro do formulário (nome e valor), em ordem alfabética de nome. A URL precisa ser exatamente a cadastrada
 * na Twilio, por isso vem da configuração e não do pedido: atrás de um túnel, o pedido chega com outro endereço.
 */
public class TwilioSignatureVerifier {

    private final byte[] authToken;
    private final String webhookUrl;

    public TwilioSignatureVerifier(String authToken, String webhookUrl) {
        this.authToken = authToken.getBytes(StandardCharsets.UTF_8);
        this.webhookUrl = webhookUrl;
    }

    public boolean isValid(Map<String, String> params, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(params);
        byte[] received;
        try {
            received = Base64.getDecoder().decode(signature.strip());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, received);
    }

    byte[] sign(Map<String, String> params) {
        StringBuilder data = new StringBuilder(webhookUrl);
        new TreeMap<>(params).forEach((name, value) -> data.append(name).append(value));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken, "HmacSHA1"));
            return mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
