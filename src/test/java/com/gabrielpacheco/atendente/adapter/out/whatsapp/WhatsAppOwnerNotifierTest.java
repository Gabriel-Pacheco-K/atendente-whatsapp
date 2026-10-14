package com.gabrielpacheco.atendente.adapter.out.whatsapp;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Aviso ao dono no WhatsApp")
class WhatsAppOwnerNotifierTest {

    private static final AppointmentInfo AGENDAMENTO = new AppointmentInfo(7, 1, "João Batista", "Corte masculino",
            new BigDecimal("45.00"), "Ana Souza", "48999990001", LocalDateTime.of(2026, 10, 14, 15, 0),
            LocalDateTime.of(2026, 10, 14, 15, 30), BookingStatus.SCHEDULED);

    @Test
    @DisplayName("Manda nome, serviço, profissional, dia e telefone para o número do dono, só com dígitos")
    void deveAvisarODono() {
        List<String> para = new ArrayList<>();
        List<Reply> mensagens = new ArrayList<>();
        new WhatsAppOwnerNotifier((to, reply) -> {
            para.add(to);
            mensagens.add(reply);
        }, "+55 (48) 99999-0000").newBooking(AGENDAMENTO);

        assertThat(para).containsExactly("5548999990000");
        assertThat(mensagens.get(0).text())
                .contains("Ana Souza · Corte masculino com João Batista")
                .contains("14/10 às 15:00")
                .contains("48999990001");
    }

    @Test
    @DisplayName("Uma falha no envio não chega no agendamento")
    void falhaNaoPropaga() {
        WhatsAppOwnerNotifier aviso = new WhatsAppOwnerNotifier((to, reply) -> {
            throw new IllegalStateException("fora do ar");
        }, "48999990000");
        assertThatCode(() -> aviso.newBooking(AGENDAMENTO)).doesNotThrowAnyException();
    }
}
