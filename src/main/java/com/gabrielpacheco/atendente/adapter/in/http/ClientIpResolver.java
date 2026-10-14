package com.gabrielpacheco.atendente.adapter.in.http;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * A chave de quem fez o pedido, para os limites de taxa. Vem do endereço da conexão: X-Forwarded-For só vale com
 * {@code server.forward-headers-strategy=native}, ligado apenas atrás de um proxy confiável, e aí o próprio
 * servidor já o aplica a {@code getRemoteAddr()}. Assim ninguém escapa do limite inventando um cabeçalho.
 */
public final class ClientIpResolver {

    private static final int IPV6_NETWORK_BYTES = 8;

    private ClientIpResolver() {
    }

    public static String rateLimitKey(HttpServletRequest request) {
        return rateLimitKey(request.getRemoteAddr());
    }

    /**
     * O IPv4 inteiro, ou só a rede /64 do IPv6: um cliente comum recebe a /64 inteira e poderia usar um endereço novo
     * a cada pedido.
     */
    static String rateLimitKey(String remoteAddress) {
        if (remoteAddress == null || !remoteAddress.contains(":")) {
            return remoteAddress;
        }
        try {
            // Com ":" o texto é um IPv6 literal: getByName só o lê, sem consultar o DNS.
            InetAddress address = InetAddress.getByName(remoteAddress);
            if (!(address instanceof Inet6Address)) {
                return address.getHostAddress();
            }
            byte[] network = Arrays.copyOf(address.getAddress(), 16);
            Arrays.fill(network, IPV6_NETWORK_BYTES, network.length, (byte) 0);
            return InetAddress.getByAddress(network).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return remoteAddress;
        }
    }
}
