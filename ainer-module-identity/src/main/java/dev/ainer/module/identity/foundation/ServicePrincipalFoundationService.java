package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.security.principal.IdentityAuthorityRef;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Greenfield ServicePrincipal 模型的应用核心（ADR-0033 Greenfield §2.6、S1.1 主干）。
 *
 * <p>通过 foundation 端口驱动 {@link ServicePrincipal} + {@link OAuthClientBinding}。
 * principal 是审计上稳定的非人类身份；OAuth {@code client_id} 是绑定到它之上的可轮换
 * 凭证。客户端轮换绝不能改变审计身份；绑定冲突（同一 client_id 已存在 ACTIVE 绑定）
 * 是硬冲突。
 *
 * <p>与 {@link IdentityFoundationService} 一样，该服务刻意与旧版 tenant 绑定服务解耦，
 * 不触碰它们。破坏性切换时把本核心接入 token 签发路径，把可轮换凭证投影为稳定的
 * {@code ServiceSubjectRef}。
 *
 * <p>未标注 {@code @Service}：{@code Supplier<UUID>} ID 来源在
 * {@code IdentityModuleConfiguration} 中绑定到 foundation 仓库的 {@code nextUuidV7()}，
 * 因此 bean 在那里显式声明，而不是注入语义含糊的 {@code Supplier}。
 */
public class ServicePrincipalFoundationService {

    private final ServicePrincipalRepository principalRepository;
    private final OAuthClientBindingRepository bindingRepository;
    private final Clock clock;
    private final Supplier<UUID> idSource;

    public ServicePrincipalFoundationService(
            ServicePrincipalRepository principalRepository,
            OAuthClientBindingRepository bindingRepository,
            Clock clock,
            Supplier<UUID> idSource) {
        this.principalRepository = Objects.requireNonNull(principalRepository, "principalRepository");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
    }

    /**
     * 注册一个新的 ACTIVE ServicePrincipal。同一 ID 已存在 principal 时失败关闭
     * （fail-closed）。
     */
    public ServicePrincipal registerServicePrincipal(IdentityAuthorityRef authority) {
        Objects.requireNonNull(authority, "authority");
        Instant now = clock.instant();
        ServicePrincipal principal = new ServicePrincipal(
                idSource.get(), authority, ServicePrincipalStatus.ACTIVE, 0L, now);
        principalRepository.save(principal);
        return principal;
    }

    /**
     * 把可轮换的 OAuth client_id 绑定到既有 ACTIVE ServicePrincipal。principal 不存在、
     * 不处于活跃状态，或同一 client_id 已携带 ACTIVE 绑定时，一律失败关闭（fail-closed）。
     */
    public OAuthClientBinding bindClient(UUID principalId, String clientId) {
        Objects.requireNonNull(principalId, "principalId");
        requireNonBlank(clientId, "clientId");
        ServicePrincipal principal = principalRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.SERVICE_PRINCIPAL_NOT_FOUND));
        if (!principal.status().canAuthenticate()) {
            throw new BusinessException(IdentityErrorCode.SERVICE_PRINCIPAL_NOT_ACTIVE);
        }
        if (bindingRepository.findActiveByClientId(clientId).isPresent()) {
            throw new BusinessException(IdentityErrorCode.OAUTH_CLIENT_BINDING_ALREADY_EXISTS);
        }
        OAuthClientBinding binding = new OAuthClientBinding(
                idSource.get(), principalId, clientId, OAuthClientBindingStatus.ACTIVE,
                clock.instant(), null);
        bindingRepository.save(binding);
        return binding;
    }

    /** 解析可轮换 OAuth client_id 背后的稳定 principal（如存在）。 */
    public Optional<ServicePrincipal> findPrincipalByClientId(String clientId) {
        requireNonBlank(clientId, "clientId");
        return principalRepository.findByActiveClientId(clientId);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
    }
}
