package dev.ainer.module.identity;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Identity foundation 是扁平包，HTTP 只在 authorization-server。
 * 本测试只守「foundation 不依赖 Servlet / Spring Web」。
 */
class IdentityArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.ainer.module.identity");

    @Test
    void foundationDoesNotDependOnWeb() {
        noClasses()
                .that().resideInAPackage("dev.ainer.module.identity.foundation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..")
                .check(classes);
    }
}
