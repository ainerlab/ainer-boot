package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import dev.ainer.initializer.manifest.ManifestV1;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic offline generator: builds the full in-memory file tree from a validated
 * manifest v1 and the embedded template set. No network, no clock, no registry access.
 */
public final class ProjectGenerator {

    /** Classpath root of the template set, keyed by version. */
    private static final String TEMPLATE_ROOT = "templates/v1";

    private static final Map<String, String> EXTRA_STARTER_DEPENDENCY = Map.of(
            "dev.ainer:ainer-starter-persistence",
            "        <dependency>\n"
                    + "            <groupId>dev.ainer</groupId>\n"
                    + "            <artifactId>ainer-starter-persistence</artifactId>\n"
                    + "        </dependency>\n",
            "dev.ainer:ainer-starter-security",
            "        <dependency>\n"
                    + "            <groupId>dev.ainer</groupId>\n"
                    + "            <artifactId>ainer-starter-security</artifactId>\n"
                    + "        </dependency>\n");

    private static final String POSTGRES_STARTER =
            "        <dependency>\n"
                    + "            <groupId>dev.ainer</groupId>\n"
                    + "            <artifactId>ainer-starter-persistence</artifactId>\n"
                    + "        </dependency>\n";

    private static final String DATABASE_DEPENDENCIES =
            "        <dependency>\n"
                    + "            <groupId>org.postgresql</groupId>\n"
                    + "            <artifactId>postgresql</artifactId>\n"
                    + "            <scope>runtime</scope>\n"
                    + "        </dependency>\n"
                    + "        <dependency>\n"
                    + "            <groupId>org.testcontainers</groupId>\n"
                    + "            <artifactId>testcontainers-postgresql</artifactId>\n"
                    + "            <scope>test</scope>\n"
                    + "        </dependency>\n"
                    + "        <dependency>\n"
                    + "            <groupId>org.testcontainers</groupId>\n"
                    + "            <artifactId>testcontainers-junit-jupiter</artifactId>\n"
                    + "            <scope>test</scope>\n"
                    + "        </dependency>\n";

    private static final String DATABASE_CONFIG =
            "  datasource:\n"
                    + "    url: ${DATASOURCE_URL}\n"
                    + "    username: ${DATASOURCE_USERNAME}\n"
                    + "    password: ${DATASOURCE_PASSWORD}\n";

    private final TemplateRenderer renderer;
    private final String applicationClassName;
    private final String packagePath;
    private final boolean database;

    public ProjectGenerator(ManifestV1 manifest) {
        Objects.requireNonNull(manifest, "manifest");
        this.applicationClassName = applicationClassName(manifest);
        this.packagePath = manifest.resolvedPackageName().replace('.', '/');
        this.database = manifest.database() == ManifestV1.Database.POSTGRESQL;
        this.renderer = buildRenderer(manifest);
    }

    /** Renders the complete project tree for the manifest. Deterministic across runs. */
    public ProjectTree generate() {
        List<GeneratedFile> files = new ArrayList<>();
        files.add(render("pom.xml", "pom.xml"));
        files.add(render("Application.java", "src/main/java/" + packagePath + "/" + applicationClassName + "Application.java"));
        files.add(render("PingController.java", "src/main/java/" + packagePath + "/ping/PingController.java"));
        files.add(render("application.yml", "src/main/resources/application.yml"));
        files.add(render(smokeTestTemplate(),
                "src/test/java/" + packagePath + "/" + applicationClassName + "ApplicationSmokeTest.java"));
        files.add(render(".gitignore.tpl", ".gitignore"));
        files.add(render("README.md", "README.md"));
        return new ProjectTree(files);
    }

    private String smokeTestTemplate() {
        return database ? "ApplicationPostgresSmokeTest.java" : "ApplicationSmokeTest.java";
    }

    private GeneratedFile render(String templateName, String targetPath) {
        String template = loadTemplate(templateName);
        String rendered = renderer.render(template, templateName);
        return new GeneratedFile(targetPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    public String packagePath() {
        return packagePath;
    }

    public String applicationClassName() {
        return applicationClassName;
    }

    private TemplateRenderer buildRenderer(ManifestV1 manifest) {
        String packageName = manifest.resolvedPackageName();
        String packagePath = packageName.replace('.', '/');
        String projectDescription = manifest.project().description() != null
                ? manifest.project().description()
                : manifest.project().name();
        String ownerBlock = ownerBlock(manifest);

        TemplateRenderer.Builder builder = TemplateRenderer.builder()
                .put("project.groupId", manifest.project().groupId())
                .put("project.artifactId", manifest.project().artifactId())
                .put("project.version", manifest.project().version())
                .put("project.name", manifest.project().name())
                .put("project.description", projectDescription)
                .put("java.release", String.valueOf(manifest.javaRelease()))
                .put("spring.boot.version", manifest.springBootVersion())
                .put("ainner.version", manifest.ainerVersion())
                .put("package.name", packageName)
                .put("package.path", packagePath)
                .put("application.className", applicationClassName)
                .put("owner.block", ownerBlock)
                .put("extra.starters", extraStartersBlock(manifest))
                .put("database.dependencies", database
                        ? POSTGRES_STARTER + DATABASE_DEPENDENCIES
                        : "")
                .put("database.config", database ? DATABASE_CONFIG : "");
        return builder.build();
    }

    private String applicationClassName(ManifestV1 manifest) {
        String artifactId = manifest.project().artifactId();
        String[] parts = artifactId.split("[-_.]");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0)));
                name.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        if (name.length() == 0) {
            throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                    "无法从 artifactId 派生应用类名: " + artifactId);
        }
        return name.toString();
    }

    private String extraStartersBlock(ManifestV1 manifest) {
        StringBuilder block = new StringBuilder();
        for (String starter : manifest.effectiveStarters()) {
            if (ManifestV1.FRAMEWORK_STARTER_WEB.equals(starter)) {
                continue;
            }
            if (database && "dev.ainer:ainer-starter-persistence".equals(starter)) {
                continue;
            }
            String dependency = EXTRA_STARTER_DEPENDENCY.get(starter);
            if (dependency == null) {
                throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                        "不支持的 starter: " + starter);
            }
            block.append(dependency);
        }
        return block.toString();
    }

    private String ownerBlock(ManifestV1 manifest) {
        if (manifest.owner() == null) {
            return "";
        }
        String displayName = manifest.owner().displayNameOrFallback();
        String email = manifest.owner().email();
        return email == null
                ? "\n\nMaintained by: " + displayName
                : "\n\nMaintained by: " + displayName + " <" + email + ">";
    }

    private String loadTemplate(String templateName) {
        String resource = TEMPLATE_ROOT + "/" + templateName;
        try (InputStream stream = ProjectGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                        "缺少内嵌模板: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取模板失败: " + resource, e);
        }
    }
}