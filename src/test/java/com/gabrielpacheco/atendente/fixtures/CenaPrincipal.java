package com.gabrielpacheco.atendente.fixtures;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;

/**
 * A cena do agendamento numa frase só: "qro agnedar um corte amanha as 3 da tarde c/ o joao", o nome e o "sim".
 * Na agenda, o João tem a quarta às 15:00 livre.
 */
public final class CenaPrincipal {

    public static final String FRASE = "qro agnedar um corte amanha as 3 da tarde c/ o joao";
    public static final String NOME = "Gabriel Pacheco";
    public static final String TELEFONE = "+55 48 99999-0001";
    public static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    public static final LocalDateTime QUARTA_15H = QUARTA.atTime(15, 0);

    private CenaPrincipal() {
    }

    /** O horário das 15:00 livre e um POST que cria o agendamento {@code id} para o cliente da cena. */
    public static void prepararAgenda(AgendaHttp agenda, long id) {
        agenda.comHorariosLivres(JOAO, CORTE, QUARTA, "09:00", "09:30", "15:00", "15:30", "16:00")
                .aceitandoAgendamento(id, JOAO, CORTE, QUARTA_15H, NOME, TELEFONE);
    }
}
