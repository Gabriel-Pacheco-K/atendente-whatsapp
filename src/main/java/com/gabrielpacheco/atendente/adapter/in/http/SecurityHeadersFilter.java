package com.gabrielpacheco.atendente.adapter.in.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeçalhos de segurança em todas as respostas, inclusive nos erros. A CSP não tem {@code unsafe-inline}: a página
 * não usa script nem estilo inline, e o texto das mensagens entra só como texto.
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; "
            + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
