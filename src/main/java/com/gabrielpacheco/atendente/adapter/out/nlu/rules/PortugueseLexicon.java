package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.domain.nlu.Intent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gabrielpacheco.atendente.domain.nlu.Intent.ADDRESS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.AFFIRM;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.BOOK;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.CANCEL_BOOKING;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.DENY;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.GOODBYE;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.GREETING;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.HUMAN;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.MY_BOOKINGS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.OPENING_HOURS;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.PRICES;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.RESTART;
import static com.gabrielpacheco.atendente.domain.nlu.Intent.THANKS;

/** O vocabulário do motor de regras, já no formato normalizado (sem acentos, "pra" vira "para"). */
final class PortugueseLexicon {

    /** Despedidas, marcadas antes da data como as saudações: "até amanhã" não é um pedido para amanhã. */
    static final List<String> FAREWELLS = List.of("ate mais", "ate logo", "ate depois", "ate a proxima", "ate amanha",
            "ate segunda", "ate terca", "ate quarta", "ate quinta", "ate sexta", "ate sabado", "abraco", "abracos");

    /** Da expressão mais longa para a mais curta: "horario de funcionamento" consome "horario". */
    static final List<Cue> CUES = Stream.of(
                    weighted(BOOK, 3, "agendar", "marcar", "reservar", "tem horario", "tem vaga", "o quanto antes"),
                    weighted(BOOK, 2, "cortar", "vaga", "encaixe"),
                    weighted(BOOK, 1, "horario", "da para"),
                    weighted(CANCEL_BOOKING, 3, "cancelar", "cancela", "desmarcar", "desmarca", "remarcar",
                            "mudar meu horario", "mudar o horario", "nao vou poder ir", "nao vou conseguir ir",
                            "nao posso ir"),
                    weighted(MY_BOOKINGS, 3, "meus agendamentos", "meu horario", "qual meu horario", "tenho horario"),
                    weighted(MY_BOOKINGS, 2, "ja agendei"),
                    weighted(PRICES, 3, "preco", "valor", "quanto e", "quanto ta", "quanto fica", "quanto custa",
                            "quanto tempo", "duracao"),
                    weighted(PRICES, 2, "quanto", "custa", "tabela", "servicos", "demora"),
                    weighted(OPENING_HOURS, 4, "que horas abre", "que horas fecha", "horario de funcionamento"),
                    weighted(OPENING_HOURS, 3, "funcionamento"),
                    weighted(OPENING_HOURS, 2, "abre", "abrem", "abrir", "fecha", "fecham", "aberto", "expediente"),
                    weighted(ADDRESS, 4, "onde fica", "onde ficam", "como chego"),
                    weighted(ADDRESS, 3, "endereco", "localizacao"),
                    weighted(ADDRESS, 2, "onde", "mapa"),
                    weighted(HUMAN, 4, "falar com alguem", "falar com uma pessoa"),
                    weighted(HUMAN, 3, "atendente", "humano", "gerente"),
                    weighted(HUMAN, 2, "pessoa", "ligar", "telefone"),
                    weighted(RESTART, 4, "comecar de novo"),
                    weighted(RESTART, 3, "menu", "recomecar", "sair"),
                    weighted(RESTART, 2, "inicio", "voltar", "desistir"),
                    weighted(AFFIRM, 3, "sim", "pode ser", "confirmo", "confirma"),
                    weighted(AFFIRM, 2, "isso", "pode", "ok", "beleza", "certo", "claro", "fechado", "bora", "uhum",
                            "aham", "perfeito", "show", "top", "massa"),
                    weighted(DENY, 3, "nao", "negativo", "nao quero", "esquece", "deixa para la", "deixa quieto",
                            "pode deixar", "pode manter", "manter", "mantem", "mantenha", "outro dia", "outro horario",
                            "outra data"),
                    weighted(DENY, 2, "errado", "mudar", "trocar", "outro", "outra"),
                    weighted(THANKS, 4, "muito obrigado", "muito obrigada"),
                    weighted(THANKS, 3, "obrigado", "obrigada", "valeu", "agradeco", "tmj"),
                    weighted(GOODBYE, 3, Stream.concat(FAREWELLS.stream(), Stream.of("tchau", "falou", "flw"))
                            .toArray(String[]::new)),
                    weighted(GREETING, 3, "bom dia", "boa tarde", "boa noite"),
                    weighted(GREETING, 2, "oi", "ola", "e ai", "opa", "salve"))
            .flatMap(List::stream)
            .sorted(Comparator.comparingInt(Cue::words).thenComparingInt(cue -> cue.phrase().length()).reversed())
            .toList();

    /** Uma destas até 2 palavras antes de uma pista de agendamento a transforma em recusa ("nao quero mais marcar"). */
    static final List<String> NEGATIONS = List.of("nao quero", "nao vou", "nao preciso", "nao", "nem");

    /** Saudações fixas, marcadas antes de tudo para "boa tarde" não virar período nem "bom dia" virar data. */
    static final List<String> GREETINGS = List.of("bom dia", "boa tarde", "boa noite");


    /** Verbos que, ao responder o nome, deixam claro que o cliente está corrigindo o agendamento. */
    static final Set<String> BOOKING_VERBS = Set.of("agendar", "marcar", "reservar", "cortar");

    static final Set<String> CALENDAR = Set.of(
            "hoje", "amanha", "depois", "manha", "tarde", "noite", "meio", "dia", "hora", "horas", "cedo", "almoco",
            "fim", "final", "semana", "proxima", "proximo", "vem", "feira",
            "segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo",
            "janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro",
            "novembro", "dezembro",
            "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze",
            "doze", "meia");

    static final Set<String> OPTION_WORDS = Set.of(
            "opcao", "numero", "primeiro", "primeira", "segundo", "terceiro", "terceira", "quarto", "quinto", "sexto",
            "ultimo", "ultima", "tanto", "faz", "qualquer", "quem", "tiver", "indiferente");

    static final Set<String> STOPWORDS = Set.of(
            "a", "o", "as", "os", "de", "da", "do", "para", "com", "um", "uma", "e", "no", "na", "que", "eu", "me",
            "meu", "minha", "voce");

    /** Palavras comuns perto demais do vocabulário: sem esta lista, "barbearia" viraria "barbear" (Barba). */
    static final Set<String> KNOWN_WORDS = Set.of(
            "barbeiro", "barbeiros", "barbearia", "barbearias", "cabeleireiro", "cabelos", "horarios", "agendamento",
            "agendado", "agendada", "marcado", "marcada", "cancelado", "cancelada", "reservado", "reservada",
            "quando", "mais", "manda", "mande", "pessoal", "algum", "alguma", "lugar", "vale", "esqueci", "falo",
            "temos");

    private PortugueseLexicon() {
    }

    /** Todas as palavras das pistas e das opções, que o corretor usa como destino. */
    static Set<String> words() {
        return Stream.of(CUES.stream().map(Cue::phrase), NEGATIONS.stream(), OPTION_WORDS.stream())
                .flatMap(phrases -> phrases)
                .flatMap(phrase -> Arrays.stream(phrase.split(" ")))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Pistas de uma palavra só ("sim", "cancelar", "menu"), que nunca são nome de pessoa. */
    static Set<String> singleWordCues() {
        return CUES.stream().map(Cue::phrase).filter(phrase -> !phrase.contains(" "))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<Cue> weighted(Intent intent, int weight, String... phrases) {
        return Arrays.stream(phrases).map(phrase -> new Cue(intent, phrase, weight)).toList();
    }
}
