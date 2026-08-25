package dev.ainer.observability;

/**
 * OTLP 导出已按配置开启的装配标记。真实 OTel exporter 由产品在 classpath
 * 上自行提供；本 Starter 不强制引入导出依赖，也不改写域 Micrometer counters。
 */
public final class AinerOtlpExportMarker {

    public AinerOtlpExportMarker() {
    }
}
