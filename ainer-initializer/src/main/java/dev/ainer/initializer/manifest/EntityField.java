package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A business field of an {@link EntityDeclaration} (ADR-0036 decision 1).
 *
 * @param name   field name (snake_case serialized to the column, camelCase to Java)
 * @param type   column/Java type from the v1 vocabulary
 * @param size   VARCHAR length for {@code string(type)}; ignored otherwise
 * @param nullable column NULL/NOT NULL (default NOT NULL)
 * @param unique    single-column unique constraint
 * @param comment   column comment (defaults to the field name)
 * @param initial   optional DB-level default expression; template literal rejected
 */
public record EntityField(
        String name,
        EntityDeclaration.FieldType type,
        @Nullable Integer size,
        boolean nullable,
        boolean unique,
        @Nullable String comment,
        @Nullable String initial) {

    private static final String ID_RESERVED = "id";

    public EntityField {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            fail("字段名不能为空");
        }
        if (name.equals(ID_RESERVED)) {
            fail("id 是保留字段名，实体业务字段不能叫 id");
        }
        if (!name.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            fail("字段名必须匹配 [a-zA-Z][a-zA-Z0-9_]*，收到: " + name);
        }
        Objects.requireNonNull(type, "type");
        if (type.takesSize() && (size == null || size <= 0 || size > 4000)) {
            fail("string 类型必须显式给出 1–4000 的长度，收到: " + size);
        }
        if (!type.takesSize() && size != null) {
            fail("类型 " + type + " 不接受 size 参数");
        }
        if (comment != null && comment.contains("{{") || comment != null && comment.contains("}}")) {
            fail("字段注释不能包含模板占位符");
        }
        if (unique && nullable) {
            fail("字段 " + name + " 不能同时 unique 与可空（PostgreSQL 唯一约束下空值可重复）");
        }
    }

    /** snake_case column name. */
    public String columnName() {
        return toSnake(name);
    }

    /** camelCase Java field name. */
    public String javaName() {
        return toCamel(name);
    }

    public String commentOrDefault() {
        return comment == null ? name : comment;
    }

    private static String toSnake(String value) {
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

    private static String toCamel(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return builder.toString();
    }

    private static void fail(String message) {
        throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST, message);
    }
}