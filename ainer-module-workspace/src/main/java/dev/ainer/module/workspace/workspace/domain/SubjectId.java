package dev.ainer.module.workspace.workspace.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 成员主体标识值对象，限定为安全字符集且最长 128 位。
 *
 * <p>标识在构造时去空白并做格式校验，防止任意字符串进入成员表与审计记录。
 */
public record SubjectId(String value) {

    public static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public SubjectId {
        value = Objects.requireNonNull(value, "value").trim();
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Subject identifier is invalid");
        }
    }
}
