package dev.ainer.server.boundary.fixture;

import cn.xiaoqu.fixture.ProductFixtureType;

/**
 * 负向自测夹具：故意让框架包（{@code dev.ainer}）依赖产品包（{@code cn.xiaoqu}），
 * 用来验证 {@code AinerServerBoundaryArchitectureTest} 的边界规则真的会拦。
 *
 * <p>只在测试类路径上存在，不被 shell 门禁扫描（门禁只扫 {@code src/main/java}），
 * 也不参与打包。
 */
public final class FrameworkFixtureDependingOnProduct {

    private final ProductFixtureType productFixtureType = new ProductFixtureType("boundary");

    public String describe() {
        return productFixtureType.describe();
    }
}
