package com.gabrielpacheco.atendente.adapter.in.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Chave do limite por IP")
class ClientIpResolverTest {

    @Test
    @DisplayName("IPv4 é a chave como veio")
    void ipv4DeveSerAChave() {
        assertThat(ClientIpResolver.rateLimitKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("IPv6 vira a rede /64: endereços da mesma rede dão a mesma chave")
    void ipv6DeveVirarARede() {
        String primeiro = ClientIpResolver.rateLimitKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd");
        String outroDaMesmaRede = ClientIpResolver.rateLimitKey("2001:db8:1:2::1");

        assertThat(primeiro).isEqualTo(outroDaMesmaRede).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(ClientIpResolver.rateLimitKey("2001:db8:1:3::1")).isNotEqualTo(primeiro);
    }

    @Test
    @DisplayName("IPv4 escrito como IPv6 continua sendo o IPv4")
    void ipv4MapeadoDeveSerOIpv4() {
        assertThat(ClientIpResolver.rateLimitKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }
}
