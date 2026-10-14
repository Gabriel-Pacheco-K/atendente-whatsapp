package com.gabrielpacheco.atendente.fixtures;

import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.nlu.DateIssue;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entidades montadas campo a campo, para o teste dizer só o que importa:
 * <pre>
 * Entidades.com().servico(BARBA).data(SEXTA).montar()
 * </pre>
 */
public final class Entidades {

    private Long servico;
    private Long profissional;
    private boolean tantoFaz;
    private LocalDate data;
    private DateIssue problemaNaData;
    private TimePreference hora;
    private Integer opcao;
    private String nome;
    private String faq;
    private boolean remarcar;
    private boolean simENao;

    private Entidades() {
    }

    public static Entidades com() {
        return new Entidades();
    }

    public Entidades servico(ServiceType servico) {
        return servico(servico.id());
    }

    public Entidades servico(long id) {
        this.servico = id;
        return this;
    }

    public Entidades profissional(Professional profissional) {
        return profissional(profissional.id());
    }

    public Entidades profissional(long id) {
        this.profissional = id;
        return this;
    }

    public Entidades tantoFaz() {
        this.tantoFaz = true;
        return this;
    }

    public Entidades data(LocalDate data) {
        this.data = data;
        return this;
    }

    public Entidades problemaNaData(DateIssue problema) {
        this.problemaNaData = problema;
        return this;
    }

    public Entidades hora(TimePreference hora) {
        this.hora = hora;
        return this;
    }

    /** "15:00" vira um horário exato. */
    public Entidades hora(String hora) {
        return hora(new TimePreference.Exact(LocalTime.parse(hora)));
    }

    public Entidades opcao(int opcao) {
        this.opcao = opcao;
        return this;
    }

    public Entidades nome(String nome) {
        this.nome = nome;
        return this;
    }

    public Entidades faq(String id) {
        this.faq = id;
        return this;
    }

    public Entidades remarcar() {
        this.remarcar = true;
        return this;
    }

    public Entidades simENao() {
        this.simENao = true;
        return this;
    }

    public Entities montar() {
        return new Entities(servico, profissional, tantoFaz, data, problemaNaData, hora, opcao, nome, faq, remarcar,
                simENao);
    }
}
