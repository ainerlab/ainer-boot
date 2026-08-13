package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal deterministic template renderer. Placeholders use {@code {{key}}} syntax; unknown
 * placeholders and residual placeholder text fail generation instead of producing silent output.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");

    private final Map<String, String> values;

    private TemplateRenderer(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Renders {@code template}, failing loudly on unknown keys and residual placeholders. */
    public String render(String template, String sourceName) {
        Objects.requireNonNull(template, "template");
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                        sourceName + " 引用未知占位符 {{" + key + "}}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        String rendered = result.toString();
        if (PLACEHOLDER.matcher(rendered).find()) {
            throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                    sourceName + " 渲染后仍残留未替换占位符");
        }
        return rendered;
    }

    public static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();

        public Builder put(String key, @Nullable String value) {
            Objects.requireNonNull(key, "key");
            if (value == null) {
                throw new IllegalArgumentException("占位符 " + key + " 不能为 null（必须显式提供默认值）");
            }
            if (PLACEHOLDER.matcher(value).find()) {
                throw new IllegalArgumentException("占位符 " + key + " 的值不能包含嵌套占位符");
            }
            values.put(key, value);
            return this;
        }

        public TemplateRenderer build() {
            return new TemplateRenderer(values);
        }
    }
}