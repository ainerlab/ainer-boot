package dev.ainer.module.task;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 任务调度模块配置（ADR-0047）。默认开启；关闭用 {@code ainer.task.enabled=false}。
 * 执行引擎独立条件装配（{@code ainer.task.engine.enabled}），便于测试只验管理面不跑轮询。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.task", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = TaskFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = TaskFeatureMarker.class, annotationClass = Mapper.class))
public class TaskModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock taskClock() {
        return Clock.systemUTC();
    }
}
