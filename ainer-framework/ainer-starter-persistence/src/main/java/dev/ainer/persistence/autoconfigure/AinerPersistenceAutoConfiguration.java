package dev.ainer.persistence.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import dev.ainer.persistence.mybatis.UuidTypeHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MyBatis-Plus / MyBatis 持久层自动装配（PostgreSQL 基线）。
 *
 * <p>注册 UUID {@link UuidTypeHandler}、MyBatis-Plus 全局配置（关闭 banner、主键策略
 * {@code IdType.AUTO}，新 Ainer 数据仍默认由应用生成 UUIDv7）以及 PostgreSQL 方言的
 * 分页内层拦截器（单页上限 100）。组装 {@link MybatisPlusInterceptor} 时分页拦截器
 * 始终排在其他内层拦截器之后。所有 Bean 均可被覆盖。
 */
@AutoConfiguration
@ConditionalOnClass(ConfigurationCustomizer.class)
public class AinerPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ainerUuidTypeHandlerCustomizer")
    public ConfigurationCustomizer ainerUuidTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(UUID.class, UuidTypeHandler.class);
    }

    @Bean
    @ConditionalOnMissingBean(name = "ainerMybatisPlusPropertiesCustomizer")
    public MybatisPlusPropertiesCustomizer ainerMybatisPlusPropertiesCustomizer() {
        return properties -> {
            properties.getGlobalConfig().setBanner(false);
            properties.getGlobalConfig().getDbConfig().setIdType(IdType.AUTO);
        };
    }

    @Bean
    @ConditionalOnMissingBean(PaginationInnerInterceptor.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public PaginationInnerInterceptor ainerPaginationInnerInterceptor() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(100L);
        return pagination;
    }

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor ainerMybatisPlusInterceptor(
            List<InnerInterceptor> innerInterceptors) {
        List<InnerInterceptor> orderedInterceptors = new ArrayList<>(innerInterceptors.size());
        innerInterceptors.stream()
                .filter(innerInterceptor -> !(innerInterceptor instanceof PaginationInnerInterceptor))
                .forEach(orderedInterceptors::add);
        innerInterceptors.stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .forEach(orderedInterceptors::add);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.setInterceptors(orderedInterceptors);
        return interceptor;
    }
}
