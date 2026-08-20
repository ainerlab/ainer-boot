package dev.ainer.module.workspace.workspace.domain;

import java.util.Objects;

/**
 * Workspace 名称值对象，长度限制在 2 到 80 个字符之间。
 *
 * <p>构造时去空白并校验长度，非法名称在应用层统一映射为 {@code INVALID_NAME} 错误。
 */
public record WorkspaceName(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 80;

    public WorkspaceName {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Workspace name length is invalid");
        }
    }
}
