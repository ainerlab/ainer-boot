package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses and validates a Manifest v1 document. Fail-fast: structural errors, unknown fields,
 * unknown starters and template look-alikes are reported before any generation starts.
 */
public final class ManifestReader {

    /** Boot versions the embedded generator templates are verified against. */
    public static final Set<String> SUPPORTED_SPRING_BOOT_VERSIONS = Set.of("4.1.0");

    private static final Pattern GROUP_ID_SEGMENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+){2}([.-][A-Za-z0-9.-]+)?$");
    private static final Pattern PACKAGE_SEGMENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final Set<String> KNOWN_TOP_LEVEL_KEYS = Set.of(
            "schemaVersion", "project", "java", "spring-boot", "ainner", "package", "starters",
            "database", "owner", "allowSnapshot");
    private static final Set<String> KNOWN_PROJECT_KEYS =
            Set.of("name", "groupId", "artifactId", "version", "description");
    private static final Set<String> KNOWN_OWNER_KEYS = Set.of("displayName", "email");

    private final Yaml yaml;

    public ManifestReader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        this.yaml = new Yaml(options);
    }

    /**
     * Reads a single YAML document and returns the validated manifest.
     *
     * @throws BusinessException with {@code AINER.INITIALIZER.INVALID_MANIFEST} for any
     *                           structural or semantic problem
     */
    public ManifestV1 read(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        return parseAndValidate(nextDocument(reader));
    }

    private Map<?, ?> nextDocument(Reader reader) throws IOException {
        try {
            Object document = yaml.load(reader);
            if (document == null) {
                fail("Manifest 内容不能为空");
            }
            if (document instanceof Map<?, ?> root) {
                return root;
            }
            fail("Manifest 必须是 YAML 映射");
            throw new AssertionError("unreachable");
        } catch (YAMLException e) {
            throw fail("YAML 解析失败: " + safeMessage(e));
        }
    }

    private @Nullable String safeMessage(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private ManifestV1 parseAndValidate(Map<?, ?> root) {
        rejectUnknownKeys(root, KNOWN_TOP_LEVEL_KEYS, "");

        String schema = stringOrNull(root, "schemaVersion");
        if (!ManifestV1.SCHEMA_VERSION.equals(schema)) {
            fail("schemaVersion 必须是 v1，收到: " + schema);
        }

        Map<?, ?> project = mapOrNull(root, "project");
        if (project == null) {
            fail("缺少 project 段落");
        }
        rejectUnknownKeys(project, KNOWN_PROJECT_KEYS, "project");

        String name = requiredString(project, "name", "project.name");
        checkLength(name, "project.name", 120);
        String groupId = requiredString(project, "groupId", "project.groupId");
        validateGroupId(groupId);
        String artifactId = requiredString(project, "artifactId", "project.artifactId");
        validateArtifactId(artifactId);
        String version = requiredString(project, "version", "project.version");
        validateSemver(version);
        String description = stringOrNull(project, "description");
        if (description != null && description.isBlank()) {
            description = null;
        }
        checkTemplateLiteral(description);

        int javaRelease = intOrFail(root, "java", "java.release");
        if (javaRelease != 25) {
            fail("java.release 仅支持 25，收到: " + javaRelease);
        }

        String springBootVersion = stringOrNull(root, "spring-boot");
        if (springBootVersion == null || springBootVersion.isBlank()) {
            fail("缺少 spring-boot.version");
        }
        if (!SUPPORTED_SPRING_BOOT_VERSIONS.contains(springBootVersion)) {
            fail("spring-boot.version 必须是受支持版本 " + SUPPORTED_SPRING_BOOT_VERSIONS
                    + "，收到: " + springBootVersion);
        }

        String ainerVersion = stringOrNull(root, "ainner");
        if (ainerVersion == null || ainerVersion.isBlank()) {
            fail("缺少 ainerVersion");
        }
        checkTemplateLiteral(ainerVersion);
        if (ainerVersion.endsWith("-SNAPSHOT") && !isTrue(root, "allowSnapshot")) {
            fail("ainnerVersion 是 SNAPSHOT，必须显式声明 allowSnapshot=true");
        }

        String packageName = stringOrNull(root, "package");
        if (packageName == null || packageName.isBlank()) {
            packageName = groupId;
        }
        validatePackageName(packageName);

        List<String> starters = startersOrEmpty(root);
        ManifestV1.Database database = parseDatabase(stringOrNull(root, "database"));
        Owner owner = parseOwner(mapOrNull(root, "owner"));

        return new ManifestV1(
                new ProjectInfo(name, groupId, artifactId, version, description),
                javaRelease,
                springBootVersion,
                ainerVersion,
                packageName,
                starters,
                database,
                owner);
    }

    private void rejectUnknownKeys(Map<?, ?> map, Set<String> allowed, String prefix) {
        Set<String> unknown = map.keySet().stream()
                .map(String::valueOf)
                .filter(key -> !allowed.contains(key))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            fail("未知字段 " + prefix + (prefix.isEmpty() ? "" : ".") + unknown + "，v1 不允许未知字段");
        }
    }

    private @Nullable String requiredString(Map<?, ?> map, String key, String field) {
        Object value = map.get(key);
        if (value == null) {
            fail("缺少必需字段 " + field);
        }
        if (!(value instanceof String s) || s.isBlank()) {
            fail(field + " 必须是非空字符串");
        }
        checkTemplateLiteral((String) value);
        return (String) value;
    }

    private @Nullable String stringOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        fail(key + " 必须是字符串");
        throw new AssertionError("unreachable");
    }

    private int intOrFail(Map<?, ?> map, String key, String field) {
        Object value = map.get(key);
        if (value == null) {
            fail(field + " 缺失");
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof String s && s.matches("\\d+")) {
            return Integer.parseInt(s);
        }
        fail(field + " 必须是整数");
        throw new AssertionError("unreachable");
    }

    private @Nullable Map<?, ?> mapOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> m) {
            return m;
        }
        fail(key + " 必须是映射");
        throw new AssertionError("unreachable");
    }

    private List<String> startersOrEmpty(Map<?, ?> root) {
        Object value = root.get("starters");
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String s)) {
                    fail("starters 元素必须是字符串");
                }
                String starter = (String) item;
                if (starter.isBlank()) {
                    fail("starters 元素不能为空");
                }
                checkTemplateLiteral(starter);
                if (!starter.contains(":")) {
                    fail("starter 必须是 groupId:artifactId 格式，收到: " + starter);
                }
                result.add(starter);
            }
            return List.copyOf(result);
        }
        fail("starters 必须是字符串数组");
        throw new AssertionError("unreachable");
    }

    private ManifestV1.Database parseDatabase(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return ManifestV1.Database.NONE;
        }
        try {
            return ManifestV1.Database.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            fail("database 必须是 none 或 postgresql，收到: " + value);
            throw new AssertionError("unreachable");
        }
    }

    private Owner parseOwner(@Nullable Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        rejectUnknownKeys(map, KNOWN_OWNER_KEYS, "owner");
        String displayName = stringOrNull(map, "displayName");
        String email = stringOrNull(map, "email");
        if (email != null && !email.contains("@")) {
            fail("owner.email 必须包含 @");
        }
        return new Owner(displayName, email);
    }

    private void validateGroupId(String groupId) {
        for (String segment : groupId.split("\\.")) {
            if (!GROUP_ID_SEGMENT.matcher(segment).matches()) {
                fail("project.groupId 每段必须匹配 " + GROUP_ID_SEGMENT + "，收到: " + groupId);
            }
        }
    }

    private void validateArtifactId(String artifactId) {
        if (!ARTIFACT_ID_PATTERN.matcher(artifactId).matches()) {
            fail("project.artifactId 必须匹配 " + ARTIFACT_ID_PATTERN + "，收到: " + artifactId);
        }
    }

    private void validateSemver(String version) {
        if (!SEMVER_PATTERN.matcher(version).matches()) {
            fail("project.version 必须匹配语义化版本 " + SEMVER_PATTERN + "，收到: " + version);
        }
    }

    private void validatePackageName(String packageName) {
        for (String segment : packageName.split("\\.")) {
            if (!PACKAGE_SEGMENT.matcher(segment).matches()) {
                fail("package 每段必须匹配 " + PACKAGE_SEGMENT + "，收到: " + packageName);
            }
        }
    }

    private void checkLength(String value, String field, int max) {
        if (value.length() > max) {
            fail(field + " 长度不能超过 " + max);
        }
    }

    private void checkTemplateLiteral(@Nullable String value) {
        if (value != null && (value.contains("{{") || value.contains("}}"))) {
            fail("文本字段不能包含模板占位符 {{ }}");
        }
    }

    private boolean isTrue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return Boolean.TRUE.equals(value);
    }

    private BusinessException fail(String message) {
        throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST, message);
    }
}