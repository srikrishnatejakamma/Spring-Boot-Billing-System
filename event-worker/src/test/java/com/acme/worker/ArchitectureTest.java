package com.acme.worker;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.acme.worker", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule workerOwnsItsPersistence = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("com.acme.billing.account..", "com.acme.billing.events..", "com.acme.billing.ledger..", "com.acme.billing.web..");
}
