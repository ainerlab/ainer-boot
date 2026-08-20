package dev.ainer.spring.runtime;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅当 {@code ainer.runtime.mode} 等于 {@link #value()} 时才装配被标注的 Bean。
 *
 * <p>条件由 {@link OnRuntimeModeCondition} 实现；属性缺失时按
 * {@link RuntimeMode#MONOLITH} 匹配。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnRuntimeModeCondition.class)
public @interface ConditionalOnRuntimeMode {

    RuntimeMode value();
}
