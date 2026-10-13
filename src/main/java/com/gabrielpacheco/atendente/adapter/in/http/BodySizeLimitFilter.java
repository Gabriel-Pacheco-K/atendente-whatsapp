package com.gabrielpacheco.atendente.adapter.in.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Recusa com 413 um corpo maior que o limite. Com Content-Length, decide sem ler nada; sem ele (envio em partes,
 * "chunked"), lê no máximo limite + 1 bytes e, se couber, entrega ao resto da aplicação o corpo já lido.
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ProblemResponses problems;

    public BodySizeLimitFilter(long maxBytes, ProblemResponses problems) {
        this.maxBytes = maxBytes;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            reject(response);
            return;
        }
        if (declared >= 0) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = readAtMost(request.getInputStream(), maxBytes + 1);
        if (body.length > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        problems.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Mensagem muito grande",
                "O corpo da requisição passa do limite de " + maxBytes / 1024 + " KB.");
    }

    private static byte[] readAtMost(InputStream input, long limit) throws IOException {
        return input.readNBytes((int) Math.min(limit, Integer.MAX_VALUE));
    }

    /** O pedido com o corpo já lido em memória, que pode ser lido de novo pelo Spring MVC. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffered = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return buffered.read();
                }

                @Override
                public int read(byte[] target, int offset, int length) {
                    return buffered.read(target, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return buffered.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Leitura assíncrona não é usada aqui");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        private Charset charset() {
            String encoding = getCharacterEncoding();
            return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        }
    }
}
