package com.gabrielpacheco.atendente;

import com.gabrielpacheco.atendente.fixtures.AgendaHttp;
import com.gabrielpacheco.atendente.fixtures.CapturaDeLogs;
import com.gabrielpacheco.atendente.fixtures.CenaPrincipal;
import com.gabrielpacheco.atendente.fixtures.ChatHttp;
import com.gabrielpacheco.atendente.fixtures.ChatHttp.Sessao;
import com.gabrielpacheco.atendente.fixtures.WhatsAppHttp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.AGENDAMENTOS;
import static com.gabrielpacheco.atendente.fixtures.AgendaHttp.json;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.NOME;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.QUARTA_15H;
import static com.gabrielpacheco.atendente.fixtures.CenaPrincipal.TELEFONE;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.ACCESS_TOKEN;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.APP_SECRET;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.ENVIOS;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.PERFIL;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.VERIFY_TOKEN;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.WA_ID;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.imagem;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.payload;
import static com.gabrielpacheco.atendente.fixtures.WhatsAppHttp.texto;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * LGPD na prática: depois de uma conversa inteira, nada do que a aplicação registrou (mensagem, MDC ou exceção, em
 * qualquer nível) traz o nome, o telefone, o texto do cliente, o id da sessão, o wa_id ou um segredo.
 */
@DisplayName("Logs sem dados pessoais")
class NoPersonalDataInLogsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Agendar, cancelar e até um erro inesperado no chat web não deixam dados pessoais no log")
    void conversaNaWebNaoDeveDeixarDadosPessoaisNoLog() {
        CenaPrincipal.prepararAgenda(agenda(), 1);
        agenda().responder(get(urlEqualTo(AGENDAMENTOS + "/1")).willReturn(json(200,
                        AgendaHttp.agendamento(1, JOAO, CORTE, QUARTA_15H, NOME, TELEFONE, "SCHEDULED"))))
                .responder(patch(urlEqualTo(AGENDAMENTOS + "/1/cancel")).willReturn(json(200,
                        AgendaHttp.agendamento(1, JOAO, CORTE, QUARTA_15H, NOME, TELEFONE, "CANCELLED"))));

        try (CapturaDeLogs logs = CapturaDeLogs.daAplicacao()) {
            Sessao sessao = new ChatHttp(mvc).novaSessao(TELEFONE);
            sessao.envia(CenaPrincipal.FRASE);
            sessao.envia(NOME);
            assertThat(sessao.envia("sim").textos()).contains("Agendado");
            sessao.envia("quero cancelar");
            assertThat(sessao.envia("sim").textos()).contains("Pronto, cancelei.");
            sessao.envia("me chamo Gabriel Pacheco e meu telefone é 48 99999-0001, " + ERRO_PROPOSITAL);

            List<String> escrito = logs.tudoQueFoiEscrito();
            assertThat(escrito).as("a conversa deixou rastro no log").anyMatch(linha -> linha.startsWith("turno "));
            assertThat(escrito).allSatisfy(linha -> assertThat(linha).doesNotContain(
                    "Gabriel Pacheco", "48999990001", "99999-0001", "agnedar", sessao.id().toString()));
        }
    }

    @Test
    @DisplayName("Agendar pelo WhatsApp, com envio recusado, um bug e um webhook forjado, não deixa dados nem segredos")
    void conversaNoWhatsAppNaoDeveDeixarDadosPessoaisNemSegredosNoLog() {
        CenaPrincipal.prepararAgenda(agenda(), 1);
        // Toda resposta esbarra no número de teste da Meta, para o WARN com o destinatário também ser conferido.
        GRAPH.stubFor(post(urlEqualTo(ENVIOS)).willReturn(json(400,
                "{\"error\":{\"message\":\"(#131030) Recipient phone number not in allowed list\",\"code\":131030}}")));
        WhatsAppHttp whatsapp = new WhatsAppHttp(mvc);

        try (CapturaDeLogs logs = CapturaDeLogs.daAplicacao()) {
            whatsapp.envia(payload(texto("wamid.1", CenaPrincipal.FRASE)));
            whatsapp.envia(payload(texto("wamid.2", "2")));
            whatsapp.envia(payload(texto("wamid.3", NOME)));
            whatsapp.envia(payload(texto("wamid.4", "sim")));
            whatsapp.envia(payload(imagem("wamid.5")));
            whatsapp.envia(payload(texto("wamid.6", "me chamo Gabriel Pacheco, meu telefone é 48 99999-0001, "
                    + ERRO_PROPOSITAL)));
            whatsapp.enviaComAssinatura(payload(texto("wamid.7", "oi")), "sha256=" + "0".repeat(64));
            whatsapp.envia("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"texto\":\"agnedar");

            List<String> escrito = logs.tudoQueFoiEscrito();
            assertThat(escrito).as("a conversa deixou rastro no log")
                    .anyMatch(linha -> linha.startsWith("turno canal=WHATSAPP"))
                    .anyMatch(linha -> linha.contains("+55 48 9****-0000"));
            assertThat(escrito).allSatisfy(linha -> assertThat(linha).doesNotContain(
                    "Gabriel Pacheco", PERFIL, "48999990001", "99999-0001", WA_ID, "48999990000", "agnedar",
                    APP_SECRET, VERIFY_TOKEN, ACCESS_TOKEN));
        }
    }
}
