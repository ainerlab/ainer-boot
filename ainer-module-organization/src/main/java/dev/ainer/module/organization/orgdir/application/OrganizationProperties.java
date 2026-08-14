package dev.ainer.module.organization.orgdir.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 组织目录配置。{@code trusted-issuer} 是可任职 subject 的唯一可信 issuer（Authorization
 * Server issuer）；未配置时 fail-closed 拒绝创建任职（ADR-0042 §2.2）。
 */
@ConfigurationProperties("ainer.organization")
public record OrganizationProperties(String trustedIssuer) {

    public OrganizationProperties {
        trustedIssuer = trustedIssuer == null ? "" : trustedIssuer.strip();
    }
}
