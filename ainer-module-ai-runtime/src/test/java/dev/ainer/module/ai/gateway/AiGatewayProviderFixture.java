package dev.ainer.module.ai.gateway;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.module.ai.gateway.application.ContextSnapshotBuilder;
import dev.ainer.module.ai.gateway.application.ModelProvider;
import dev.ainer.module.ai.gateway.application.ModelStreamObserver;
import dev.ainer.module.ai.gateway.application.ProviderFailure;
import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;
import dev.ainer.module.ai.gateway.domain.TokenUsage;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 网关集成测试共用的 provider / 快照 / JWT 替身（真实 RSA 验签链，不用 stub）。
 *
 * <p>为什么必须是<strong>独立一份</strong>定义：{@code AiRuntimeModuleConfiguration} 的
 * {@code @ComponentScan(basePackageClasses = AiGatewayFeatureMarker.class)} 覆盖
 * {@code dev.ainer.module.ai.gateway} 整个包，且主代码的 {@code @ComponentScan} 没有
 * {@code TypeExcludeFilter}（它只在 {@code @SpringBootApplication} 上自动挂），因此测试类路径上
 * 同包内的 {@code @TestConfiguration} 会被组件扫描真实注册。两个测试类各自定义同名 {@code @Bean}
 * 会直接 {@code BeanDefinitionOverrideException}（Boot 默认禁止覆盖），
 * 所以替身集中在本类，由各集成测试 {@code @Import} 进来，恰好只有一份定义。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AiGatewayProviderFixture {

    /** 测试 RSA key：签名与验签同源（{@link JwtTestSupport}）。 */
    public static final RSAKey HTTP_RSA_JWK = JwtTestSupport.generateRsaKey();

    @Bean
    @Primary
    public TestModelProvider testModelProvider() {
        return new TestModelProvider();
    }

    @Bean
    @Primary
    public ContextSnapshotBuilder testSnapshotBuilder() {
        return (task, ctx) -> new ContextSnapshotBuilder.ContextSnapshotData(
                task.targetIdentityId(),
                UUID.randomUUID(),
                "[{\"type\":\"publication\",\"id\":\"pub-001\",\"summary\":\"上周发布3篇笔记\"},"
                        + "{\"type\":\"metric\",\"id\":\"m-001\",\"summary\":\"总曝光12.3k,互动率4.2%\"},"
                        + "{\"type\":\"feedback\",\"id\":\"f-001\",\"summary\":\"用户咨询增加15%\"}]",
                "[{\"memory_id\":\"mem-001\",\"scope\":\"brand\",\"confidence\":0.85,"
                        + "\"summary\":\"优先发布教程类内容\"}]");
    }

    /** 真链：RSA 验签 + issuer 校验（JwtTestSupport），替代按 token 字符串直接构造的 stub。 */
    @Bean
    public JwtDecoder testJwtDecoder() {
        return JwtTestSupport.jwtDecoder(HTTP_RSA_JWK, "https://auth.ainer.test", "ainer-api");
    }

    /** 可注入失败的 provider 替身：让「供应商不可用」这类失败路径不依赖真实供应商。 */
    public static final class TestModelProvider implements ModelProvider {

        private final AtomicBoolean failNext = new AtomicBoolean();
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String name() {
            return "test-provider";
        }

        @Override
        public ModelCompletion complete(ModelInvocation invocation) {
            calls.incrementAndGet();
            if (failNext.compareAndSet(true, false)) {
                throw new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "simulated provider outage");
            }
            return completion();
        }

        @Override
        public void stream(ModelInvocation invocation, ModelStreamObserver observer) {
            calls.incrementAndGet();
            observer.onDelta("Ainer ");
            observer.onDelta("stream");
            observer.onComplete(new ModelCompletion(
                    "provider-stream-1", "test/model", "Ainer stream", "stop",
                    new TokenUsage(8, 2, false)));
        }

        public void failNext() {
            failNext.set(true);
        }

        public int calls() {
            return calls.get();
        }

        public void reset() {
            failNext.set(false);
            calls.set(0);
        }

        private ModelCompletion completion() {
            return new ModelCompletion(
                    "provider-request-1", "test/model", "Ainer answer", "stop",
                    new TokenUsage(10, 8, false));
        }
    }
}
