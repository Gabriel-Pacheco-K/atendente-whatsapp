package com.gabrielpacheco.atendente.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Limite de tamanho do corpo")
class BodySizeLimitFilterTest {

    private static final int LIMITE = 1024;

    private final BodySizeLimitFilter filtro = new BodySizeLimitFilter(LIMITE, new ProblemResponses(new ObjectMapper()));

    @Test
    @DisplayName("Com Content-Length acima do limite, recusa com 413 sem ler o corpo")
    void contentLengthAcimaDoLimiteDeveDar413() throws Exception {
        MockHttpServletRequest pedido = pedido("x".repeat(LIMITE + 1), true);
        MockFilterChain cadeia = new MockFilterChain();
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(resposta.getStatus()).isEqualTo(413);
        assertThat(resposta.getContentType()).startsWith("application/problem+json");
        assertThat(resposta.getContentAsString(StandardCharsets.UTF_8)).contains("\"title\":\"Mensagem muito grande\"");
        assertThat(cadeia.getRequest()).isNull();
    }

    @Test
    @DisplayName("Sem Content-Length (chunked), lê só até o limite + 1 e recusa o que passa")
    void chunkedAcimaDoLimiteDeveDar413() throws Exception {
        MockFilterChain cadeia = new MockFilterChain();
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(pedido("x".repeat(LIMITE * 10), false), resposta, cadeia);

        assertThat(resposta.getStatus()).isEqualTo(413);
        assertThat(cadeia.getRequest()).isNull();
    }

    @Test
    @DisplayName("Sem Content-Length e dentro do limite, o resto da aplicação lê o mesmo corpo")
    void chunkedDentroDoLimiteDeveSeguirComOCorpoLido() throws Exception {
        String corpo = "{\"text\":\"amanhã às 15h\"}";
        MockFilterChain cadeia = new MockFilterChain();

        filtro.doFilter(pedido(corpo, false), new MockHttpServletResponse(), cadeia);

        ServletRequest repassado = cadeia.getRequest();
        assertThat(repassado).isNotNull();
        assertThat(new String(repassado.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(corpo);
        assertThat(repassado.getContentLengthLong()).isEqualTo(corpo.getBytes(StandardCharsets.UTF_8).length);
    }

    private static MockHttpServletRequest pedido(String corpo, boolean comContentLength) {
        MockHttpServletRequest pedido = comContentLength ? new MockHttpServletRequest() : new SemContentLength();
        pedido.setMethod("POST");
        pedido.setRequestURI("/api/chat/sessions");
        pedido.setContentType("application/json");
        pedido.setCharacterEncoding("UTF-8");
        pedido.setContent(corpo.getBytes(StandardCharsets.UTF_8));
        return pedido;
    }

    /** Um envio em partes: o tamanho só é conhecido depois de ler tudo. */
    private static final class SemContentLength extends MockHttpServletRequest {

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
