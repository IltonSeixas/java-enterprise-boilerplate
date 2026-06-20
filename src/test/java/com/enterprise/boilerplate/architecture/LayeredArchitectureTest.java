package com.enterprise.boilerplate.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.enterprise.boilerplate", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String INTERFACES = "..interfaces..";

    @ArchTest
    static final ArchRule layered_architecture_dependencies_are_respected = com.tngtech.archunit.library.Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .layer("Interfaces").definedBy(INTERFACES)
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Interfaces")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule domain_must_not_depend_on_any_framework = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "io.grpc..",
                    "io.jsonwebtoken..",
                    "org.bouncycastle..",
                    "redis.clients..")
            .because("the domain layer must be framework-agnostic — it expresses business rules only");

    @ArchTest
    static final ArchRule application_must_not_depend_on_persistence_or_protocol_frameworks = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "io.grpc..",
                    "io.jsonwebtoken..",
                    "org.bouncycastle..",
                    "redis.clients..")
            .because("use cases must stay portable across persistence and transport adapters — "
                    + "Spring is allowed here only for dependency injection (@Service, @Value)");

    @ArchTest
    static final ArchRule use_cases_must_reside_in_usecase_package = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage("..application.usecase..")
            .because("use cases are the application layer's single entry point convention");

    @ArchTest
    static final ArchRule ports_must_be_interfaces = classes()
            .that().resideInAPackage("..application.port..")
            .should().beInterfaces()
            .because("ports are contracts implemented by infrastructure adapters, never concrete logic");
}
