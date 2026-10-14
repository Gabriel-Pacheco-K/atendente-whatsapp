package com.gabrielpacheco.atendente.adapter.in.http;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("Cabeçalhos de segurança e a página")
class SecurityHeadersIntegrationTest extends IntegrationTestBase {

    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; "
            + "frame-ancestors 'none'";

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/", "/app.js", "/style.css"})
    @DisplayName("A página e os arquivos estáticos saem com a CSP exata e os demais cabeçalhos")
    void arquivosEstaticosDevemTerOsCabecalhos(String caminho) throws Exception {
        MockHttpServletResponse resposta = executar(get(caminho));

        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(resposta.getHeader("Content-Security-Policy")).isEqualTo(CSP);
        assertThat(resposta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(resposta.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(resposta.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(resposta.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(resposta.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }

    @Test
    @DisplayName("A API também, e ainda proíbe guardar a resposta em cache")
    void apiDeveTerOsCabecalhosESemCache() throws Exception {
        MockHttpServletResponse resposta = executar(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"+55 48 99999-0001\"}"));

        assertThat(resposta.getStatus()).isEqualTo(201);
        assertThat(resposta.getHeader("Content-Security-Policy")).isEqualTo(CSP);
        assertThat(resposta.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(resposta.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/%61pi/chat/sessions", "/api;x=1/chat/sessions"})
    @DisplayName("Um caminho equivalente ao da API também sai sem cache, porque a resposta traz o id da sessão")
    void caminhoEquivalenteDeveSairSemCache(String caminho) throws Exception {
        MockHttpServletResponse resposta = executar(post(URI.create(caminho)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"+55 48 99999-0001\"}"));

        assertThat(resposta.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("Até a recusa de um corpo grande demais sai com os cabeçalhos")
    void recusaDoFiltroDeveTerOsCabecalhos() throws Exception {
        MockHttpServletResponse resposta = executar(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + "9".repeat(20 * 1024) + "\"}"));

        assertThat(resposta.getStatus()).isEqualTo(413);
        assertThat(resposta.getHeader("Content-Security-Policy")).isEqualTo(CSP);
    }

    @Test
    @DisplayName("O HTML não tem script inline, atributos on* nem recursos de fora")
    void htmlNaoDeveTerCodigoInline() throws IOException {
        String html = estatico("index.html");

        assertThat(Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html).find())
                .as("script inline").isFalse();
        assertThat(Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE).matcher(html).find())
                .as("atributo on*").isFalse();
        assertThat(html).doesNotContainIgnoringCase("<style").doesNotContainIgnoringCase(" style=")
                .doesNotContain("http://", "https://", "src=\"//", "href=\"//");
    }

    @Test
    @DisplayName("O JavaScript monta o DOM só com texto: nada de innerHTML, eval ou handlers on*")
    void javascriptNaoDeveInterpretarHtml() throws IOException {
        String js = estatico("app.js");

        assertThat(js).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(",
                "new Function", "http://", "https://");
        assertThat(Pattern.compile("\\.on[a-z]+\\s*=").matcher(js).find()).as("handler on*").isFalse();
    }

    private MockHttpServletResponse executar(RequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andReturn().getResponse();
    }

    private static String estatico(String arquivo) throws IOException {
        return new ClassPathResource("static/" + arquivo).getContentAsString(StandardCharsets.UTF_8);
    }
}
