package com.gabrielpacheco.atendente.adapter.in.web;

import com.gabrielpacheco.atendente.IntegrationTestBase;
import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.CenaPrincipal;
import com.gabrielpacheco.atendente.fixtures.ChatHttp;
import com.gabrielpacheco.atendente.fixtures.ChatHttp.Sessao;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.AGENDAMENTOS;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.consultasDaAgendaDoDia;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.problema;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.FRASE;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.NOME;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA_15H;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.TELEFONE;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uma sessão nunca enxerga nem mexe no que é de outra. O telefone da web é digitado e não é verificado, então o
 * teste mais duro é o de quem digita o telefone e o nome de outra pessoa e escolhe o mesmo horário dela.
 */
@DisplayName("Isolamento entre clientes")
class CustomerIsolationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    private ChatHttp chat;

    @BeforeEach
    void abrirChat() {
        chat = new ChatHttp(mvc);
        CenaPrincipal.prepararAgenda(agenda(), 1);
    }

    @Test
    @DisplayName("A sessão B não vê nem consegue cancelar o agendamento da sessão A")
    void sessaoBNaoDeveVerNemCancelarOsAgendamentosDeA() {
        Sessao ana = chat.novaSessao(TELEFONE);
        agendar(ana);
        Sessao bruno = chat.novaSessao("+55 48 98888-0002");

        String meus = bruno.envia("meus agendamentos").textos();
        String cancelar = bruno.envia("cancelar").textos();

        assertThat(meus).contains("Não encontrei agendamentos futuros feitos por aqui.");
        assertThat(cancelar).contains("Não encontrei agendamentos futuros feitos por aqui.");
        assertThat(dono(1)).containsExactly(ana.id().toString());
        AGENDA.verify(exactly(0), anyRequestedFor(urlPathMatching(AGENDAMENTOS + "/.*")));
    }

    @Test
    @DisplayName("\"cancelar 1\" com o código de outra pessoa não faz nada: o id nunca vem do texto")
    void idDigitadoNaoDeveCancelarNada() {
        agendar(chat.novaSessao(TELEFONE));
        Sessao bruno = chat.novaSessao("+55 48 98888-0002");

        String resposta = bruno.envia("cancelar 1").textos();
        bruno.envia("sim");

        assertThat(resposta).contains("Não encontrei agendamentos futuros feitos por aqui.");
        AGENDA.verify(exactly(0), patchRequestedFor(urlPathMatching(AGENDAMENTOS + "/.*/cancel")));
    }

    @Test
    @DisplayName("Com o telefone e o nome de A, no mesmo horário, B recebe \"alguém acabou de reservar\" e não ganha posse")
    void conflitoComOsDadosDeOutraPessoaNaoDeveDarPosse() {
        Sessao ana = chat.novaSessao(TELEFONE);
        Sessao impostor = chat.novaSessao(TELEFONE);
        levarAoResumo(ana);
        levarAoResumo(impostor);
        assertThat(ana.envia("sim").textos()).contains("Agendado, Gabriel!");
        horarioDaAnaOcupado();
        agenda().responder(post(urlEqualTo(AGENDAMENTOS)).willReturn(json(409,
                problema(409, "Horário indisponível", "O horário 15:00 já está ocupado para João Batista."))));

        String resposta = impostor.envia("sim").textos();

        assertThat(resposta).startsWith("Puxa, alguém acabou de reservar esse horário.").doesNotContain("Agendado");
        assertThat(dono(1)).containsExactly(ana.id().toString());
        AGENDA.verify(exactly(0), consultasDaAgendaDoDia());
    }

    @Test
    @DisplayName("Com o telefone e o nome de A e um POST sem resposta, B não recebe \"Agendado\" nem o código de A")
    void resultadoIncertoComOsDadosDeOutraPessoaNaoDeveDarPosse() {
        Sessao ana = chat.novaSessao(TELEFONE);
        Sessao impostor = chat.novaSessao(TELEFONE);
        levarAoResumo(ana);
        levarAoResumo(impostor);
        assertThat(ana.envia("sim").textos()).contains("Agendado, Gabriel!");
        horarioDaAnaOcupado();
        agenda().comAgendaDoDia(JOAO, QUARTA, AgendaHttp.agendamento(1, JOAO, CORTE, QUARTA_15H, NOME, TELEFONE,
                        "SCHEDULED"))
                .responder(post(urlEqualTo(AGENDAMENTOS))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        String incerto = impostor.envia("sim").textos();
        agenda().responder(post(urlEqualTo(AGENDAMENTOS)).willReturn(json(409,
                problema(409, "Horário indisponível", "O horário 15:00 já está ocupado para João Batista."))));
        String depois = impostor.envia("sim").textos();

        assertThat(incerto).contains("não consegui confirmar se o horário foi marcado").doesNotContain("#1");
        assertThat(depois).startsWith("Puxa, alguém acabou de reservar esse horário.").doesNotContain("#1");
        assertThat(dono(1)).containsExactly(ana.id().toString());
        AGENDA.verify(moreThanOrExactly(1), consultasDaAgendaDoDia());
    }

    private void agendar(Sessao sessao) {
        levarAoResumo(sessao);
        assertThat(sessao.envia("sim").textos()).contains("Agendado");
    }

    private static void levarAoResumo(Sessao sessao) {
        sessao.envia(FRASE);
        assertThat(sessao.envia(NOME).textos()).contains("Confirma o agendamento?");
    }

    /** Depois do agendamento de A, a agenda já não oferece as 15:00. */
    private static void horarioDaAnaOcupado() {
        agenda().comHorariosLivres(JOAO, CORTE, QUARTA, "09:00", "09:30", "15:30", "16:00");
    }

    /** As sessões que têm o agendamento guardado como seu. */
    private List<String> dono(long idNaAgenda) {
        return jdbc.sql("""
                        SELECT c.external_id FROM bookings b JOIN customers c ON c.id = b.customer_id
                        WHERE b.appointment_id = :id""")
                .param("id", idNaAgenda)
                .query(String.class)
                .list();
    }
}
