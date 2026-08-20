package dev.ainer.authorization.domain;

/**
 * 由端点/用例契约选定的访问模式（ADR-0030 §5.7）。{@link #PUBLIC_PROJECTION} 只进入公开
 * 管道，{@link #AUTHENTICATED} 只进入认证管道。模式在服务端固定，绝不由客户端 header、
 * query 或 body 选择，且两条路径互不自动回退。
 */
public enum AccessMode {
    PUBLIC_PROJECTION,
    AUTHENTICATED
}
