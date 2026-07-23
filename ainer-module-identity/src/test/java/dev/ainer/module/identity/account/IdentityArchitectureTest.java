package dev.ainer.module.identity.account;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class IdentityArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.ainer.module.identity.account");

    @Test
    void domainDependsOnlyOnItselfAndJava() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "java..",
                        "dev.ainer.module.identity.account.domain..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                .check(classes);
    }

    @Test
    void accountLayersAreFreeOfCycles() {
        slices()
                .matching("dev.ainer.module.identity.account.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
