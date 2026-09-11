package dev.ainer.server;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 框架 ↔ 产品边界（规范见 docs/conventions.md §13）的字节码级方向断言。
 *
 * <p>规则：{@code dev.ainer..}（框架）不得依赖 {@code cn.xiaoqu..} / {@code dev.xq..}（产品）。
 * 框架子集要能被脚本机械导出回公开仓，前提就是开发期框架从不依赖产品；这里把该前提变成
 * 测试，而不是评审记忆。
 *
 * <p><b>当前公开仓没有产品类，主断言会平凡通过</b>——它的价值在产品代码与框架代码同仓开发的
 * monorepo 中生效：只要某个 {@code dev.ainer} 类引用产品包，本测试立即失败。为了让「规则会拦」
 * 这件事在公开仓就可验证，本类同时用测试作用域的 {@code cn.xiaoqu} 夹具做负向自测
 * （见 {@link #boundaryRuleRejectsFrameworkFixtureDependingOnProductPackage()}）与对照组
 * （见 {@link #boundaryRuleAllowsFrameworkFixtureWithoutProductDependency()}）。
 *
 * <p>产品包根不写死在本类，而是从 {@code scripts/framework-boundary-targets.txt} 读取——与
 * {@code scripts/check-framework-boundary.sh} 共用同一份清单，避免「脚本规则扩展了、ArchUnit
 * 没跟上」的漂移。覆盖范围限于 ainer-server 测试类路径可见的框架模块；{@code ainer-offstate-app}、
 * {@code ainer-initializer*} 等不在其依赖图中的模块由 shell 门禁兜底。
 */
class AinerServerBoundaryArchitectureTest {

    /** 边界清单文件相对仓库根的位置；缺失时失败关闭，不静默跳过。 */
    private static final String TARGETS_FILE = "scripts/framework-boundary-targets.txt";

    private static final List<String> PRODUCT_PACKAGE_ROOTS = productPackageRoots();

    private static final ClassFileImporter MAIN_CLASS_IMPORTER = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    private static final ArchRule FRAMEWORK_DOES_NOT_DEPEND_ON_PRODUCT = noClasses()
            .that().resideInAPackage("dev.ainer..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(PRODUCT_PACKAGE_ROOTS.stream()
                    .map(root -> root + "..")
                    .toArray(String[]::new))
            .because("框架必须能机械导出回公开仓：dev.ainer.* 一旦依赖产品包，抽取就会退化为重写"
                    + "（docs/conventions.md §13）");

    @Test
    void frameworkClassesDoNotDependOnProductPackages() {
        JavaClasses frameworkClasses = MAIN_CLASS_IMPORTER.importPackages("dev.ainer");

        // 先证明导入确实看到了框架主类与主源码（而不是空集合），否则本断言会平凡通过。
        assertThat(frameworkClasses.contain("dev.ainer.server.AinerServerApplication"))
                .as("必须导入到框架主类，否则边界规则可能因空集合恒真")
                .isTrue();

        FRAMEWORK_DOES_NOT_DEPEND_ON_PRODUCT.check(frameworkClasses);
    }

    @Test
    void boundaryRuleRejectsFrameworkFixtureDependingOnProductPackage() {
        JavaClasses violatingFixture = new ClassFileImporter()
                .importPackages("dev.ainer.server.boundary.fixture", "cn.xiaoqu.fixture");

        assertThat(violatingFixture.contain("dev.ainer.server.boundary.fixture.FrameworkFixtureDependingOnProduct"))
                .as("负向夹具必须真的被导入，否则本用例证明不了规则会拦")
                .isTrue();

        assertThatThrownBy(() -> FRAMEWORK_DOES_NOT_DEPEND_ON_PRODUCT.check(violatingFixture))
                .as("框架类依赖产品包时必须被拦下")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("FrameworkFixtureDependingOnProduct")
                .hasMessageContaining("cn.xiaoqu.fixture.ProductFixtureType");
    }

    @Test
    void boundaryRuleAllowsFrameworkFixtureWithoutProductDependency() {
        JavaClasses isolatedFixture = new ClassFileImporter()
                .importPackages("dev.ainer.server.boundary.isolated");

        assertThat(isolatedFixture.contain("dev.ainer.server.boundary.isolated.IsolatedFrameworkFixture"))
                .as("对照组夹具必须真的被导入")
                .isTrue();

        // 对照组证明规则不是「见到夹具就失败」：违规判定确实来自产品包依赖本身。
        FRAMEWORK_DOES_NOT_DEPEND_ON_PRODUCT.check(isolatedFixture);
    }

    @Test
    void productPackageRootsAreConfiguredAndCoverTheNegativeFixture() {
        assertThat(PRODUCT_PACKAGE_ROOTS)
                .as("边界清单 %s 必须至少登记一个产品包根", TARGETS_FILE)
                .isNotEmpty();

        assertThat(PRODUCT_PACKAGE_ROOTS)
                .as("负向夹具的包必须命中清单登记的产品包根，否则负向自测失去意义")
                .anyMatch(root -> "cn.xiaoqu.fixture.ProductFixtureType".startsWith(root + "."));
    }

    /**
     * 解析边界清单的 {@code package} 行；与 shell 门禁共用同一份配置，新增产品包根时无需改测试。
     */
    private static List<String> productPackageRoots() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(TARGETS_FILE);
            if (Files.isRegularFile(candidate)) {
                return readPackageRoots(candidate);
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("找不到框架/产品边界清单：" + TARGETS_FILE);
    }

    private static List<String> readPackageRoots(Path targetsFile) {
        try (var lines = Files.lines(targetsFile)) {
            return lines
                    .map(line -> line.split("#", 2)[0].strip())
                    .filter(line -> line.startsWith("package "))
                    .map(line -> line.substring("package ".length()).strip())
                    .filter(root -> !root.isEmpty())
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("读取框架/产品边界清单失败：" + targetsFile, exception);
        }
    }
}
