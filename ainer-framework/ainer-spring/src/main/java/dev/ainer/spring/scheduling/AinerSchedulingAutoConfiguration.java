package dev.ainer.spring.scheduling;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ainer 调度装配：为整个进程启用 Spring {@code @Scheduled} 注解处理。
 *
 * <p>放在 {@code ainer-spring} 而不是 {@code ainer-starter-web} 或某个业务模块，是因为
 * {@code @EnableScheduling} 只依赖 Spring Framework 的调度基础设施
 * （{@code ScheduledAnnotationBeanPostProcessor}），既不依赖 Servlet web（web starter 的
 * 自动装配带 {@code @ConditionalOnWebApplication(SERVLET)}），也不依赖持久化或任何具体模块；
 * 非 web 进程（worker、off-state 应用）同样需要它。放在这里可以被所有 starter 传递获得，
 * 避免像 2026-09-11 之前的 {@code WorkspaceAuthorizationAuditRetentionConfiguration} 那样
 * 把全局调度能力绑在某个业务开关上——那个开关默认关闭时，通知投递引擎的
 * {@code @Scheduled} 根本不注册，记录永远停在 PENDING 且没有任何报错。
 *
 * <p>开关 {@code ainer.scheduling.enabled} 默认 {@code true}（缺失即生效），
 * 只有显式配置为 {@code false} 才关闭全局调度；关闭后所有 {@code @Scheduled} 方法都不再运行，
 * 属于运维降级手段。业务模块自己的 bean 条件（例如审计保留任务的 enabled）仍由各自配置类保留。
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "ainer.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AinerSchedulingAutoConfiguration {
}
