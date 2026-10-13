package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.in.ConversationReply;
import com.gabrielpacheco.atendente.application.port.in.IncomingMessage;
import com.gabrielpacheco.atendente.application.port.in.MessageKind;
import com.gabrielpacheco.atendente.domain.conversation.ConversationState;
import com.gabrielpacheco.atendente.domain.conversation.Customer;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;
import com.gabrielpacheco.atendente.domain.reply.Reply;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Um teste que se lê como a conversa:
 * <pre>
 * dialogo.cliente("qro agnedar um corte amanha as 3 da tarde c/ o joao")
 *        .atendenteDiz("quarta-feira, 14/10, às 15:00")
 *        .estadoE(AskingName.class);
 * </pre>
 */
public final class Dialogo {

    private final Ambiente ambiente;
    private final CustomerRef cliente;
    private ConversationReply ultima;
    private String ultimoId;

    Dialogo(Ambiente ambiente, CustomerRef cliente) {
        this.ambiente = ambiente;
        this.cliente = cliente;
    }

    public static Dialogo naWeb() {
        return new Ambiente().clienteNaWeb();
    }

    public static Dialogo noWhatsApp(String nomeDoPerfil) {
        return new Ambiente().clienteNoWhatsApp("5548999990000", nomeDoPerfil);
    }

    public Dialogo cliente(String texto) {
        return enviar(MessageKind.TEXT, texto, UUID.randomUUID().toString());
    }

    /** Reenvia uma mensagem com o mesmo id, como faz a Meta ou a página depois de uma falha de rede. */
    public Dialogo reenviaAUltima(String texto) {
        return enviar(MessageKind.TEXT, texto, ultimoId);
    }

    public Dialogo enviaAudio() {
        return enviar(MessageKind.UNSUPPORTED, null, UUID.randomUUID().toString());
    }

    public Dialogo atendenteDiz(String trecho) {
        assertThat(texto()).contains(trecho);
        return this;
    }

    public Dialogo atendenteNaoDiz(String trecho) {
        assertThat(texto()).doesNotContain(trecho);
        return this;
    }

    public Dialogo estadoE(Class<? extends ConversationState> tipo) {
        assertThat(estado()).isInstanceOf(tipo);
        return this;
    }

    public Dialogo notaE(String nota) {
        assertThat(ultima.diagnostics().note()).isEqualTo(nota);
        return this;
    }

    public Dialogo avancarRelogio(Duration quanto) {
        ambiente.relogio().avancar(quanto);
        return this;
    }

    /** Todas as respostas do último turno, separadas por uma linha em branco. */
    public String texto() {
        return ultima.replies().stream().map(Reply::text).collect(Collectors.joining("\n\n"));
    }

    public List<Reply> respostas() {
        return ultima.replies();
    }

    public ConversationReply resposta() {
        return ultima;
    }

    public ConversationState estado() {
        return ambiente.conversas().estado(clienteSalvo().id());
    }

    public Customer clienteSalvo() {
        return ambiente.clientes().upsert(cliente);
    }

    public Ambiente ambiente() {
        return ambiente;
    }

    public FakeSchedulingPort agenda() {
        return ambiente.agenda();
    }

    private Dialogo enviar(MessageKind tipo, String texto, String messageId) {
        ultimoId = messageId;
        ultima = ambiente.atendente().handle(
                new IncomingMessage(cliente, tipo, texto, messageId, ambiente.relogio().instant()));
        return this;
    }
}
