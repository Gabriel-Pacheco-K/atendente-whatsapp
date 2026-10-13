package com.gabrielpacheco.atendente.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

/** Erros em ProblemDetail com título em PT-BR, inclusive para os filtros, que respondem antes do Spring MVC. */
public class ProblemResponses {

    private final ObjectMapper mapper;

    public ProblemResponses(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    public void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem(status, title, detail));
    }
}
