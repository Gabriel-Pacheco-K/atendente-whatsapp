package com.gabrielpacheco.atendente.domain.reply;

import com.gabrielpacheco.atendente.domain.booking.BookingRef;
import com.gabrielpacheco.atendente.domain.booking.DayAvailability;
import com.gabrielpacheco.atendente.domain.booking.SlotOption;
import com.gabrielpacheco.atendente.domain.booking.Suggestion;
import com.gabrielpacheco.atendente.domain.business.Faq;
import com.gabrielpacheco.atendente.domain.business.OpeningHoursText;
import com.gabrielpacheco.atendente.domain.business.OpeningPeriod;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.nlu.DateIssue;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.PartOfDay;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Tudo o que o atendente escreve. {@code *negrito*} é a marcação do WhatsApp, que a página web também desenha.
 * Os textos que terminam em espaço são prefixos: vão na frente da resposta seguinte.
 */
public final class Texts {

    private static final List<String> YES_NO = List.of("Sim", "Não");
    private static final String ANY_PROFESSIONAL = "Tanto faz";
    private static final int MAX_DETAIL_LENGTH = 200;

    private Texts() {
    }

    // Menu e informação

    public static Reply menu(String businessName) {
        return new Reply("Olá! Eu sou o atendente virtual da *" + businessName + "*. Posso te ajudar a:\n"
                + "1. Agendar um horário\n"
                + "2. Ver ou cancelar seus agendamentos\n"
                + "3. Preços e serviços\n"
                + "4. Endereço e horário de funcionamento\n"
                + "Se quiser falar com uma pessoa, escreva *atendente*.",
                List.of("Agendar", "Meus agendamentos", "Preços", "Endereço"));
    }

    public static Reply prices(List<ServiceType> services) {
        String lines = services.stream()
                .map(service -> "- " + service.name() + ": " + PtBr.money(service.price())
                        + " (" + service.durationMinutes() + " min)")
                .collect(Collectors.joining("\n"));
        return Reply.of("Nossos serviços:\n" + lines + "\nQuer agendar? É só me dizer o serviço e o dia.");
    }

    public static Reply priceOf(ServiceType service) {
        return Reply.of("*" + service.name() + "* custa " + PtBr.money(service.price()) + " e leva "
                + service.durationMinutes() + " min. Quer agendar? É só me dizer o dia.");
    }

    public static Reply address(String address) {
        return Reply.of("Ficamos na " + address + ".");
    }

    public static Reply openingHours(List<OpeningPeriod> periods) {
        return Reply.of("Nosso horário de funcionamento:\n" + OpeningHoursText.describe(periods));
    }

    public static Reply addressAndHours(String address, List<OpeningPeriod> periods) {
        return Reply.of(address(address).text() + "\n" + openingHours(periods).text());
    }

    public static Reply faq(Faq faq) {
        return Reply.of(faq.answer());
    }

    public static Reply human(String phone, String hours) {
        return Reply.of("Claro! Fale com a nossa equipe pelo " + phone + ", " + hours + ".");
    }

    public static Reply thanks() {
        return Reply.of("Por nada! Se precisar, é só chamar.");
    }

    public static Reply noWorries() {
        return Reply.of("Tudo bem! Se precisar, é só chamar.");
    }

    // Agendamento

    /** Mostra no máximo {@code max} serviços; os demais podem ser pedidos pelo nome. */
    public static Reply askService(List<ServiceType> services, int max) {
        List<ServiceType> shown = services.stream().limit(max).toList();
        String text = "Qual serviço você quer?\n" + numbered(shown.stream()
                .map(service -> service.name() + " (" + service.durationMinutes() + " min, "
                        + PtBr.money(service.price()) + ")")
                .toList());
        if (services.size() > max) {
            text += "\nOu escreva o nome do serviço.";
        }
        return new Reply(text, shown.stream().map(ServiceType::name).toList());
    }

    public static Reply askProfessional(List<Professional> professionals) {
        List<String> labels = professionalOptions(professionals);
        return new Reply("Com quem você prefere?\n" + numbered(labels), labels);
    }

    /** Os nomes e, por último, "Tanto faz". */
    public static List<String> professionalOptions(List<Professional> professionals) {
        List<String> labels = new ArrayList<>(professionals.stream().map(Professional::name).toList());
        labels.add(ANY_PROFESSIONAL);
        return List.copyOf(labels);
    }

    public static Reply askDate() {
        return Reply.of("Para qual dia? Pode ser 'amanhã', 'sexta' ou uma data como 20/10.");
    }

    public static Reply dateIssue(DateIssue issue, int daysAhead, LocalDate lastDay) {
        return switch (issue) {
            case PAST -> datePast();
            case INVALID -> dateInvalid();
            case TOO_FAR -> dateTooFar(daysAhead, lastDay);
        };
    }

    public static Reply datePast() {
        return Reply.of("Essa data já passou. Para qual dia você quer?");
    }

    public static Reply dateInvalid() {
        return Reply.of("Essa data não existe. Pode mandar de novo? Ex.: 20/10.");
    }

    public static Reply dateTooFar(int daysAhead, LocalDate lastDay) {
        return Reply.of("Consigo agendar com até " + daysAhead + " dias de antecedência (até "
                + PtBr.longDate(lastDay) + "). Para qual dia?");
    }

    public static String exactMatch(SlotOption slot, ServiceType service) {
        return "Boa notícia: o *" + slot.professionalName() + "* tem horário livre " + onDateAt(slot.startAt())
                + " para *" + service.name() + "* (" + durationAndPrice(service) + "). ";
    }

    public static Reply askName() {
        return Reply.of("Para reservar, qual nome eu coloco no agendamento?");
    }

    public static Reply askNameSuggested(String firstName) {
        return new Reply("Para reservar, posso colocar no nome de *" + firstName + "*?\n"
                + "1. Sim, no nome de " + firstName + "\n"
                + "2. Outro nome", List.of("Sim", "Outro nome"));
    }

    /** Os rótulos das opções de {@link #askNameSuggested(String)}, na mesma ordem. */
    public static List<String> suggestedNameOptions(String firstName) {
        return List.of("Sim, no nome de " + firstName, "Outro nome");
    }

    public static Reply summary(ServiceType service, SlotOption slot, String customerName) {
        return new Reply("Confirma o agendamento?\n"
                + "*" + service.name() + "* com *" + slot.professionalName() + "*\n"
                + PtBr.capitalize(dateAt(slot.startAt())) + ", " + PtBr.money(service.price()) + "\n"
                + "No nome de " + customerName + ".\n"
                + "Responda *sim* ou *não*.", YES_NO);
    }

    public static Reply booked(long appointmentId, String customerName, String address) {
        return Reply.of("Agendado, " + firstWord(customerName) + "! Seu código é *#" + appointmentId + "*.\n"
                + "Endereço: " + address + ".\n"
                + "Se precisar cancelar, é só mandar *cancelar* por aqui.");
    }

    public static Reply alreadyConfirmed(BookingRef booking) {
        return Reply.of("Seu horário de " + describe(booking) + ", já está confirmado (código *#"
                + booking.appointmentId() + "*).");
    }

    public static Reply foundAfterRestart(BookingRef booking) {
        return Reply.of("Faz um tempinho que conversamos, então conferi a agenda: seu horário de " + describe(booking)
                + " ficou marcado (código *#" + booking.appointmentId() + "*).");
    }

    /** Uma tentativa incerta que estava gravada, descoberta quando o cliente já tinha mandado outra coisa. */
    public static Reply pendingFound(BookingRef booking) {
        return Reply.of("Antes de seguir, conferi a agenda: seu horário de " + describe(booking) + " ficou marcado"
                + " (código *#" + booking.appointmentId() + "*). Se não quiser mais, mande *cancelar*.");
    }

    public static Reply pendingUnchecked(String phone) {
        return Reply.of("Antes de seguir, preciso conferir se aquele horário ficou marcado, mas a agenda ainda não"
                + " respondeu. Mande uma mensagem de novo em instantes ou fale com a gente pelo " + phone + ".");
    }

    public static Reply declined() {
        return Reply.of("Sem problemas, não marquei. Para qual dia você prefere?");
    }

    public static Reply yesOrNo() {
        return new Reply("Responda *sim* para confirmar ou *não* para mudar.", YES_NO);
    }

    public static Reply desist() {
        return Reply.of("Tudo bem, não marquei nada. Se quiser cancelar um horário já marcado, mande *cancelar* de novo.");
    }

    public static Reply denyInFlow() {
        return Reply.of("Sem problemas. Me diga outro dia ou horário (ex.: sexta às 16h) ou mande *menu* para recomeçar.");
    }

    public static Reply limitReached(int active, String phone) {
        return Reply.of("Você já tem " + active + " agendamentos marcados por aqui. Para marcar outro, cancele um deles"
                + " ou fale com a gente pelo " + phone + ".");
    }

    // Lista de horários

    /**
     * A lista de horários com o cabeçalho que explica a nota. {@code professional} é nulo com "tanto faz",
     * e então cada linha diz com quem é o horário.
     */
    public static Reply slots(DayAvailability day, Suggestion.Alternatives alternatives, TimePreference preference,
                              ServiceType service, Professional professional) {
        String header = slotsHeader(day, alternatives, preference, service, professional);
        return slotList(header, alternatives.options(), professional == null,
                "Responda com o número ou com outro horário (ex.: 10:30).");
    }

    /** Repete a lista de um passo já mostrado. */
    public static Reply chooseSlotAgain(List<SlotOption> options, boolean anyProfessional) {
        return slotList("Qual destes horários você prefere? (1 a " + options.size() + ")", options, anyProfessional,
                null);
    }

    /** O rótulo curto de um horário, usado como opção e como botão: "10:30" ou "10:30 com Pedro". */
    public static String slotLabel(SlotOption slot, boolean withProfessional) {
        String time = PtBr.time(slot.startAt().toLocalTime());
        return withProfessional ? time + " com " + firstWord(slot.professionalName()) : time;
    }

    private static Reply slotList(String header, List<SlotOption> options, boolean withProfessional, String footer) {
        List<String> labels = options.stream().map(slot -> slotLabel(slot, withProfessional)).toList();
        String text = header + "\n" + numbered(labels) + (footer == null ? "" : "\n" + footer);
        return new Reply(text, labels);
    }

    private static String slotsHeader(DayAvailability day, Suggestion.Alternatives alternatives,
                                      TimePreference preference, ServiceType service, Professional professional) {
        LocalDate date = day.date();
        return switch (alternatives.note()) {
            case NONE -> "Horários livres " + onDateBold(date) + periodSuffix(preference) + " para *" + service.name()
                    + "* (" + durationAndPrice(service) + "):";
            case REQUESTED_TAKEN -> professional == null
                    ? "Às " + requestedTime(preference) + " de *" + PtBr.longDate(date)
                    + "* não tenho ninguém livre. Os horários mais próximos são:"
                    : "Às " + requestedTime(preference) + " de *" + PtBr.longDate(date) + "*, o "
                    + professional.firstName() + " já tem cliente. Os horários mais próximos com ele são:";
            case PERIOD_FULL -> periodOpening(preference) + " não tenho mais " + onDateBold(date)
                    + ", mas tenho estes:";
            case MOVED_TO_NEXT_DAY -> movedHeader(day, professional);
            case OUTSIDE_HOURS -> professional == null
                    ? "Às " + requestedTime(preference) + " ninguém atende " + PtBr.onDate(date)
                    + ". Os mais próximos são:"
                    : "O " + professional.firstName() + " atende " + PtBr.onWeekday(date.getDayOfWeek()) + " "
                    + workingHours(professional, date.getDayOfWeek()) + ". Os mais próximos são:";
            case TOO_SOON -> "Esse horário já passou ou está muito em cima. Os próximos livres são:";
        };
    }

    private static String movedHeader(DayAvailability day, Professional professional) {
        LocalDate requested = day.requested();
        DayOfWeek weekday = requested.getDayOfWeek();
        String reason = switch (day.requestedStatus()) {
            case CLOSED_WEEKDAY -> PtBr.weekdayPlural(weekday, true) + " a barbearia não abre. ";
            case CLOSED_DATE -> "Dia " + PtBr.dayMonth(requested) + " (" + day.closedReason()
                    + ") a barbearia estará fechada. ";
            case NOT_WORKING -> professional == null
                    ? "Ninguém atende " + PtBr.weekdayPlural(weekday, false) + ". "
                    : "O " + professional.firstName() + " não atende " + PtBr.weekdayPlural(weekday, false) + ". ";
            case FULL -> PtBr.fullDay(requested) + ". ";
            case AVAILABLE -> "";
        };
        return reason + "O próximo dia com horários é *" + PtBr.longDate(day.date()) + "*:";
    }

    private static String workingHours(Professional professional, DayOfWeek day) {
        return professional.periodsOn(day).stream()
                .map(period -> "das " + PtBr.time(period.start()) + " às " + PtBr.time(period.end()))
                .collect(Collectors.joining(" e "));
    }

    private static String periodSuffix(TimePreference preference) {
        return preference instanceof TimePreference.Period period ? " " + periodName(period.part()) : "";
    }

    private static String periodOpening(TimePreference preference) {
        PartOfDay part = preference instanceof TimePreference.Period period ? period.part() : PartOfDay.MORNING;
        return PtBr.capitalize(periodName(part));
    }

    private static String periodName(PartOfDay part) {
        return switch (part) {
            case MORNING -> "de manhã";
            case AFTERNOON -> "à tarde";
            case EVENING -> "à noite";
        };
    }

    private static String requestedTime(TimePreference preference) {
        return preference instanceof TimePreference.Exact exact ? PtBr.time(exact.time()) : "";
    }

    // Falhas da agenda

    public static Reply noSlots(LocalDate searchedUntil) {
        return Reply.of("Não encontrei horários livres até " + PtBr.longDate(searchedUntil)
                + ". Quer tentar outra data?");
    }

    public static String slotTaken() {
        return "Puxa, alguém acabou de reservar esse horário. ";
    }

    /** {@code detail} é o motivo que o agendamento-api deu, repassado como veio (até 200 caracteres). */
    public static Reply bookingRejected(String detail) {
        return Reply.of("Não consegui agendar: " + agendaDetail(detail) + "\nPara qual dia você prefere?");
    }

    public static Reply schedulingUnavailableConfirm(String phone) {
        return new Reply("Não consegui acessar a agenda agora. Guardei seus dados: mande *sim* de novo em alguns minutos"
                + " ou ligue para " + phone + ".", YES_NO);
    }

    public static Reply bookingUncertain() {
        return new Reply("A agenda demorou para responder e não consegui confirmar se o horário foi marcado. Mande *sim*"
                + " de novo em instantes que eu confiro antes de tentar outra vez.", YES_NO);
    }

    public static Reply availabilityUnavailable(String phone) {
        return Reply.of("Não consegui consultar a agenda agora. Tente de novo em alguns minutos ou fale com a gente pelo "
                + phone + ".");
    }

    public static Reply schedulingOffline(String phone) {
        return Reply.of("Nosso sistema de agenda está fora do ar agora. Tente de novo em alguns minutos ou fale com a"
                + " gente pelo " + phone + ".");
    }

    // Cancelamento

    public static Reply noBookings() {
        return Reply.of("Não encontrei agendamentos futuros feitos por aqui. Só consigo ver os horários marcados nesta"
                + " conversa.");
    }

    public static Reply myBookings(List<BookingRef> bookings) {
        String lines = bookings.stream()
                .map(booking -> "- *" + booking.serviceName() + "* com " + booking.professionalName() + ", "
                        + dateAt(booking.startAt()) + " (código #" + booking.appointmentId() + ")")
                .collect(Collectors.joining("\n"));
        return Reply.of("Seus próximos agendamentos:\n" + lines + "\nPara cancelar, mande *cancelar*.");
    }

    public static Reply chooseBookingToCancel(List<BookingRef> bookings) {
        return Reply.of("Qual agendamento você quer cancelar?\n" + numbered(bookings.stream()
                .map(booking -> booking.serviceName() + " com " + booking.professionalName() + ", "
                        + dateAt(booking.startAt()))
                .toList()));
    }

    /** O rótulo curto de um agendamento na lista de cancelamento: "Corte masculino com João, qua 14/10 15:00". */
    public static String bookingLabel(BookingRef booking) {
        return booking.serviceName() + " com " + firstWord(booking.professionalName()) + ", "
                + PtBr.shortDateTime(booking.startAt());
    }

    public static Reply confirmCancel(BookingRef booking) {
        return new Reply("Você quer cancelar *" + booking.serviceName() + "* com *" + booking.professionalName() + "* "
                + onDateAt(booking.startAt()) + "? Responda *sim* ou *não*.", YES_NO);
    }

    public static Reply cancelled() {
        return Reply.of("Pronto, cancelei. O horário foi liberado.");
    }

    public static Reply cancelUncertain() {
        return new Reply("A agenda demorou para responder e não consegui confirmar se o horário foi cancelado. Mande"
                + " *sim* de novo em instantes que eu confiro antes de tentar outra vez.", YES_NO);
    }

    public static Reply cancelledAfterCheck() {
        return Reply.of("Conferi a agenda: o cancelamento foi feito, e o horário foi liberado.");
    }

    public static Reply cancelUnchecked(String phone) {
        return Reply.of("Ainda não consegui conferir na agenda se o cancelamento foi feito. Mande *não* de novo em"
                + " instantes que eu confiro, ou fale com a gente pelo " + phone + ".");
    }

    public static Reply alreadyCancelled() {
        return Reply.of("Esse agendamento já estava cancelado.");
    }

    public static Reply cancelRejected(String detail, String phone) {
        return Reply.of("Não consegui cancelar: " + agendaDetail(detail) + " Se precisar, fale com a gente pelo "
                + phone + ".");
    }

    public static Reply cancelKept() {
        return Reply.of("Combinado, seu horário continua marcado.");
    }

    public static Reply rescheduleHint() {
        return Reply.of("Depois de cancelar, me diga o novo dia que eu procuro um horário.");
    }

    // Conversa

    public static Reply notUnderstood(Expectation expectation) {
        return Reply.of(switch (expectation) {
            case NONE -> "Desculpe, não entendi. Você pode escrever, por exemplo: \"quero cortar o cabelo sexta às 10h\""
                    + " ou \"quanto custa a barba?\".";
            case DATE -> "Não entendi a data. Pode ser 'amanhã', 'sexta' ou '20/10'.";
            case OPTION -> "Não entendi. Responda com o número da opção (ex.: 2).";
            case YES_NO -> "Não entendi. Responda *sim* ou *não*.";
            case NAME -> "Me diga só o seu nome, por exemplo: Maria Souza.";
        });
    }

    public static Reply notUnderstoodTwice(String phone) {
        return Reply.of("Ainda não consegui entender. Se preferir, fale com uma pessoa pelo " + phone
                + " ou mande *menu* para recomeçar.");
    }

    /** Uma digressão respondida; em seguida, a pergunta do passo de novo. */
    public static Reply resumingBooking(Reply reprompt) {
        return new Reply("Voltando ao seu agendamento: " + decapitalize(reprompt.text()), reprompt.quickReplies());
    }

    public static String greetingPrefix() {
        return "Oi! ";
    }

    public static String thanksPrefix() {
        return "Por nada! ";
    }

    public static String bookingSetAside() {
        return "Deixei o agendamento de lado. ";
    }

    public static String restarted() {
        return "Como já fazia um tempinho, recomecei nossa conversa. ";
    }

    public static Reply restartedForSafety() {
        return Reply.of("Faz um tempinho que conversamos, então recomecei por segurança e nada foi marcado.");
    }

    public static Reply onlyText() {
        return Reply.of("Por enquanto eu só entendo mensagens de texto. Pode escrever o que você precisa?");
    }

    public static Reply genericError(String phone) {
        return Reply.of("Tive um problema aqui do meu lado. Tente de novo em instantes ou fale com a gente pelo "
                + phone + ".");
    }

    public static Reply rateLimited() {
        return Reply.of("Recebi muitas mensagens seguidas. Me dá um minutinho e tente de novo, por favor.");
    }

    // Apoio

    /** "*Corte masculino* com *João Batista* na quarta-feira, 14/10, às 15:00". */
    private static String describe(BookingRef booking) {
        return "*" + booking.serviceName() + "* com *" + booking.professionalName() + "* "
                + PtBr.onDate(booking.startAt().toLocalDate()) + ", às " + PtBr.time(booking.startAt().toLocalTime());
    }

    /** "quarta-feira, 14/10, às 15:00". */
    private static String dateAt(LocalDateTime startAt) {
        return PtBr.longDate(startAt.toLocalDate()) + ", às " + PtBr.time(startAt.toLocalTime());
    }

    /** "na *quarta-feira, 14/10, às 15:00*". */
    private static String onDateAt(LocalDateTime startAt) {
        return PtBr.preposition(startAt.getDayOfWeek()) + " *" + dateAt(startAt) + "*";
    }

    /** "no *sábado, 17/10*". */
    private static String onDateBold(LocalDate date) {
        return PtBr.preposition(date.getDayOfWeek()) + " *" + PtBr.longDate(date) + "*";
    }

    private static String agendaDetail(String detail) {
        String text = detail == null || detail.isBlank() ? "a agenda recusou o pedido." : detail.strip();
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH - 1) + "…";
    }

    private static String durationAndPrice(ServiceType service) {
        return service.durationMinutes() + " min, " + PtBr.money(service.price());
    }

    private static String numbered(List<String> items) {
        return IntStream.range(0, items.size())
                .mapToObj(i -> (i + 1) + ". " + items.get(i))
                .collect(Collectors.joining("\n"));
    }

    private static String firstWord(String name) {
        return name.strip().split("\\s+")[0];
    }

    private static String decapitalize(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
