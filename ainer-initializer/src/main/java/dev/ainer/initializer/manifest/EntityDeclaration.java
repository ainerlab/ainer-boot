package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A single-table entity declared in Manifest v1 {@code entities} (ADR-0036). Immutable and
 * fully validated; generation never sees an invalid entity.
 *
 * @param name   entity name, used for class prefixes and the plural REST resource path
 * @param fields business fields; {@code id} is always implied and reserved
 */
public record EntityDeclaration(String name, List<EntityField> fields) {

    /** Column type vocabulary supported by the v1 templates (ADR-0036 decision 3). */
    public enum FieldType {
        STRING, TEXT, INT, LONG, DECIMAL, BOOLEAN, INSTANT, UUID;

        public boolean takesSize() {
            return this == STRING;
        }
    }

    public EntityDeclaration {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[A-Za-z][A-Za-z0-9]*")) {
            fail("实体名必须匹配 [A-Za-z][A-Za-z0-9]*，收到: " + name);
        }
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) {
            fail("实体 " + name + " 必须至少声明一个业务字段");
        }
    }

    /** Pascal-case class prefix, e.g. {@code customerOrder} -> {@code CustomerOrder}. */
    public String className() {
        StringBuilder builder = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return builder.toString();
    }

    /** Pluralized resource path segment, e.g. {@code customerOrder} -> {@code customer-orders}. */
    public String resourcePath() {
        String className = className();
        String lower = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        return lower + "s";
    }

    /** Table name: {@code ainer_<snake case>_<name>} to keep generated tables namespaced. */
    public String tableName() {
        return "ainer_" + snakeCase(name);
    }

    private static String snakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static void fail(String message) {
        throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST, message);
    }
}
