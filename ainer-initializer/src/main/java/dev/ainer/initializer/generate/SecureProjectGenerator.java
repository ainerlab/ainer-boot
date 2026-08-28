package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import dev.ainer.initializer.manifest.EntityDeclaration;
import dev.ainer.initializer.manifest.EntityField;
import dev.ainer.initializer.manifest.ManifestV1;
import dev.ainer.initializer.manifest.ManifestV2;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Manifest v2 {@code simple-service + workspace} 预设的确定性生成器。
 *
 * <p>与面向兼容的 v1 CRUD 模板不同，本生成器输出显式 API/application/persistence 边界、
 * Workspace 范围 SQL、受控分页、乐观锁、稳定产品错误、安全决策审计和真 JWT 负向测试。
 */
public final class SecureProjectGenerator {

    private static final String TEMPLATE_ROOT = "templates/v2";
    private static final String COMMON_TEMPLATE_ROOT = "templates/v1";
    private static final Set<String> GENERATED_COLUMNS = Set.of(
            "id", "workspace_id", "version", "created_by_subject_id", "updated_by_subject_id",
            "created_at", "updated_at");

    private final ManifestV2 manifest;
    private final String packagePath;
    private final String applicationClassName;
    private final TemplateRenderer projectRenderer;

    public SecureProjectGenerator(ManifestV2 manifest) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.packagePath = manifest.packagePath();
        this.applicationClassName = applicationClassName(manifest.project().artifactId());
        this.projectRenderer = projectRenderer().build();
        validateIdentifiers();
    }

    public ProjectTree generate() {
        List<GeneratedFile> files = new ArrayList<>();
        files.add(render("pom.xml", "pom.xml"));
        files.add(commonStatic("mvnw", "mvnw", true));
        files.add(commonStatic("mvnw.cmd", "mvnw.cmd", false));
        files.add(commonStatic("maven-wrapper.properties",
                ".mvn/wrapper/maven-wrapper.properties", false));
        files.add(render("Application.java", sourceRoot() + "/" + applicationClassName + "Application.java"));
        files.add(render("PingController.java", sourceRoot() + "/ping/PingController.java"));
        files.add(render("application.yml", "src/main/resources/application.yml"));
        files.add(render("ApplicationSecurePostgresSmokeTest.java",
                testRoot() + "/" + applicationClassName + "ApplicationSmokeTest.java"));
        files.add(render("SecureTestConfiguration.java",
                testRoot() + "/support/SecureTestConfiguration.java"));
        files.add(commonRender(".gitignore.tpl", ".gitignore"));
        files.add(render("README.md", "README.md"));
        files.addAll(entityFiles(1));
        return new ProjectTree(files);
    }

    /**
     * 生成可叠加到已有 Ainer Maven 项目的安全纵向切片。项目级 POM、主应用、README、
     * application.yml 与 Wrapper 不进入结果；调用方负责先完成已有项目预检和 POM 依赖合并。
     *
     * @param migrationVersion 第一个实体使用的显式 Flyway 数字版本
     */
    public ProjectTree generateAdditive(long migrationVersion) {
        if (migrationVersion < 1) {
            throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                    "migration version 必须是正整数，收到: " + migrationVersion);
        }
        long additionalVersions = manifest.entities().size() - 1L;
        if (migrationVersion > Long.MAX_VALUE - additionalVersions) {
            throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                    "migration version 无法容纳全部实体，起始版本: " + migrationVersion);
        }
        List<GeneratedFile> files = new ArrayList<>();
        files.add(render("ExistingProjectConfiguration.java",
                sourceRoot() + "/initializer/AinerInitializerWorkspaceConfiguration.java"));
        files.add(render("SecureTestConfiguration.java",
                testRoot() + "/support/SecureTestConfiguration.java"));
        files.addAll(entityFiles(migrationVersion));
        return new ProjectTree(files);
    }

    public String packagePath() {
        return packagePath;
    }

    public String applicationClassName() {
        return applicationClassName;
    }

    private List<GeneratedFile> entityFiles(long firstMigrationVersion) {
        List<GeneratedFile> files = new ArrayList<>();
        long migrationVersion = firstMigrationVersion;
        for (int entityIndex = 0; entityIndex < manifest.entities().size(); entityIndex++) {
            EntityDeclaration entity = manifest.entities().get(entityIndex);
            String feature = featurePackage(entity);
            String main = sourceRoot() + "/" + feature;
            String test = testRoot() + "/" + feature;
            files.add(renderEntity("entity/V1__secure_resource.sql",
                    "src/main/resources/db/migration/V" + migrationVersion + "__secure_"
                            + secureTableName(entity) + ".sql", entity));
            files.add(renderEntity("entity/ApiDtos.java", main + "/api/"
                    + entity.className() + "ApiDtos.java", entity));
            files.add(renderEntity("entity/Controller.java", main + "/api/"
                    + entity.className() + "Controller.java", entity));
            files.add(renderEntity("entity/ApplicationService.java", main + "/application/"
                    + entity.className() + "ApplicationService.java", entity));
            files.add(renderEntity("entity/Commands.java", main + "/application/"
                    + entity.className() + "Commands.java", entity));
            files.add(renderEntity("entity/Record.java", main + "/application/"
                    + entity.className() + "Record.java", entity));
            files.add(renderEntity("entity/Page.java", main + "/application/"
                    + entity.className() + "Page.java", entity));
            files.add(renderEntity("entity/AccessAuditService.java", main + "/application/"
                    + entity.className() + "AccessAuditService.java", entity));
            files.add(renderEntity("entity/ErrorCode.java", main + "/application/"
                    + entity.className() + "ErrorCode.java", entity));
            files.add(renderEntity("entity/Row.java", main + "/infrastructure/"
                    + entity.className() + "Row.java", entity));
            files.add(renderEntity("entity/Mapper.java", main + "/infrastructure/"
                    + entity.className() + "Mapper.java", entity));
            files.add(renderEntity("entity/SecureCrudIntegrationTest.java", test + "/"
                    + entity.className() + "SecureCrudIntegrationTest.java", entity));
            if (entityIndex + 1 < manifest.entities().size()) {
                migrationVersion++;
            }
        }
        return files;
    }

    private GeneratedFile render(String templateName, String targetPath) {
        return generated(targetPath, projectRenderer.render(load(TEMPLATE_ROOT, templateName), templateName));
    }

    private GeneratedFile commonRender(String templateName, String targetPath) {
        return generated(targetPath, projectRenderer.render(load(COMMON_TEMPLATE_ROOT, templateName), templateName));
    }

    private GeneratedFile commonStatic(String templateName, String targetPath, boolean executable) {
        return new GeneratedFile(targetPath,
                load(COMMON_TEMPLATE_ROOT, templateName).getBytes(StandardCharsets.UTF_8), executable);
    }

    private GeneratedFile renderEntity(String templateName, String targetPath, EntityDeclaration entity) {
        String rendered = entityRenderer(entity).build()
                .render(load(TEMPLATE_ROOT, templateName), templateName);
        return generated(targetPath, rendered);
    }

    private GeneratedFile generated(String targetPath, String rendered) {
        return new GeneratedFile(targetPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    private TemplateRenderer.Builder projectRenderer() {
        String description = manifest.project().description() == null
                ? manifest.project().name()
                : manifest.project().description();
        return TemplateRenderer.builder()
                .put("project.groupId", manifest.project().groupId())
                .put("project.artifactId", manifest.project().artifactId())
                .put("project.version", manifest.project().version())
                .put("project.name", manifest.project().name())
                .put("project.description", description)
                .put("project.errorNamespace", manifest.errorNamespace())
                .put("java.release", String.valueOf(manifest.javaRelease()))
                .put("spring.boot.version", manifest.springBootVersion())
                .put("ainner.version", manifest.ainerVersion())
                .put("package.name", manifest.resolvedPackageName())
                .put("package.path", packagePath)
                .put("application.className", applicationClassName)
                .put("owner.block", ownerBlock())
                .put("extra.starters", extraStartersBlock());
    }

    private TemplateRenderer.Builder entityRenderer(EntityDeclaration entity) {
        TemplateRenderer.Builder builder = projectRenderer();
        FieldFragments fragments = fieldFragments(entity);
        String table = secureTableName(entity);
        String auditTable = table + "_access_audit";
        return builder
                .put("entity.className", entity.className())
                .put("entity.package", featurePackage(entity).replace('/', '.'))
                .put("entity.errorSegment", upperSnake(entity.name()))
                .put("entity.scope.read", entity.resourcePath() + ".read")
                .put("entity.scope.write", entity.resourcePath() + ".write")
                .put("resource.path", entity.resourcePath())
                .put("table.name", table)
                .put("audit.table.name", auditTable)
                .put("table.versionConstraint", postgresIdentifier("ck_" + table + "_version"))
                .put("table.pageIndex", postgresIdentifier("idx_" + table + "_workspace_page"))
                .put("audit.decisionConstraint",
                        postgresIdentifier("ck_" + auditTable + "_decision"))
                .put("audit.workspaceTimeIndex",
                        postgresIdentifier("idx_" + auditTable + "_workspace_time"))
                .put("entity.sqlColumns", fragments.sqlColumns())
                .put("entity.sqlComments", fragments.sqlComments())
                .put("entity.uniqueConstraints", fragments.uniqueConstraints())
                .put("entity.rowFields", fragments.rowFields())
                .put("entity.rowAccessors", fragments.rowAccessors())
                .put("entity.selectColumns", fragments.selectColumns())
                .put("entity.insertColumns", fragments.insertColumns())
                .put("entity.insertParams", fragments.insertParams())
                .put("entity.updateAssignments", fragments.updateAssignments())
                .put("entity.createComponents", fragments.createComponents())
                .put("entity.updateComponents", fragments.updateComponents())
                .put("entity.responseComponents", fragments.responseComponents())
                .put("entity.commandComponents", fragments.commandComponents())
                .put("entity.requestValues", fragments.requestValues())
                .put("entity.applyCreate", fragments.applyCreate())
                .put("entity.applyUpdate", fragments.applyUpdate())
                .put("entity.responseValues", fragments.responseValues())
                .put("entity.recordValues", fragments.recordValues())
                .put("test.createdPayload", javaLiteral(samplePayload(entity, "created", false)))
                .put("test.updatedPayload", javaLiteral(samplePayload(entity, "updated", true)))
                .put("test.firstCreatedValue", sampleValue(entity.fields().getFirst(), "created"))
                .put("test.firstUpdatedValue", sampleValue(entity.fields().getFirst(), "updated"));
    }

    private FieldFragments fieldFragments(EntityDeclaration entity) {
        StringBuilder sqlColumns = new StringBuilder();
        StringBuilder sqlComments = new StringBuilder();
        StringBuilder uniqueConstraints = new StringBuilder();
        StringBuilder rowFields = new StringBuilder();
        StringBuilder rowAccessors = new StringBuilder();
        StringBuilder selectColumns = new StringBuilder();
        StringBuilder insertColumns = new StringBuilder();
        StringBuilder insertParams = new StringBuilder();
        StringBuilder updateAssignments = new StringBuilder();
        StringBuilder createComponents = new StringBuilder();
        StringBuilder updateComponents = new StringBuilder();
        StringBuilder responseComponents = new StringBuilder();
        StringBuilder commandComponents = new StringBuilder();
        StringBuilder requestValues = new StringBuilder();
        StringBuilder applyCreate = new StringBuilder();
        StringBuilder applyUpdate = new StringBuilder();
        StringBuilder responseValues = new StringBuilder();
        StringBuilder recordValues = new StringBuilder();

        int index = 0;
        for (EntityField field : entity.fields()) {
            String javaName = field.javaName();
            String column = field.columnName();
            String javaType = javaType(field.type());
            if (index > 0) {
                sqlColumns.append('\n');
                rowFields.append('\n');
                rowAccessors.append('\n');
                createComponents.append(",\n");
                updateComponents.append(",\n");
                responseComponents.append(",\n");
                commandComponents.append(",\n");
                requestValues.append(",\n");
                applyCreate.append('\n');
                applyUpdate.append('\n');
                responseValues.append(",\n");
                recordValues.append(",\n");
            }
            sqlColumns.append("    ").append(column).append(' ').append(columnType(field))
                    .append(field.nullable() ? " NULL," : " NOT NULL,");
            sqlComments.append("COMMENT ON COLUMN ").append(secureTableName(entity)).append('.')
                    .append(column).append(" IS '")
                    .append(escapeSqlLiteral(field.commentOrDefault())).append("';\n");
            if (field.unique()) {
                uniqueConstraints.append(",\n    CONSTRAINT ").append(postgresIdentifier(
                                "uq_" + secureTableName(entity) + "_" + column))
                        .append(" UNIQUE (workspace_id, ").append(column).append(')');
            }
            rowFields.append("    private ").append(javaType).append(' ').append(javaName).append(';');
            rowAccessors.append(accessors(javaType, javaName));
            selectColumns.append(", ").append(column);
            insertColumns.append(", ").append(column);
            insertParams.append(", #{row.").append(javaName).append('}');
            updateAssignments.append("            ").append(column).append(" = #{row.")
                    .append(javaName).append("},\n");
            createComponents.append("            ").append(validationAnnotations(field))
                    .append(javaType).append(' ').append(javaName);
            updateComponents.append("            ").append(validationAnnotations(field))
                    .append(javaType).append(' ').append(javaName);
            responseComponents.append("            ").append(javaType).append(' ').append(javaName);
            commandComponents.append("            ").append(javaType).append(' ').append(javaName);
            requestValues.append("                    ").append(javaName).append("()");
            applyCreate.append("        row.set").append(capitalize(javaName)).append("(command.")
                    .append(javaName).append("());");
            applyUpdate.append("        row.set").append(capitalize(javaName)).append("(command.")
                    .append(javaName).append("());");
            responseValues.append("                row.get").append(capitalize(javaName)).append("()");
            recordValues.append("                    record.").append(javaName).append("()");
            index++;
        }
        updateComponents.append(",\n            @jakarta.validation.constraints.PositiveOrZero long version");

        return new FieldFragments(
                sqlColumns.toString(), sqlComments.toString().strip(), uniqueConstraints.toString(),
                rowFields.toString(), rowAccessors.toString(), selectColumns.toString(),
                insertColumns.toString(), insertParams.toString(), updateAssignments.toString(),
                createComponents.toString(), updateComponents.toString(), responseComponents.toString(),
                commandComponents.toString(), requestValues.toString(), applyCreate.toString(),
                applyUpdate.toString(), responseValues.toString(), recordValues.toString());
    }

    private String validationAnnotations(EntityField field) {
        StringBuilder value = new StringBuilder();
        if (!field.nullable()) {
            if (field.type() == EntityDeclaration.FieldType.STRING
                    || field.type() == EntityDeclaration.FieldType.TEXT) {
                value.append("@jakarta.validation.constraints.NotBlank ");
            } else {
                value.append("@jakarta.validation.constraints.NotNull ");
            }
        }
        if (field.type() == EntityDeclaration.FieldType.STRING) {
            value.append("@jakarta.validation.constraints.Size(max = ")
                    .append(field.size()).append(") ");
        }
        return value.toString();
    }

    private String accessors(String type, String name) {
        String cap = capitalize(name);
        return "    public " + type + " get" + cap + "() {\n"
                + "        return " + name + ";\n"
                + "    }\n\n"
                + "    public void set" + cap + "(" + type + " " + name + ") {\n"
                + "        this." + name + " = " + name + ";\n"
                + "    }";
    }

    private String extraStartersBlock() {
        StringBuilder block = new StringBuilder();
        for (String starter : manifest.effectiveStarters()) {
            if (ManifestV1.FRAMEWORK_STARTER_WEB.equals(starter)
                    || "dev.ainer:ainer-starter-persistence".equals(starter)
                    || "dev.ainer:ainer-starter-security".equals(starter)) {
                continue;
            }
            if (!"dev.ainer:ainer-starter-observability".equals(starter)
                    && !"dev.ainer:ainer-starter-cache".equals(starter)) {
                throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                        "Manifest v2 不支持的额外 starter: " + starter);
            }
            String[] coordinate = starter.split(":", 2);
            block.append("        <dependency>\n")
                    .append("            <groupId>").append(coordinate[0]).append("</groupId>\n")
                    .append("            <artifactId>").append(coordinate[1]).append("</artifactId>\n")
                    .append("        </dependency>\n");
        }
        return block.toString().stripTrailing();
    }

    private String ownerBlock() {
        if (manifest.owner() == null) {
            return "";
        }
        String displayName = manifest.owner().displayNameOrFallback();
        return manifest.owner().email() == null
                ? "\n\nMaintained by: " + displayName
                : "\n\nMaintained by: " + displayName + " <" + manifest.owner().email() + ">";
    }

    private String sourceRoot() {
        return "src/main/java/" + packagePath;
    }

    private String testRoot() {
        return "src/test/java/" + packagePath;
    }

    private String featurePackage(EntityDeclaration entity) {
        return lowerCamel(entity.className());
    }

    private String secureTableName(EntityDeclaration entity) {
        return snakeCase(manifest.project().artifactId()) + "_" + snakeCase(entity.name());
    }

    private void validateIdentifiers() {
        for (EntityDeclaration entity : manifest.entities()) {
            String table = secureTableName(entity);
            if (table.length() > 63 || (table + "_access_audit").length() > 63) {
                throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                        "项目与实体名生成的 PostgreSQL 标识符过长: " + table);
            }
            Set<String> columns = new HashSet<>();
            Set<String> javaFields = new HashSet<>();
            for (EntityField field : entity.fields()) {
                String column = field.columnName();
                if (column.length() > 63) {
                    throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                            "字段生成的 PostgreSQL 标识符过长: " + column);
                }
                if (GENERATED_COLUMNS.contains(column)) {
                    throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                            "字段与 Manifest v2 内建列重名: " + column);
                }
                if (!columns.add(column)) {
                    throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                            "多个字段生成了同一个 PostgreSQL 列名: " + column);
                }
                if (!javaFields.add(field.javaName())) {
                    throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                            "多个字段生成了同一个 Java 字段名: " + field.javaName());
                }
            }
        }
    }

    private String load(String root, String templateName) {
        String resource = root + "/" + templateName;
        try (InputStream stream = SecureProjectGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                        "缺少内嵌模板: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("读取模板失败: " + resource, exception);
        }
    }

    private String applicationClassName(String artifactId) {
        StringBuilder name = new StringBuilder();
        for (String part : artifactId.split("[-_.]")) {
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        if (name.isEmpty()) {
            throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST,
                    "无法从 artifactId 派生应用类名: " + artifactId);
        }
        return name.toString();
    }

    private String javaType(EntityDeclaration.FieldType type) {
        return switch (type) {
            case STRING, TEXT -> "String";
            case INT -> "Integer";
            case LONG -> "Long";
            case DECIMAL -> "java.math.BigDecimal";
            case BOOLEAN -> "Boolean";
            case INSTANT -> "java.time.Instant";
            case UUID -> "java.util.UUID";
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

    private String samplePayload(EntityDeclaration entity, String suffix, boolean includeVersion) {
        StringBuilder json = new StringBuilder("{");
        int index = 0;
        for (EntityField field : entity.fields()) {
            if (index++ > 0) {
                json.append(',');
            }
            json.append('"').append(field.javaName()).append("\":")
                    .append(sampleJsonValue(field, suffix));
        }
        if (includeVersion) {
            json.append(",\"version\":0");
        }
        return json.append('}').toString();
    }

    private String sampleJsonValue(EntityField field, String suffix) {
        return switch (field.type()) {
            case INT, LONG -> "42";
            case DECIMAL -> "42.5";
            case BOOLEAN -> "true";
            case INSTANT -> "\"2026-08-27T00:00:00Z\"";
            case UUID -> "\"00000000-0000-0000-0000-00000000000" + sampleUuidNibble(suffix) + "\"";
            default -> '"' + paddedSample(field, suffix) + '"';
        };
    }

    private String sampleValue(EntityField field, String suffix) {
        return switch (field.type()) {
            case INT, LONG -> "42";
            case DECIMAL -> "42.5";
            case BOOLEAN -> "true";
            case INSTANT -> "2026-08-27T00:00:00Z";
            case UUID -> "00000000-0000-0000-0000-00000000000" + sampleUuidNibble(suffix);
            default -> paddedSample(field, suffix);
        };
    }

    private String paddedSample(EntityField field, String suffix) {
        String value = field.name() + "-" + suffix;
        return field.size() != null && value.length() > field.size()
                ? value.substring(0, field.size())
                : value;
    }

    private static String javaLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String lowerCamel(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String upperSnake(String value) {
        return snakeCase(value).toUpperCase(Locale.ROOT);
    }

    private static String snakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        char previous = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '-' || current == '.') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
            } else if (Character.isUpperCase(current)) {
                if (!builder.isEmpty() && previous != '_' && !Character.isUpperCase(previous)) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(Character.toLowerCase(current));
            }
            previous = current;
        }
        return builder.toString();
    }

    private static char sampleUuidNibble(String suffix) {
        return "updated".equals(suffix) ? '2' : '1';
    }

    private static String postgresIdentifier(String value) {
        if (value.length() <= 63) {
            return value;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            String suffix = HexFormat.of().formatHex(digest, 0, 6);
            return value.substring(0, 50) + "_" + suffix;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private record FieldFragments(
            String sqlColumns,
            String sqlComments,
            String uniqueConstraints,
            String rowFields,
            String rowAccessors,
            String selectColumns,
            String insertColumns,
            String insertParams,
            String updateAssignments,
            String createComponents,
            String updateComponents,
            String responseComponents,
            String commandComponents,
            String requestValues,
            String applyCreate,
            String applyUpdate,
            String responseValues,
            String recordValues) {
    }
}
