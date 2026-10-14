package com.gabrielpacheco.atendente;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

/** Portas e adaptadores de verdade: as regras quebram o build quando uma dependência atravessa a fronteira errada. */
@DisplayName("Arquitetura")
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.gabrielpacheco.atendente");

    @Test
    @DisplayName("1. O domínio depende só do Java e de si mesmo")
    void dominioDependeSoDoJavaEDoProprioDominio() {
        classes().that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("2. A aplicação usa o domínio, o SLF4J e só as anotações e exceções de transação do Spring")
    void aplicacaoUsaSoODominioEOEssencialDoSpring() {
        classes().that().resideInAPackage("..application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..", "..domain..", "..application..", "org.slf4j..",
                        "org.springframework.stereotype..", "org.springframework.transaction.annotation..",
                        "org.springframework.dao..", "org.springframework.transaction..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("2. A aplicação não conhece adaptadores nem configuração")
    void aplicacaoNaoConheceAdaptadoresNemConfiguracao() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..config..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("3. Adaptadores de entrada e de saída não se conhecem")
    void adaptadoresDeEntradaESaidaNaoSeConhecem() {
        noClasses().that().resideInAPackage("..adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("4. O motor de regras e o adaptador do Claude não se conhecem")
    void motorDeRegrasEClaudeNaoSeConhecem() {
        noClasses().that().resideInAPackage("..adapter.out.nlu.rules..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out.nlu.claude..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..adapter.out.nlu.claude..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out.nlu.rules..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("4. O interpretador híbrido depende só da porta, nunca das implementações")
    void interpretadorHibridoDependeSoDaPorta() {
        classes().that().resideInAPackage("..adapter.out.nlu")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..", "..domain..", "..application.port..", "..adapter.out.nlu", "org.slf4j..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("5. O SDK da Anthropic só aparece no adaptador do Claude")
    void sdkDaAnthropicSoNoAdaptadorDoClaude() {
        noClasses().that().resideOutsideOfPackage("..adapter.out.nlu.claude..")
                .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("6. Ninguém lê o relógio do sistema sem passar por um Clock")
    void ninguemLeORelogioSemClock() {
        noClasses()
                .should().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(LocalTime.class, "now")
                .orShould().callMethod(Instant.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .because("o horário vem do Clock injetado, para os testes controlarem o tempo")
                .check(CLASSES);
    }

    @Test
    @DisplayName("6. Ninguém escreve em System.out ou System.err")
    void ninguemEscreveNaSaidaPadrao() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
    }
}
