package dev.ainer.module.organization;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class OrganizationArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.ainer.module.organization.orgdir");

    @Test
    void domainDependsOnlyOnItselfAndJava() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "java..",
                        "org.jspecify..",
                        "dev.ainer.module.organization.orgdir.domain..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void accessDoesNotDependOnHttpApi() {
        noClasses()
                .that().resideInAPackage("..access..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(classes);
    }

    @Test
    void featureLayersAreFreeOfCycles() {
        slices()
                .matching("dev.ainer.module.organization.orgdir.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
