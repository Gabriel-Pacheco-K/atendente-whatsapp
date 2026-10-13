package com.gabrielpacheco.atendente.domain.reply;

import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.booking.DayAvailability;
import com.gabrielpacheco.atendente.domain.booking.Note;
import com.gabrielpacheco.atendente.domain.booking.RequestedDayStatus;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.booking.Suggestion.Alternatives;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static com.gabrielpacheco.atendente.fixtures.Barbearia.BARBA;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.CORTE;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.ENDERECO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.JOAO;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.NOME;
import static com.gabrielpacheco.atendente.fixtures.Barbearia.PEDRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Os textos que aparecem na demonstração, comparados com os arquivos em /textos. Mudar um texto exige atualizar
 * o arquivo de propósito.
 */
@DisplayName("Textos gravados")
class TextsSnapshotTest {

    private static final LocalDate QUARTA = LocalDate.of(2026, 10, 14);
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 19);
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime AGORA_MAIS_30 = LocalDateTime.of(2026, 10, 13, 14, 30);

    static Stream<Arguments> textos() {
        SlotOption quartaAs15 = opcao(QUARTA, "15:00", JOAO);
        SlotOption sabadoAs9 = opcao(SABADO, "09:00", PEDRO);
        return Stream.of(
                arguments("menu", Texts.menu(NOME).text()),
                arguments("boa-noticia-quarta", Texts.exactMatch(quartaAs15, CORTE) + Texts.askName().text()),
                arguments("boa-noticia-sabado", Texts.exactMatch(sabadoAs9, BARBA) + Texts.askName().text()),
                arguments("resumo", Texts.summary(CORTE, quartaAs15, "Gabriel Pacheco").text()),
                arguments("agendado", Texts.booked(1, "Gabriel Pacheco", ENDERECO).text()),
                arguments("ja-confirmado-sabado", Texts.alreadyConfirmed(ref(sabadoAs9, BARBA.name())).text()),
                arguments("confirmar-cancelamento", Texts.confirmCancel(ref(quartaAs15, CORTE.name())).text()),
                arguments("horarios-sabado-de-manha-tanto-faz", Texts.slots(
                        dia(SABADO, SABADO, RequestedDayStatus.AVAILABLE, null),
                        new Alternatives(List.of(opcao(SABADO, "08:00", JOAO), opcao(SABADO, "08:45", PEDRO),
                                opcao(SABADO, "09:30", JOAO), opcao(SABADO, "10:15", PEDRO),
                                opcao(SABADO, "11:00", JOAO), opcao(SABADO, "11:45", PEDRO)), Note.NONE),
                        new TimePreference.Period(PartOfDay.MORNING), BARBA, null).text()),
                arguments("horarios-pedro-ocupado", Texts.slots(
                        dia(SEXTA, SEXTA, RequestedDayStatus.AVAILABLE, null),
                        new Alternatives(List.of(opcao(SEXTA, "10:30", PEDRO), opcao(SEXTA, "10:45", PEDRO),
                                opcao(SEXTA, "11:00", PEDRO)), Note.REQUESTED_TAKEN),
                        new TimePreference.Exact(LocalTime.of(10, 0)), CORTE, PEDRO).text()),
                arguments("horarios-segunda-fechada", Texts.slots(
                        dia(SEGUNDA, TERCA, RequestedDayStatus.CLOSED_WEEKDAY, null),
                        new Alternatives(List.of(opcao(TERCA, "09:00", JOAO), opcao(TERCA, "10:45", PEDRO),
                                opcao(TERCA, "13:00", JOAO), opcao(TERCA, "14:45", PEDRO),
                                opcao(TERCA, "16:45", JOAO), opcao(TERCA, "18:30", PEDRO)), Note.MOVED_TO_NEXT_DAY),
                        null, CORTE, null).text()),
                arguments("horarios-feriado", movido(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 3),
                        RequestedDayStatus.CLOSED_DATE, "Confraternização da equipe", null)),
                arguments("horarios-sabado-lotado", movido(SABADO, TERCA, RequestedDayStatus.FULL, null, JOAO)),
                arguments("horarios-quarta-lotada", movido(QUARTA, LocalDate.of(2026, 10, 15),
                        RequestedDayStatus.FULL, null, JOAO)),
                arguments("horarios-joao-nao-atende", movido(SEGUNDA, TERCA, RequestedDayStatus.NOT_WORKING, null,
                        JOAO)),
                arguments("horarios-fora-do-expediente", Texts.slots(
                        dia(SEXTA, SEXTA, RequestedDayStatus.AVAILABLE, null),
                        new Alternatives(List.of(opcao(SEXTA, "18:00", JOAO), opcao(SEXTA, "18:15", JOAO),
                                opcao(SEXTA, "18:30", JOAO)), Note.OUTSIDE_HOURS),
                        new TimePreference.Exact(LocalTime.of(20, 0)), CORTE, JOAO).text()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textos")
    @DisplayName("O texto é igual ao gravado")
    void textoDeveSerIgualAoGravado(String nome, String texto) {
        assertThat(texto).isEqualTo(gravado(nome));
    }

    private static String movido(LocalDate pedido, LocalDate encontrado, RequestedDayStatus status, String motivo,
                                 Professional profissional) {
        List<SlotOption> opcoes = List.of(opcao(encontrado, "10:00", JOAO), opcao(encontrado, "15:00", JOAO));
        return Texts.slots(dia(pedido, encontrado, status, motivo), new Alternatives(opcoes, Note.MOVED_TO_NEXT_DAY),
                null, CORTE, profissional).text();
    }

    private static DayAvailability dia(LocalDate pedido, LocalDate encontrado, RequestedDayStatus status,
                                       String motivo) {
        return new DayAvailability(pedido, encontrado, List.of(), status, motivo, null, AGORA_MAIS_30);
    }

    private static SlotOption opcao(LocalDate data, String hora, Professional profissional) {
        return new SlotOption(data.atTime(LocalTime.parse(hora)), profissional.id(), profissional.name());
    }

    private static BookingRef ref(SlotOption horario, String servico) {
        return new BookingRef(1, servico, horario.professionalName(), horario.startAt());
    }

    private static String gravado(String nome) {
        try (InputStream arquivo = TextsSnapshotTest.class.getResourceAsStream("/textos/" + nome + ".txt")) {
            assertThat(arquivo).as("arquivo /textos/%s.txt", nome).isNotNull();
            String conteudo = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
            return conteudo.endsWith("\n") ? conteudo.substring(0, conteudo.length() - 1) : conteudo;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
