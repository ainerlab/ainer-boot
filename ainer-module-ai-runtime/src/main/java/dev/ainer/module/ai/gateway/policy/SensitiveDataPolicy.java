package dev.ainer.module.ai.gateway.policy;

import dev.ainer.module.ai.gateway.domain.ModelMessage;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感数据策略：调用前对消息序列做密钥特征检测（PEM 私钥块、{@code sk-} API key、
 * AWS AKIA 访问密钥），命中即拒绝发送给模型供应商。
 */
public final class SensitiveDataPolicy {

    private static final List<Pattern> DENIED_PATTERNS = List.of(
            Pattern.compile("-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<![A-Za-z0-9])sk-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("(?<![A-Z0-9])AKIA[A-Z0-9]{16}(?![A-Z0-9])"));

    public boolean containsDeniedData(List<ModelMessage> messages) {
        return messages.stream()
                .map(ModelMessage::content)
                .anyMatch(content -> DENIED_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find()));
    }
}
