package com.gabrielpacheco.atendente.application;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.in.HandleIncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import com.gabrielpacheco.atendente.domain.reply.Reply;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.CenaPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.AGENDAMENTOS;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.postsDeAgendamento;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.NOME;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA_15H;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.TELEFONE;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A prova da trava por cliente: oito "sim" ao mesmo tempo, com o POST demorando, marcam um horário só. Sem o
 * {@code FOR UPDATE} da conversa, mais de um "sim" encontraria o resumo e o teste veria mais de um POST.
 */
@DisplayName("Concorrência na conversa")
class ConversationConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int SIMULTANEOS = 8;

    @Autowired
    private HandleIncomingMessage atendente;

    @Test
    @DisplayName("Oito \"sim\" simultâneos geram um POST, um \"Agendado\" e sete \"já está confirmado\"")
    void oitoSimSimultaneosDevemAgendarUmaVezSo() throws Exception {
        CustomerRef cliente = new CustomerRef(Channel.WEB, UUID.randomUUID().toString(), TELEFONE, null);
        CenaPrincipal.prepararAgenda(agenda(), 1);
        enviar(cliente, CenaPrincipal.FRASE);
        assertThat(textos(enviar(cliente, NOME))).contains("Confirma o agendamento?");
        agenda().responder(post(urlEqualTo(AGENDAMENTOS)).willReturn(json(201,
                AgendaHttp.agendamento(1, JOAO, CORTE, QUARTA_15H, NOME, TELEFONE, "SCHEDULED")).withFixedDelay(300)));

        List<String> respostas = todosAoMesmoTempo(() -> textos(enviar(cliente, "sim")));

        AGENDA.verify(exactly(1), postsDeAgendamento());
        assertThat(respostas).filteredOn(texto -> texto.startsWith("Agendado, Gabriel!")).hasSize(1);
        assertThat(respostas).filteredOn(texto -> texto.contains("já está confirmado")).hasSize(SIMULTANEOS - 1);
    }

    private static List<String> todosAoMesmoTempo(Callable<String> tarefa) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> pendentes = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(SIMULTANEOS)) {
            for (int i = 0; i < SIMULTANEOS; i++) {
                pendentes.add(threads.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<String> resultados = new ArrayList<>();
            for (Future<String> pendente : pendentes) {
                resultados.add(pendente.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        }
    }

    private ConversationReply enviar(CustomerRef cliente, String texto) {
        return atendente.handle(new IncomingMessage(cliente, MessageKind.TEXT, texto, UUID.randomUUID().toString(),
                relogio.instant()));
    }

    private static String textos(ConversationReply resposta) {
        return resposta.replies().stream().map(Reply::text).collect(Collectors.joining("\n\n"));
    }
}
