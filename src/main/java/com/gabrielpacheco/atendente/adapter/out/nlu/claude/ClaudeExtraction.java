package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gabrielpacheco.atendente.domain.nlu.Intent;

import java.util.Optional;

/**
 * O esquema da saída estruturada. Os campos opcionais viram tipo anulável no esquema. Não há nome nem telefone: o
 * Claude nunca extrai dados pessoais.
 * <p>
 * Um campo ausente na resposta chega aqui como null (os {@code Optional} chegam vazios); ele vira o valor neutro,
 * para uma resposta incompleta não derrubar o turno.
 */
@JsonClassDescription("Interpretação de uma mensagem de cliente de uma barbearia")
public record ClaudeExtraction(
        @JsonPropertyDescription("Intenção principal da mensagem") Intent intent,
        @JsonPropertyDescription("Confiança de 0 a 1") double confidence,
        @JsonPropertyDescription("Nome exato de um serviço da lista, ou null") Optional<String> service,
        @JsonPropertyDescription("Nome exato de um profissional da lista, ou null") Optional<String> professional,
        @JsonPropertyDescription("true se o cliente disse que tanto faz o profissional") boolean anyProfessional,
        @JsonPropertyDescription("Data AAAA-MM-DD resolvida a partir da data de referência, ou null")
        Optional<String> date,
        @JsonPropertyDescription("Hora HH:MM em 24h, ou null") Optional<String> time,
        @JsonPropertyDescription("Período do dia citado; NONE se não houver") ClaudePartOfDay partOfDay,
        @JsonPropertyDescription("Número da opção escolhida entre as mostradas, ou null") Optional<Integer> option,
        @JsonPropertyDescription("id de uma pergunta frequente da lista, ou null") Optional<String> faqId) {

    public ClaudeExtraction {
        intent = intent == null ? Intent.UNKNOWN : intent;
        partOfDay = partOfDay == null ? ClaudePartOfDay.NONE : partOfDay;
    }
}
