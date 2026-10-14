package com.gabrielpacheco.atendente.adapter.out.nlu.rules;

import com.gabrielpacheco.atendente.adapter.out.nlu.rules.DateExtractor.DateMention;
import com.gabrielpacheco.atendente.adapter.out.nlu.rules.FaqMatcher.FaqHit;
import com.gabrielpacheco.atendente.adapter.out.nlu.rules.IntentClassifier.Classification;
import com.gabrielpacheco.atendente.application.port.out.MessageInterpreter;
import com.gabrielpacheco.atendente.domain.catalog.Catalog;
import com.gabrielpacheco.atendente.domain.catalog.CatalogMatcher;
import com.gabrielpacheco.atendente.domain.catalog.Hit;
import com.gabrielpacheco.atendente.domain.catalog.Professional;
import com.gabrielpacheco.atendente.domain.catalog.ServiceType;
import com.gabrielpacheco.atendente.domain.nlu.Entities;
import com.gabrielpacheco.atendente.domain.nlu.Expectation;
import com.gabrielpacheco.atendente.domain.nlu.Interpretation;
import com.gabrielpacheco.atendente.domain.nlu.InterpretationContext;
import com.gabrielpacheco.atendente.domain.nlu.InterpreterKind;
import com.gabrielpacheco.atendente.domain.nlu.TimePreference;
import com.gabrielpacheco.atendente.domain.text.NormalizedText;
import com.gabrielpacheco.atendente.domain.text.TextNormalizer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Interpreta português de chat sem chamar nenhum serviço: normaliza, corrige a digitação, extrai as entidades
 * (cada extrator consome o trecho que reconheceu) e classifica a intenção. Determinístico e sem estado de conversa.
 */
public final class RuleBasedInterpreter implements MessageInterpreter {

    private static final Pattern RESCHEDULE = Pattern.compile("\\b(?:remarcar|mudar (?:meu|o) horario)\\b");
    private static final Pattern WITH_BEFORE_NAME = Pattern.compile("\\bcom(?: [oa])? $");

    private record CatalogTools(Catalog catalog, CatalogMatcher matcher, SpellCorrector corrector) {
    }

    private record CatalogMention(Long serviceId, Long professionalId, boolean professionalAfterWith) {

        static final CatalogMention NONE = new CatalogMention(null, null, false);

        boolean isEmpty() {
            return serviceId == null && professionalId == null;
        }
    }

    private final AtomicReference<CatalogTools> tools = new AtomicReference<>();
    private final OptionExtractor options = new OptionExtractor();
    private final TimeExtractor times = new TimeExtractor();
    private final DateExtractor dates = new DateExtractor();
    private final AnyProfessionalExtractor anyProfessional = new AnyProfessionalExtractor();
    private final NameExtractor names = new NameExtractor();
    private final FaqMatcher faqs = new FaqMatcher();
    private final IntentClassifier classifier = new IntentClassifier();

    @Override
    public Interpretation interpret(String text, InterpretationContext context) {
        NormalizedText normalized = TextNormalizer.normalize(text);
        CatalogTools catalog = toolsFor(context.catalog());
        // Ao pedir o nome, nada é corrigido: "Clara" não pode virar "claro".
        boolean nameExpected = context.expectation() == Expectation.NAME;
        Correction corrected = nameExpected
                ? Correction.unchanged(normalized.tokens())
                : catalog.corrector().correct(normalized.tokens());

        Spans spans = new Spans(corrected.tokens());
        PortugueseLexicon.GREETINGS.forEach(spans::consumeAll);
        Integer option = options.extract(spans, context).orElse(null);
        TimePreference time = times.extract(spans, context).orElse(null);
        Optional<DateMention> date = dates.extract(spans, context);
        boolean anyone = !nameExpected && anyProfessional.extract(spans);
        CatalogMention mention = catalogMention(spans, catalog.matcher());
        boolean question = normalized.original().strip().endsWith("?");
        if (nameExpected && !question && !clearlyAboutBooking(time, date, corrected, mention)) {
            mention = CatalogMention.NONE;
        }
        boolean bookingData = time != null || date.isPresent() || anyone || !mention.isEmpty();
        Optional<FaqHit> faq = faqs.match(corrected.text(), context.faqs());
        String personName = bookingData || faq.isPresent() ? null
                : personName(normalized.original(), context, catalog.matcher()).orElse(null);

        Entities found = new Entities(mention.serviceId(), mention.professionalId(), anyone,
                date.map(DateMention::date).orElse(null), date.map(DateMention::issue).orElse(null), time, option,
                personName, faq.map(FaqHit::faqId).orElse(null), RESCHEDULE.matcher(corrected.text()).find(), false);
        Classification classification = classifier.classify(corrected, context.expectation(), found,
                faq.map(FaqHit::keywords).orElse(0));
        return new Interpretation(classification.intent(), classification.confidence(),
                withYesNoConflict(found, classification.yesNoConflict()), InterpreterKind.RULES);
    }

    /** Na pergunta do nome, a resposta; no resumo, só um nome apresentado ("meu nome é Ana") corrige o anterior. */
    private Optional<String> personName(String original, InterpretationContext context, CatalogMatcher catalog) {
        return switch (context.expectation()) {
            case NAME -> names.extract(original, context.offeredOptions(), catalog);
            case YES_NO -> names.extractIntroduced(original, catalog);
            default -> Optional.empty();
        };
    }

    private static CatalogMention catalogMention(Spans spans, CatalogMatcher matcher) {
        Optional<Hit<ServiceType>> service = matcher.matchService(spans.text());
        service.ifPresent(hit -> spans.consume(hit.start(), hit.end()));
        String text = spans.text();
        Optional<Hit<Professional>> professional = matcher.matchProfessional(text);
        professional.ifPresent(hit -> spans.consume(hit.start(), hit.end()));
        return new CatalogMention(
                service.map(hit -> hit.value().id()).orElse(null),
                professional.map(hit -> hit.value().id()).orElse(null),
                professional.filter(hit -> WITH_BEFORE_NAME.matcher(text.substring(0, hit.start())).find()).isPresent());
    }

    /**
     * Na hora do nome, serviço e profissional só contam com uma pista clara de correção: data, hora, verbo de
     * agendamento ou "com o X". Sem isso, "Pedro Silva" é o nome do cliente. Uma pergunta nunca é o nome, e o
     * serviço dela continua valendo: "quanto custa a barba?" pergunta o preço da barba.
     */
    private static boolean clearlyAboutBooking(TimePreference time, Optional<DateMention> date, Correction text,
                                               CatalogMention mention) {
        return time != null || date.isPresent() || mention.professionalAfterWith()
                || text.tokens().stream().anyMatch(PortugueseLexicon.BOOKING_VERBS::contains);
    }

    private CatalogTools toolsFor(Catalog catalog) {
        CatalogTools current = tools.get();
        if (current != null && current.catalog() == catalog) {
            return current;
        }
        CatalogTools fresh = new CatalogTools(catalog, new CatalogMatcher(catalog), new SpellCorrector(catalog));
        tools.set(fresh);
        return fresh;
    }

    private static Entities withYesNoConflict(Entities e, boolean conflict) {
        return new Entities(e.serviceId(), e.professionalId(), e.anyProfessional(), e.date(), e.dateIssue(), e.time(),
                e.option(), e.personName(), e.faqId(), e.rescheduleRequested(), conflict);
    }
}
