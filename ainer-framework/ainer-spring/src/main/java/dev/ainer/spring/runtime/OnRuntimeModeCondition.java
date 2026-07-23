package dev.ainer.spring.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;
import java.util.Map;

final class OnRuntimeModeCondition extends SpringBootCondition {

    static final String PROPERTY = "ainer.runtime.mode";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnRuntimeMode.class.getName());
        if (attributes == null) {
            return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnRuntimeMode.class)
                    .because("annotation attributes are unavailable"));
        }

        RuntimeMode expected = (RuntimeMode) attributes.get("value");
        String configured = context.getEnvironment().getProperty(PROPERTY, RuntimeMode.MONOLITH.name());
        RuntimeMode actual;
        try {
            actual = RuntimeMode.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnRuntimeMode.class)
                    .because("property '%s' has unsupported value '%s'".formatted(PROPERTY, configured)));
        }

        ConditionMessage message = ConditionMessage.forCondition(ConditionalOnRuntimeMode.class)
                .because("runtime mode is %s".formatted(actual));
        return actual == expected ? ConditionOutcome.match(message) : ConditionOutcome.noMatch(message);
    }
}
