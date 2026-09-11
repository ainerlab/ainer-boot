package cn.xiaoqu.fixture;

/**
 * 负向自测夹具：产品侧类型（{@code cn.xiaoqu} 是
 * {@code scripts/framework-boundary-targets.txt} 中登记的产品包根）。
 *
 * <p>它只存在于测试类路径，用于让 {@code AinerServerBoundaryArchitectureTest} 能构造一个真实的
 * 「框架类依赖产品类」字节码依赖，从而证明边界规则不是恒真。这不是产品代码，也不参与打包。
 *
 * <p>刻意使用实例字段与构造器（而不是 {@code static final String} 常量），因为编译期常量会被
 * javac 内联、字节码里不留产品类引用，那样就构造不出真实依赖。
 */
public final class ProductFixtureType {

    private final String name;

    public ProductFixtureType(String name) {
        this.name = name;
    }

    public String describe() {
        return "product-fixture:" + name;
    }
}
