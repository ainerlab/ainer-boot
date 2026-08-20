/**
 * 决策求值器消费的授权策略端口（ADR-0030 §5）。实现由产品/领域模块与持久化/管理切片
 * 提供；决策核心保持无 Spring。包级 {@link org.jspecify.annotations.NullMarked}。
 */
@NullMarked
package dev.ainer.authorization.policy;

import org.jspecify.annotations.NullMarked;
