package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import dev.ainer.initializer.manifest.EntityDeclaration;
import dev.ainer.initializer.manifest.EntityField;
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
    private final ManifestV1 manifest;
    private final String applicationClassName;
    private final String packagePath;
    private final boolean database;
    private final List<EntityDeclaration> entities;

    public ProjectGenerator(ManifestV1 manifest) {
        Objects.requireNonNull(manifest, "manifest");
        this.manifest = manifest;
        this.applicationClassName = applicationClassName(manifest);
        this.packagePath = manifest.resolvedPackageName().replace('.', '/');
        this.database = manifest.database() == ManifestV1.Database.POSTGRESQL;
        this.entities = manifest.entities();
        this.renderer = buildRenderer(manifest).build();
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
        files.addAll(entityFiles());
        return new ProjectTree(files);
    }

    private List<GeneratedFile> entityFiles() {
        if (!database || entities.isEmpty()) {
            return List.of();
        }
        List<GeneratedFile> files = new ArrayList<>();
        int migrationVersion = 1;
        for (EntityDeclaration entity : entities) {
            String prefix = "src/main/java/" + packagePath + "/crud/" + entity.className();
            files.add(renderEntity("entity/V1__init.sql",
                    "src/main/resources/db/migration/V" + migrationVersion + "__init.sql", entity));
            migrationVersion++;
            files.add(renderEntity("entity/Entity.java", prefix + "Entity.java", entity));
            files.add(renderEntity("entity/Mapper.java", prefix + "Mapper.java", entity));
            files.add(renderEntity("entity/ApplicationService.java", prefix + "ApplicationService.java", entity));
            files.add(renderEntity("entity/Controller.java", prefix + "Controller.java", entity));
            files.add(renderEntity("entity/CrudIntegrationTest.java",
                    "src/test/java/" + packagePath + "/crud/" + entity.className() + "CrudIntegrationTest.java", entity));
        }
        return files;
    }

    private String smokeTestTemplate() {
        return database ? "ApplicationPostgresSmokeTest.java" : "ApplicationSmokeTest.java";
    }

    private GeneratedFile render(String templateName, String targetPath) {
        String template = loadTemplate(templateName);
        String rendered = renderer.render(template, templateName);
        return new GeneratedFile(targetPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    private GeneratedFile renderEntity(String templateName, String targetPath, EntityDeclaration entity) {
        String template = loadTemplate(templateName);
        TemplateRenderer.Builder builder = perEntityBuilder(entity);
        String rendered = builder.build().render(template, templateName);
        return new GeneratedFile(targetPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    private TemplateRenderer.Builder perEntityBuilder(EntityDeclaration entity) {
        String tableName = entity.tableName();
        StringBuilder fields = new StringBuilder();
        StringBuilder accessors = new StringBuilder();
        StringBuilder columns = new StringBuilder();
        StringBuilder insertColumns = new StringBuilder();
        StringBuilder insertParams = new StringBuilder();
        StringBuilder constraints = new StringBuilder();
        StringBuilder comments = new StringBuilder();
        int index = 0;
        for (EntityField field : entity.fields()) {
            String javaType = javaType(field.type());
            String column = field.columnName();
            if (index > 0) {
                fields.append('\n');
                accessors.append('\n');
                columns.append("    ");
                insertColumns.append(", ");
                insertParams.append(", ");
            }
            fields.append("    private ").append(javaType).append(' ').append(field.javaName()).append(';');
            accessors.append("    public ").append(javaType).append(" get")
                    .append(capitalize(field.javaName())).append("() {\n")
                    .append("        return ").append(field.javaName()).append(";\n")
                    .append("    }\n\n")
                    .append("    public void set").append(capitalize(field.javaName())).append('(')
                    .append(javaType).append(' ').append(field.javaName()).append(") {\n")
                    .append("        this.").append(field.javaName()).append(" = ").append(field.javaName())
                    .append(";\n")
                    .append("    }");
            columns.append(column).append(' ').append(columnType(field))
                    .append(field.nullable() ? " NULL" : " NOT NULL").append(',');
            insertColumns.append(column);
            insertParams.append("#{").append(field.javaName()).append('}');
            if (field.unique()) {
                constraints.append(",\n    CONSTRAINT uq_").append(tableName).append('_').append(column)
                        .append(" UNIQUE (").append(column).append(')');
            }
            comments.append("COMMENT ON COLUMN ").append(tableName).append('.').append(column)
                    .append(" IS '").append(field.commentOrDefault()).append("';\n");
            index++;
        }
        StringBuilder imports = new StringBuilder();
        if (containsDecimal(entity)) {
            imports.append("import java.math.BigDecimal;\n");
        }
        return buildRenderer(manifest)
                .put("entity.fields", fields.toString().strip())
                .put("entity.accessors", accessors.toString().strip())
                .put("entity.columns", columns.toString().strip())
                .put("entity.insertColumns", insertColumns.append(", created_at, updated_at").toString().strip())
                .put("entity.insertParams", insertParams.append(", #{createdAt}, #{updatedAt}").toString().strip())
                .put("entity.constraints", constraints.toString())
                .put("entity.comments", comments.toString().strip())
                .put("entity.imports", imports.toString().strip())
                .put("entity.className", entity.className())
                .put("table.name", tableName)
                .put("resource.path", entity.resourcePath())
                .put("test.crudBody", crudTestBody(entity));
    }

    public String packagePath() {
        return packagePath;
    }

    public String applicationClassName() {
        return applicationClassName;
    }

    private TemplateRenderer.Builder buildRenderer(ManifestV1 manifest) {
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
        return builder;
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

    private String javaType(EntityDeclaration.FieldType type) {
        return switch (type) {
            case STRING, TEXT -> "String";
            case INT -> "Integer";
            case LONG -> "Long";
            case DECIMAL -> "BigDecimal";
            case BOOLEAN -> "Boolean";
            case INSTANT -> "Instant";
            case UUID -> "UUID";
        };
    }

    private String columnType(EntityField field) {
        return switch (field.type()) {
            case STRING -> "varchar(" + field.size() + ")";
            case TEXT -> "text";
            case INT -> "integer";
            case LONG -> "bigint";
            case DECIMAL -> "numeric(19,4)";
            case BOOLEAN -> "boolean";
            case INSTANT -> "timestamptz";
            case UUID -> "uuid";
        };
    }

    private boolean containsDecimal(EntityDeclaration entity) {
        return entity.fields().stream().anyMatch(field -> field.type() == EntityDeclaration.FieldType.DECIMAL);
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String crudTestBody(EntityDeclaration entity) {
        String path = entity.resourcePath();
        String payload = samplePayload(entity, "created");
        String updatePayload = sampleUpdatePayload(entity);
        EntityField first = entity.fields().getFirst();
        String firstCreatedValue = sampleValue(first, "created");
        String firstUpdatedValue = sampleValue(first, "updated");
        return """
                String createdPayload = %s;
                String updatePayload = %s;
                ResponseEntity<String> created = restTemplate.postForEntity(url("/api/%s"), json(createdPayload), String.class);
                assertThat(created.getStatusCode().value()).isEqualTo(201);
                assertThat(created.getHeaders().getFirst("X-Request-Id")).isNotBlank();
                String id = JsonPath.parse(created.getBody()).read("$.data.id", String.class);
                assertThat(id).isNotBlank();

                ResponseEntity<String> fetched = restTemplate.getForEntity(url("/api/%s/" + id), String.class);
                assertThat(fetched.getStatusCode().value()).isEqualTo(200);
                assertThat(fetched.getBody()).contains("%s");

                ResponseEntity<String> updated = restTemplate.exchange(url("/api/%s/" + id), HttpMethod.PUT, json(updatePayload), String.class);
                assertThat(updated.getStatusCode().value()).isEqualTo(200);
                assertThat(updated.getBody()).contains("%s");

                ResponseEntity<String> listed = restTemplate.getForEntity(url("/api/%s"), String.class);
                assertThat(listed.getStatusCode().value()).isEqualTo(200);
                assertThat(listed.getBody()).contains(id);

                restTemplate.delete(url("/api/%s/" + id));
                ResponseEntity<String> afterDelete = restTemplate.getForEntity(url("/api/%s/" + id), String.class);
                assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);
                """.formatted(jsonLiteral(payload), jsonLiteral(updatePayload),
                path, path, firstCreatedValue,
                path, firstUpdatedValue,
                path, path, path);
    }

    private String jsonLiteral(String json) {
        return "\"" + json.replace("\"", "\\\"") + "\"";
    }

    private String samplePayload(EntityDeclaration entity, String suffix) {
        StringBuilder json = new StringBuilder("{");
        int index = 0;
        for (EntityField field : entity.fields()) {
            if (index > 0) {
                json.append(',');
            }
            json.append('"').append(field.javaName()).append("\":").append(sampleJsonValue(field, suffix));
            index++;
        }
        return json.append('}').toString();
    }

    private String sampleUpdatePayload(EntityDeclaration entity) {
        EntityField first = entity.fields().getFirst();
        return "{" + '"' + first.javaName() + '"' + ":" + sampleJsonValue(first, "updated") + "}";
    }

    private String sampleJsonValue(EntityField field, String suffix) {
        return switch (field.type()) {
            case INT, LONG -> "42";
            case DECIMAL -> "42.5";
            case BOOLEAN -> "true";
            case INSTANT -> "\"2026-08-09T00:00:00Z\"";
            case UUID -> "\"00000000-0000-0000-0000-00000000000" + suffix.charAt(0) + "\"";
            default -> '"' + paddedSample(field, suffix) + '"';
        };
    }

    private String paddedSample(EntityField field, String suffix) {
        String value = field.name() + "-" + suffix;
        if (field.size() != null && value.length() > field.size()) {
            return value.substring(0, field.size());
        }
        return value;
    }

    private String sampleValue(EntityField field, String suffix) {
        return switch (field.type()) {
            case INT, LONG -> "42";
            case DECIMAL -> "42.5";
            case BOOLEAN -> "true";
            case INSTANT -> "2026-08-09T00:00:00Z";
            case UUID -> "00000000-0000-0000-0000-00000000000" + suffix.charAt(0);
            default -> paddedSample(field, suffix);
        };
    }
}