package com.gabrielpacheco.atendente.adapter.in.http;

import jakarta.servlet.http.HttpServletRequest;

/**
 * O IP de quem fez o pedido, para os limites de taxa. É o endereço da conexão: X-Forwarded-For só vale com
 * {@code server.forward-headers-strategy=native}, ligado apenas atrás de um proxy confiável, e aí o próprio
 * servidor já o aplica a {@code getRemoteAddr()}. Assim ninguém escapa do limite inventando um cabeçalho.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
