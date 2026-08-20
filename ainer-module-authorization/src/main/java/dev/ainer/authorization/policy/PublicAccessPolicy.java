package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.PublicProjection;
import dev.ainer.authorization.domain.ResourceRef;

import java.util.Optional;

/**
 * 匿名/公开访问的唯一授权路径（ADR-0030 §1、§5.2）。公开访问不适用时返回空 Optional；
 * 适用时返回非空 {@link PublicProjection}。投影描述符作为义务附着在 ALLOW 决策上，
 * HTTP 适配器必须在发送响应前应用它。没有显式策略时，公开访问默认拒绝。
 */
@FunctionalInterface
public interface PublicAccessPolicy {

    Optional<PublicProjection> evaluate(PermissionCode permission, ResourceRef resource);
}
