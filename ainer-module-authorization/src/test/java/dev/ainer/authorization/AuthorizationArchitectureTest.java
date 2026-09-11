package dev.ainer.authorization;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 包边界守护（ADR-0037 §3）：{@code domain/}、{@code policy/}、{@code catalog/}、
 * {@code application/} 保持零 Spring Security 依赖；{@code spring/} 子包是唯一的
 * Spring Security/Servlet 适配层，且不得被其他包反向引用。
 */
class AuthorizationArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.ainer.authorization");

    @Test
    void domainStaysFreeOfSpring() {
        // 公开领域契约保持 Spring-free（ADR-0030 §9.2、ADR-0037 §3）
        noClasses()
                .that().resideInAPackage("..authorization.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.servlet..", "org.apache.ibatis..")
                .check(classes);
    }

    @Test
    void guardedPackagesStayFreeOfSpringSecurity() {
        noClasses()
                .that().resideInAnyPackage(
                        "..authorization.domain..",
                        "..authorization.policy..",
                        "..authorization.catalog..",
                        "..authorization.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.security..")
                .check(classes);
    }

    @Test
    void guardedPackagesStayFreeOfServletApi() {
        noClasses()
                .that().resideInAnyPackage(
                        "..authorization.domain..",
                        "..authorization.policy..",
                        "..authorization.catalog..",
                        "..authorization.application..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..")
                .check(classes);
    }

    @Test
    void springAdapterIsOnlyReferencedByModuleConfiguration() {
        // 只有模块装配 Configuration 允许引用 spring/ 适配层；domain/application 等不得反向依赖。
        // 例外：spring/ 里的注解类型（含其嵌套类型，如 @EndpointAccess.Kind）是纯声明元数据
        // （无行为、无 Spring 依赖），任何层都可以用它标注自己的端点——把注解也纳入「适配层行为」
        // 限制会逼出「api 层不能用注解声明访问口径」的荒谬结论。
        classes()
                .that().resideInAPackage("..authorization.spring..")
                .and(new com.tngtech.archunit.base.DescribedPredicate<
                        com.tngtech.archunit.core.domain.JavaClass>("不是注解类型或其嵌套类型") {
                    @Override
                    public boolean test(com.tngtech.archunit.core.domain.JavaClass candidate) {
                        com.tngtech.archunit.core.domain.JavaClass current = candidate;
                        while (current != null) {
                            if (current.isAnnotation()) {
                                return false;
                            }
                            current = current.getEnclosingClass().orElse(null);
                        }
                        return true;
                    }
                })
                .should().onlyHaveDependentClassesThat(new com.tngtech.archunit.base.DescribedPredicate<
                        com.tngtech.archunit.core.domain.JavaClass>(
                        "适配层本身或模块装配 Configuration") {
                    @Override
                    public boolean test(com.tngtech.archunit.core.domain.JavaClass dependent) {
                        return dependent.getPackageName().equals("dev.ainer.authorization.spring")
                                || dependent.getPackageName().equals("dev.ainer.authorization");
                    }
                })
                .check(classes);
    }
}
