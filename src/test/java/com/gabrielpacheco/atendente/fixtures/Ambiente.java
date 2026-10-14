package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.adapter.out.nlu.HybridInterpreter;
import com.gabrielpacheco.atendente.adapter.out.nlu.rules.RuleBasedInterpreter;
import com.gabrielpacheco.atendente.application.CatalogProvider;
import com.gabrielpacheco.atendente.application.ConversationService;
import com.gabrielpacheco.atendente.application.ConversationSettings;
import com.gabrielpacheco.atendente.application.flow.AvailabilityFinder;
import com.gabrielpacheco.atendente.application.flow.BookingConfirmation;
import com.gabrielpacheco.atendente.application.flow.BookingFlow;
import com.gabrielpacheco.atendente.application.flow.BookingGaps;
import com.gabrielpacheco.atendente.application.flow.BookingReconciler;
import com.gabrielpacheco.atendente.application.flow.CancellationFlow;
import com.gabrielpacheco.atendente.application.flow.ConversationRouter;
import com.gabrielpacheco.atendente.application.flow.InfoResponder;
import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.domain.business.BusinessProfile;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.conversation.Channel;
import com.gabrielpacheco.atendente.domain.conversation.CustomerRef;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O núcleo da conversa inteiro, de verdade, sem Spring nem banco: interpretador de regras, roteador e fluxos reais,
 * com a agenda falsa, stores em memória e um relógio que o teste controla (a pausa entre tentativas só avança o
 * relógio). Vários clientes podem dividir o mesmo ambiente, como na barbearia de verdade.
 */
public final class Ambiente {

    public static final String TELEFONE_DO_CLIENTE = "+55 48 99999-0001";
    /** O mesmo valor do application.yml. */
    private static final double LIMIAR_DAS_REGRAS = 0.75;

    private final MutableClock relogio = MutableClock.naReferencia();
    private final FakeSchedulingPort agenda;
    private final InMemoryCustomerStore clientes = new InMemoryCustomerStore();
    private final InMemoryConversationStore conversas = new InMemoryConversationStore(relogio);
    private final InMemoryBookingStore agendamentos = new InMemoryBookingStore();
    private final InMemoryProcessedMessageStore processadas = new InMemoryProcessedMessageStore(relogio);
    private final ConversationService atendente;
    private final List<AppointmentInfo> avisosAoDono = new ArrayList<>();

    public Ambiente() {
        this(Barbearia.profissionais(), Barbearia.servicosDaAgenda());
    }

    public Ambiente(List<Professional> profissionais, List<ServiceType> servicos) {
        this(profissionais, servicos, new RuleBasedInterpreter());
    }

    private Ambiente(List<Professional> profissionais, List<ServiceType> servicos, MessageInterpreter interpretador) {
        this.agenda = new FakeSchedulingPort(relogio, profissionais, servicos);
        BusinessProfile negocio = Barbearia.perfil();
        ConversationSettings configuracao = Barbearia.configuracao();
        CatalogProvider catalogo = new CatalogProvider(agenda, negocio, relogio, Duration.ofSeconds(60), Runnable::run);
        AvailabilityFinder disponibilidade = new AvailabilityFinder(agenda, negocio, configuracao);
        BookingReconciler reconciliador = new BookingReconciler(agenda, agendamentos, relogio::avancar, configuracao);
        BookingGaps lacunas = new BookingGaps(disponibilidade, catalogo, negocio, configuracao);
        BookingConfirmation confirmacao = new BookingConfirmation(lacunas, reconciliador, agenda, agendamentos,
                acao -> {
                }, negocio, configuracao, avisosAoDono::add);
        CancellationFlow cancelamento = new CancellationFlow(agenda, agendamentos, negocio, configuracao);
        ConversationRouter roteador = new ConversationRouter(new BookingFlow(lacunas, confirmacao, negocio),
                confirmacao, cancelamento, new InfoResponder(negocio), negocio, configuracao);
        this.atendente = new ConversationService(clientes, conversas, processadas, catalogo,
                interpretador, roteador, negocio, configuracao, relogio);
    }

    /** Com o Claude ligado: o interpretador híbrido de verdade, com {@code claude} no lugar do Claude. */
    public static Ambiente comClaude(MessageInterpreter claude) {
        return new Ambiente(Barbearia.profissionais(), Barbearia.servicosDaAgenda(),
                new HybridInterpreter(new RuleBasedInterpreter(), Optional.of(claude), LIMIAR_DAS_REGRAS));
    }

    public Dialogo clienteNaWeb() {
        return clienteNaWeb(TELEFONE_DO_CLIENTE);
    }

    public Dialogo clienteNaWeb(String telefone) {
        return new Dialogo(this, new CustomerRef(Channel.WEB, UUID.randomUUID().toString(), telefone, null));
    }

    public Dialogo clienteNoWhatsApp(String waId, String nomeDoPerfil) {
        return new Dialogo(this, new CustomerRef(Channel.WHATSAPP, waId, "+" + waId, nomeDoPerfil));
    }

    /** Os avisos que o dono recebeu no WhatsApp, um por horário marcado pelo atendente. */
    public List<AppointmentInfo> avisosAoDono() {
        return avisosAoDono;
    }

    public MutableClock relogio() {
        return relogio;
    }

    public FakeSchedulingPort agenda() {
        return agenda;
    }

    public InMemoryCustomerStore clientes() {
        return clientes;
    }

    public InMemoryConversationStore conversas() {
        return conversas;
    }

    public InMemoryBookingStore agendamentos() {
        return agendamentos;
    }

    ConversationService atendente() {
        return atendente;
    }
}
