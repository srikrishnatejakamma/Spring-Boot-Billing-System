package com.acme.billing;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.acme.billing", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule sharedContractsDoNotDependOnServices = noClasses().that()
            .resideInAnyPackage("com.acme.billing.core..", "com.acme.billing.dto..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.acme.billing.account..", "com.acme.worker..");
    @ArchTest static final ArchRule billingDoesNotDependOnWorker = noClasses().should().dependOnClassesThat().resideInAPackage("com.acme.worker..");
}
