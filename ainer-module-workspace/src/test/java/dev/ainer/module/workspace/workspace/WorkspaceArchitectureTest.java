package dev.ainer.module.workspace.workspace;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class WorkspaceArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.ainer.module.workspace.workspace");

    @Test
    void domainDependsOnlyOnItselfAndJava() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "java..",
                        "dev.ainer.module.workspace.workspace.domain..")
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
    void workspaceDoesNotDependOnSpringSecurityInternals() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.security..",
                        "org.springframework.security.oauth2..")
                .check(classes);
    }

    @Test
    void featureLayersAreFreeOfCycles() {
        slices()
                .matching("dev.ainer.module.workspace.workspace.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
