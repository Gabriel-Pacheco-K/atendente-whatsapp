package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.application.port.out.AppointmentInfo;
import com.gabrielpacheco.atendente.application.port.out.AppointmentNotFoundException;
import com.gabrielpacheco.atendente.application.port.out.BookingRejectedException;
import com.gabrielpacheco.atendente.application.port.out.Deadline;
import com.gabrielpacheco.atendente.application.port.out.NewAppointment;
import com.gabrielpacheco.atendente.application.port.out.SchedulingContractException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingException;
import com.gabrielpacheco.atendente.application.port.out.SchedulingPort;
import com.gabrielpacheco.atendente.application.port.out.SlotTakenException;
import com.gabrielpacheco.atendente.domain.booking.BookingStatus;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.catalog.WorkingPeriod;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O agendamento-api em memória, com as mesmas regras do real: horários de 15 em 15 minutos dentro do expediente,
 * 30 minutos de antecedência, 60 dias de limite, 409 para horário ocupado e cancelamento só até 2 h antes.
 * Permite simular falhas, inclusive o POST que grava mas perde a resposta.
 */
public final class FakeSchedulingPort implements SchedulingPort {

    public enum Operacao { CATALOGO, DISPONIBILIDADE, AGENDAMENTO, CONSULTA, CANCELAMENTO, AGENDA_DO_DIA }

    private static final Duration ANTECEDENCIA_MINIMA = Duration.ofMinutes(30);
    private static final Duration PASSO = Duration.ofMinutes(15);
    private static final int DIAS_MAXIMOS = 60;
    private static final Duration PRAZO_PARA_CANCELAR = Duration.ofHours(2);

    private final MutableClock relogio;
    private final List<Professional> profissionais;
    private final List<ServiceType> servicos;
    private final Map<Long, AppointmentInfo> agenda = new LinkedHashMap<>();
    private final Map<Operacao, Deque<SchedulingException>> proximasFalhas = new EnumMap<>(Operacao.class);
    private final Map<Operacao, SchedulingException> falhasPermanentes = new EnumMap<>(Operacao.class);
    private long proximoId = 1;
    private SchedulingException falhaDepoisDeGravar;
    private SchedulingException falhaDepoisDeCancelar;
    private int posts;
    private Duration latencia = Duration.ZERO;

    public FakeSchedulingPort(MutableClock relogio) {
        this(relogio, Barbearia.profissionais(), Barbearia.servicosDaAgenda());
    }

    public FakeSchedulingPort(MutableClock relogio, List<Professional> profissionais, List<ServiceType> servicos) {
        this.relogio = relogio;
        this.profissionais = List.copyOf(profissionais);
        this.servicos = List.copyOf(servicos);
    }

    // A porta

    @Override
    public synchronized List<Professional> professionals() {
        falharSePreciso(Operacao.CATALOGO);
        return profissionais;
    }

    @Override
    public synchronized List<ServiceType> services() {
        falharSePreciso(Operacao.CATALOGO);
        return servicos;
    }

    @Override
    public synchronized List<LocalTime> freeSlots(long professionalId, long serviceId, LocalDate date) {
        esperar();
        falharSePreciso(Operacao.DISPONIBILIDADE);
        Professional profissional = profissional(professionalId);
        Duration duracao = Duration.ofMinutes(servico(serviceId).durationMinutes());
        LocalDateTime maisCedo = agora().plus(ANTECEDENCIA_MINIMA);
        List<LocalTime> livres = new ArrayList<>();
        for (WorkingPeriod turno : profissional.periodsOn(date.getDayOfWeek())) {
            LocalDateTime fimDoTurno = date.atTime(turno.end());
            for (LocalDateTime inicio = date.atTime(turno.start()); !inicio.plus(duracao).isAfter(fimDoTurno);
                 inicio = inicio.plus(PASSO)) {
                if (!inicio.isBefore(maisCedo) && !ocupado(professionalId, inicio, inicio.plus(duracao))) {
                    livres.add(inicio.toLocalTime());
                }
            }
        }
        return livres;
    }

    @Override
    public synchronized AppointmentInfo book(NewAppointment pedido) {
        posts++;
        esperar();
        falharSePreciso(Operacao.AGENDAMENTO);
        Professional profissional = profissional(pedido.professionalId());
        ServiceType servico = servico(pedido.serviceId());
        LocalDateTime inicio = pedido.startAt();
        LocalDateTime fim = inicio.plusMinutes(servico.durationMinutes());
        if (inicio.isBefore(agora().plus(ANTECEDENCIA_MINIMA))) {
            throw new BookingRejectedException("Agendamentos precisam de pelo menos 30 minutos de antecedência.");
        }
        if (inicio.toLocalDate().isAfter(agora().toLocalDate().plusDays(DIAS_MAXIMOS))) {
            throw new BookingRejectedException("Só é possível agendar com até 60 dias de antecedência.");
        }
        if (!cabeNoExpediente(profissional, inicio, fim)) {
            throw new BookingRejectedException(profissional.name() + " não atende nesse horário.");
        }
        if (ocupado(profissional.id(), inicio, fim)) {
            throw new SlotTakenException("O horário " + inicio.toLocalTime() + " já está ocupado para "
                    + profissional.name() + ".");
        }
        AppointmentInfo criado = gravar(profissional, servico, inicio, pedido.customerName(), pedido.customerPhone());
        if (falhaDepoisDeGravar != null) {
            SchedulingException falha = falhaDepoisDeGravar;
            falhaDepoisDeGravar = null;
            throw falha;
        }
        return criado;
    }

    @Override
    public synchronized AppointmentInfo find(long appointmentId) {
        esperar();
        falharSePreciso(Operacao.CONSULTA);
        return existente(appointmentId);
    }

    @Override
    public synchronized AppointmentInfo cancel(long appointmentId, String reason) {
        esperar();
        falharSePreciso(Operacao.CANCELAMENTO);
        AppointmentInfo atual = existente(appointmentId);
        if (agora().isAfter(atual.startAt().minus(PRAZO_PARA_CANCELAR))) {
            throw new BookingRejectedException("O cancelamento só é permitido até 2h antes do horário.");
        }
        if (!atual.status().isActive()) {
            throw new BookingRejectedException("Não é possível mudar o agendamento de " + atual.status()
                    + " para CANCELLED.");
        }
        AppointmentInfo cancelado = comStatus(atual, BookingStatus.CANCELLED);
        agenda.put(appointmentId, cancelado);
        if (falhaDepoisDeCancelar != null) {
            SchedulingException falha = falhaDepoisDeCancelar;
            falhaDepoisDeCancelar = null;
            throw falha;
        }
        return cancelado;
    }

    @Override
    public synchronized List<AppointmentInfo> dayAgenda(long professionalId, LocalDate date) {
        esperar();
        falharSePreciso(Operacao.AGENDA_DO_DIA);
        return agenda.values().stream()
                .filter(a -> a.professionalId() == professionalId && a.startAt().toLocalDate().equals(date))
                .sorted(Comparator.comparing(AppointmentInfo::startAt))
                .toList();
    }

    /** Na memória não há rede: nada é repetido, então o prazo não muda nada. */
    @Override
    public SchedulingPort within(Deadline deadline) {
        return this;
    }

    // Preparação dos cenários

    /** Outro cliente já marcou esse horário (corte de 30 min), direto no agendamento-api. */
    public synchronized long ocupar(Professional profissional, LocalDateTime inicio) {
        return ocupar(profissional, inicio, "Cliente Exemplo", "+55 48 90000-0000");
    }

    public synchronized long ocupar(Professional profissional, LocalDateTime inicio, String nome, String telefone) {
        return gravar(profissional, servico(Barbearia.CORTE.id()), inicio, nome, telefone).id();
    }

    /** Ocupa todo o expediente do profissional no dia. */
    public synchronized void lotar(Professional profissional, LocalDate dia) {
        for (WorkingPeriod turno : profissional.periodsOn(dia.getDayOfWeek())) {
            long id = proximoId++;
            agenda.put(id, new AppointmentInfo(id, profissional.id(), profissional.name(), "Bloqueio",
                    Barbearia.CORTE.price(), "Cliente Exemplo", "+55 48 90000-0000", dia.atTime(turno.start()),
                    dia.atTime(turno.end()), BookingStatus.SCHEDULED));
        }
    }

    public synchronized void falharProximaCom(Operacao operacao, SchedulingException falha) {
        proximasFalhas.computeIfAbsent(operacao, ignorada -> new ArrayDeque<>()).add(falha);
    }

    public synchronized void falharSempreCom(Operacao operacao, SchedulingException falha) {
        falhasPermanentes.put(operacao, falha);
    }

    public synchronized void voltarAoNormal() {
        proximasFalhas.clear();
        falhasPermanentes.clear();
        latencia = Duration.ZERO;
    }

    /** O próximo POST grava o agendamento, mas a resposta se perde e chega {@code falha}. */
    public synchronized void gravarEFalharProximoPostCom(SchedulingException falha) {
        falhaDepoisDeGravar = falha;
    }

    /** O próximo PATCH cancela o agendamento, mas a resposta se perde e chega {@code falha}. */
    public synchronized void cancelarEFalharProximoPatchCom(SchedulingException falha) {
        falhaDepoisDeCancelar = falha;
    }

    /** Cada chamada à agenda (fora o catálogo) avança o relógio nesse tanto, como uma agenda lenta. */
    public synchronized void latenciaDaAgenda(Duration porChamada) {
        latencia = porChamada;
    }

    /** A barbearia mudou o status direto na agenda. */
    public synchronized void mudarStatus(long id, BookingStatus status) {
        agenda.put(id, comStatus(existente(id), status));
    }

    public synchronized void remover(long id) {
        agenda.remove(id);
    }

    public synchronized int postCount() {
        return posts;
    }

    public synchronized AppointmentInfo agendamento(long id) {
        return existente(id);
    }

    public synchronized List<AppointmentInfo> agendamentos() {
        return List.copyOf(agenda.values());
    }

    // Apoio

    private AppointmentInfo gravar(Professional profissional, ServiceType servico, LocalDateTime inicio, String nome,
                                   String telefone) {
        long id = proximoId++;
        AppointmentInfo info = new AppointmentInfo(id, profissional.id(), profissional.name(), servico.name(),
                servico.price(), nome, telefone, inicio, inicio.plusMinutes(servico.durationMinutes()),
                BookingStatus.SCHEDULED);
        agenda.put(id, info);
        return info;
    }

    private boolean ocupado(long professionalId, LocalDateTime inicio, LocalDateTime fim) {
        return agenda.values().stream()
                .filter(a -> a.professionalId() == professionalId && a.status().isActive())
                .anyMatch(a -> a.startAt().isBefore(fim) && a.endAt().isAfter(inicio));
    }

    private static boolean cabeNoExpediente(Professional profissional, LocalDateTime inicio, LocalDateTime fim) {
        return inicio.toLocalDate().equals(fim.toLocalDate())
                && profissional.periodsOn(inicio.getDayOfWeek()).stream()
                .anyMatch(turno -> !inicio.toLocalTime().isBefore(turno.start())
                        && !fim.toLocalTime().isAfter(turno.end()));
    }

    private Professional profissional(long id) {
        return profissionais.stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new SchedulingContractException("Profissional " + id + " não encontrado."));
    }

    private ServiceType servico(long id) {
        return servicos.stream().filter(s -> s.id() == id).findFirst()
                .orElseThrow(() -> new SchedulingContractException("Serviço " + id + " não encontrado."));
    }

    private AppointmentInfo existente(long id) {
        AppointmentInfo info = agenda.get(id);
        if (info == null) {
            throw new AppointmentNotFoundException("Agendamento " + id + " não encontrado.");
        }
        return info;
    }

    private static AppointmentInfo comStatus(AppointmentInfo a, BookingStatus status) {
        return new AppointmentInfo(a.id(), a.professionalId(), a.professionalName(), a.serviceName(), a.price(),
                a.customerName(), a.customerPhone(), a.startAt(), a.endAt(), status);
    }

    private void falharSePreciso(Operacao operacao) {
        Deque<SchedulingException> fila = proximasFalhas.get(operacao);
        if (fila != null && !fila.isEmpty()) {
            throw fila.poll();
        }
        SchedulingException permanente = falhasPermanentes.get(operacao);
        if (permanente != null) {
            throw permanente;
        }
    }

    private void esperar() {
        if (!latencia.isZero()) {
            relogio.avancar(latencia);
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.ofInstant(relogio.instant(), MutableClock.FUSO);
    }
}
