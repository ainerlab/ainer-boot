package dev.ainer.initializer.integrate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 对已有 Maven POM 做有限、可重复的依赖与编译参数合并。 */
public final class MavenPomEditor {

    private static final List<Dependency> REQUIRED_DEPENDENCIES = List.of(
            new Dependency("dev.ainer", "ainer-starter-web", null),
            new Dependency("dev.ainer", "ainer-starter-persistence", null),
            new Dependency("dev.ainer", "ainer-starter-security", null),
            new Dependency("dev.ainer", "ainer-module-workspace", null),
            new Dependency("dev.ainer", "ainer-module-authorization", null),
            new Dependency("org.springframework.boot", "spring-boot-starter-validation", null),
            new Dependency("org.springdoc", "springdoc-openapi-starter-webmvc-ui", null),
            new Dependency("org.postgresql", "postgresql", "runtime"),
            new Dependency("org.springframework.boot", "spring-boot-starter-test", "test"),
            new Dependency("org.springframework.boot", "spring-boot-starter-webmvc-test", "test"),
            new Dependency("dev.ainer", "ainer-test-support", "test"),
            new Dependency("org.testcontainers", "testcontainers-postgresql", "test"),
            new Dependency("org.testcontainers", "testcontainers-junit-jupiter", "test"));

    /** POM 合并计划；{@code content} 是完整候选字节，调用 {@link #apply} 前不会写盘。 */
    public record Patch(
            Path pom,
            String original,
            String content,
            List<String> addedDependencies,
            boolean compilerParametersAdded) {

        public Patch {
            addedDependencies = List.copyOf(addedDependencies);
        }

        public boolean hasChanges() {
            return !original.equals(content);
        }
    }

    /** 只读生成 POM 合并计划，并验证目标确实消费 manifest 声明的 Ainer BOM。 */
    public Patch plan(Path pom, String expectedAinerVersion) {
        Objects.requireNonNull(pom, "pom");
        Objects.requireNonNull(expectedAinerVersion, "expectedAinerVersion");
        if (!Files.isRegularFile(pom)) {
            fail("已有项目缺少 pom.xml: " + pom);
        }
        String original;
        try {
            original = Files.readString(pom, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw failure("无法读取已有项目 POM: " + pom, exception);
        }
        Document document = parse(original);
        Element project = document.getDocumentElement();
        if (!"project".equals(localName(project))) {
            fail("pom.xml 根元素必须是 project");
        }
        verifyAinerBom(project, expectedAinerVersion);

        Map<String, String> directDependencies = directDependencies(project);
        List<Dependency> missing = new ArrayList<>();
        for (Dependency dependency : REQUIRED_DEPENDENCIES) {
            String actualScope = directDependencies.get(dependency.key());
            if (actualScope == null && !directDependencies.containsKey(dependency.key())) {
                missing.add(dependency);
            } else if (!compatibleScope(actualScope, dependency.scope())) {
                fail("已有依赖 scope 过窄，不能满足增量切片: " + dependency.key()
                        + "（当前 " + displayScope(actualScope) + "，需要 "
                        + displayScope(dependency.scope()) + "）");
            }
        }

        boolean addCompilerParameters = directChild(project, "properties") == null
                || directChild(directChild(project, "properties"), "maven.compiler.parameters") == null;
        List<TextEdit> edits = new ArrayList<>();
        if (!missing.isEmpty()) {
            int dependenciesClose = findDirectChildClosingOffset(original, "dependencies");
            if (dependenciesClose >= 0) {
                int insertion = lineStart(original, dependenciesClose);
                edits.add(new TextEdit(insertion, dependenciesXml(missing)));
            } else {
                int projectClose = requiredProjectClosingOffset(original);
                int insertion = lineStart(original, projectClose);
                edits.add(new TextEdit(insertion,
                        "    <dependencies>\n" + dependenciesXml(missing)
                                + "    </dependencies>\n\n"));
            }
        }
        if (addCompilerParameters) {
            int propertiesClose = findDirectChildClosingOffset(original, "properties");
            if (propertiesClose >= 0) {
                edits.add(new TextEdit(lineStart(original, propertiesClose),
                        "        <maven.compiler.parameters>true</maven.compiler.parameters>\n"));
            } else {
                int projectClose = requiredProjectClosingOffset(original);
                edits.add(new TextEdit(lineStart(original, projectClose),
                        "    <properties>\n"
                                + "        <maven.compiler.parameters>true</maven.compiler.parameters>\n"
                                + "    </properties>\n\n"));
            }
        }

        String patched = applyEdits(original, edits);
        parse(patched);
        return new Patch(
                pom, original, patched,
                missing.stream().map(Dependency::key).toList(),
                addCompilerParameters);
    }

    /** 原子替换已经完成只读验证的 POM；无变化时不触碰文件。 */
    public void apply(Patch patch) {
        Objects.requireNonNull(patch, "patch");
        if (!patch.hasChanges()) {
            return;
        }
        try {
            if (!Files.readString(patch.pom(), StandardCharsets.UTF_8).equals(patch.original())) {
                fail("pom.xml 在计划后发生变化，拒绝写入: " + patch.pom());
            }
            Path temporary = Files.createTempFile(
                    patch.pom().getParent(), ".ainer-pom-", ".xml");
            try {
                Files.writeString(temporary, patch.content(), StandardCharsets.UTF_8);
                if (Files.getFileAttributeView(patch.pom(), PosixFileAttributeView.class) != null) {
                    Files.setPosixFilePermissions(
                            temporary, Files.getPosixFilePermissions(patch.pom()));
                }
                try {
                    Files.move(temporary, patch.pom(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, patch.pom(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw failure("写入 pom.xml 失败: " + patch.pom(), exception);
        }
    }

    private void verifyAinerBom(Element project, String expectedVersion) {
        Element dependencyManagement = directChild(project, "dependencyManagement");
        Element managedDependencies = directChild(dependencyManagement, "dependencies");
        if (managedDependencies == null) {
            fail("已有项目必须通过 dependencyManagement 导入 dev.ainer:ainer-dependencies");
        }
        for (Element dependency : directChildren(managedDependencies, "dependency")) {
            if (!"dev.ainer".equals(text(dependency, "groupId"))
                    || !"ainer-dependencies".equals(text(dependency, "artifactId"))) {
                continue;
            }
            String version = resolveProperty(project, text(dependency, "version"));
            if (!expectedVersion.equals(version)) {
                fail("manifest Ainer 版本与已有项目 BOM 不一致：manifest=" + expectedVersion
                        + "，pom=" + version);
            }
            if (!"pom".equals(text(dependency, "type"))
                    || !"import".equals(text(dependency, "scope"))) {
                fail("dev.ainer:ainer-dependencies 必须以 type=pom、scope=import 导入");
            }
            return;
        }
        fail("已有项目未导入 dev.ainer:ainer-dependencies");
    }

    private Map<String, String> directDependencies(Element project) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        Element container = directChild(project, "dependencies");
        if (container == null) {
            return dependencies;
        }
        for (Element dependency : directChildren(container, "dependency")) {
            String groupId = text(dependency, "groupId");
            String artifactId = text(dependency, "artifactId");
            if (groupId != null && artifactId != null) {
                dependencies.put(groupId + ":" + artifactId, text(dependency, "scope"));
            }
        }
        return dependencies;
    }

    private boolean compatibleScope(String actual, String required) {
        String normalizedActual = actual == null || actual.isBlank() ? "compile" : actual;
        String normalizedRequired = required == null ? "compile" : required;
        if ("compile".equals(normalizedRequired)) {
            return "compile".equals(normalizedActual);
        }
        if ("runtime".equals(normalizedRequired)) {
            return "compile".equals(normalizedActual) || "runtime".equals(normalizedActual);
        }
        return "compile".equals(normalizedActual) || "test".equals(normalizedActual);
    }

    private String resolveProperty(Element project, String value) {
        if (value == null || !value.matches("\\$\\{[A-Za-z0-9_.-]+}")) {
            return value;
        }
        String property = value.substring(2, value.length() - 1);
        Element properties = directChild(project, "properties");
        Element resolved = directChild(properties, property);
        return resolved == null ? value : resolved.getTextContent().strip();
    }

    private String dependenciesXml(List<Dependency> dependencies) {
        StringBuilder xml = new StringBuilder();
        for (Dependency dependency : dependencies) {
            xml.append("        <dependency>\n")
                    .append("            <groupId>").append(dependency.groupId()).append("</groupId>\n")
                    .append("            <artifactId>").append(dependency.artifactId())
                    .append("</artifactId>\n");
            if (dependency.scope() != null) {
                xml.append("            <scope>").append(dependency.scope()).append("</scope>\n");
            }
            xml.append("        </dependency>\n");
        }
        return xml.toString();
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw failure("pom.xml 不是受支持的安全 XML: " + safeMessage(exception), exception);
        }
    }

    private Element directChild(Element parent, String expectedName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && expectedName.equals(localName(element))) {
                return element;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent, String expectedName) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && expectedName.equals(localName(element))) {
                result.add(element);
            }
        }
        return result;
    }

    private String text(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? null : child.getTextContent().strip();
    }

    private String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private int findDirectChildClosingOffset(String xml, String childName) {
        Deque<String> stack = new ArrayDeque<>();
        int index = 0;
        while (index < xml.length()) {
            int open = xml.indexOf('<', index);
            if (open < 0) {
                break;
            }
            if (xml.startsWith("<!--", open)) {
                index = requiredEnd(xml, "-->", open + 4);
                continue;
            }
            if (xml.startsWith("<?", open)) {
                index = requiredEnd(xml, "?>", open + 2);
                continue;
            }
            if (xml.startsWith("<![CDATA[", open)) {
                index = requiredEnd(xml, "]]>", open + 9);
                continue;
            }
            int close = tagEnd(xml, open + 1);
            String token = xml.substring(open + 1, close).strip();
            if (token.startsWith("!")) {
                index = close + 1;
                continue;
            }
            if (token.startsWith("/")) {
                String name = localTokenName(token.substring(1));
                if (stack.size() == 2 && childName.equals(name)
                        && childName.equals(stack.peekLast())) {
                    return open;
                }
                if (stack.isEmpty() || !name.equals(stack.removeLast())) {
                    fail("pom.xml 标签嵌套不合法: " + name);
                }
            } else if (!token.endsWith("/")) {
                stack.addLast(localTokenName(token));
            }
            index = close + 1;
        }
        return -1;
    }

    private int requiredProjectClosingOffset(String xml) {
        int offset = findRootClosingOffset(xml, "project");
        if (offset < 0) {
            fail("pom.xml 缺少 project 结束标签");
        }
        return offset;
    }

    private int findRootClosingOffset(String xml, String rootName) {
        int offset = xml.lastIndexOf("</" + rootName + ">");
        if (offset >= 0) {
            return offset;
        }
        return xml.lastIndexOf("</mvn:" + rootName + ">");
    }

    private int tagEnd(String xml, int start) {
        boolean quoted = false;
        char quote = 0;
        for (int index = start; index < xml.length(); index++) {
            char current = xml.charAt(index);
            if ((current == '\'' || current == '"')) {
                if (!quoted) {
                    quoted = true;
                    quote = current;
                } else if (quote == current) {
                    quoted = false;
                }
            } else if (current == '>' && !quoted) {
                return index;
            }
        }
        fail("pom.xml 存在未结束标签");
        throw new AssertionError("unreachable");
    }

    private int requiredEnd(String text, String marker, int start) {
        int found = text.indexOf(marker, start);
        if (found < 0) {
            fail("pom.xml 存在未结束的 XML 结构: " + marker);
        }
        return found + marker.length();
    }

    private String localTokenName(String token) {
        String name = token.split("\\s", 2)[0].replace("/", "");
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private int lineStart(String text, int offset) {
        int newline = text.lastIndexOf('\n', Math.max(0, offset - 1));
        int start = newline < 0 ? 0 : newline + 1;
        for (int index = start; index < offset; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return offset;
            }
        }
        return start;
    }

    private String applyEdits(String source, List<TextEdit> edits) {
        StringBuilder result = new StringBuilder(source);
        edits.stream()
                .sorted(Comparator.comparingInt(TextEdit::offset).reversed())
                .forEach(edit -> result.insert(edit.offset(), edit.content()));
        return result.toString();
    }

    private String displayScope(String scope) {
        return scope == null ? "compile" : scope;
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private BusinessException failure(String message, Throwable cause) {
        return new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                message + "（" + safeMessage(cause) + "）");
    }

    private void fail(String message) {
        throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET, message);
    }

    private record Dependency(String groupId, String artifactId, String scope) {
        String key() {
            return groupId + ":" + artifactId;
        }
    }

    private record TextEdit(int offset, String content) {
    }
}
