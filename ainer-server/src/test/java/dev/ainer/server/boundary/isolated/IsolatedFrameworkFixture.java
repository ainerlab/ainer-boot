package dev.ainer.server.boundary.isolated;

/**
 * 负向自测的对照组夹具：纯框架类，不依赖任何产品包。
 *
 * <p>它证明 {@code AinerServerBoundaryArchitectureTest} 的边界规则不是「见到夹具就失败」——
 * 违规判定确实来自产品包依赖本身。
 */
public final class IsolatedFrameworkFixture {

    public String describe() {
        return "framework-fixture";
    }
}
