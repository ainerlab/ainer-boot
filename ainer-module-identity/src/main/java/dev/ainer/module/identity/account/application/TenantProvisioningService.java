package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantProvisioningService {

    private static final Pattern TENANT_CODE =
            Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final Pattern USERNAME =
            Pattern.compile("[a-z0-9][a-z0-9._@-]{2,99}");
    private static final Pattern SAFE_REFERENCE =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Pattern EMAIL =
            Pattern.compile("[^\\s@]{1,128}@[^\\s@]{1,190}");
    private static final String EXPIRY_ACTOR = "system:expiry";
    private static final String LOCKOUT_ACTOR = "system:activation-lockout";

    private final TenantProvisioningRepository repository;
    private final IdentityRepository identityRepository;
    private final PasswordHashingPort passwordHashingPort;
    private final ActivationSecretGenerator activationSecretGenerator;
    private final ObjectProvider<TenantProvisioningNotificationPayloadProtector> protectorProvider;
    private final Clock clock;

    public TenantProvisioningService(
            TenantProvisioningRepository repository,
            IdentityRepository identityRepository,
            PasswordHashingPort passwordHashingPort,
            ActivationSecretGenerator activationSecretGenerator,
            ObjectProvider<TenantProvisioningNotificationPayloadProtector> protectorProvider,
            Clock clock) {
        this.repository = repository;
        this.identityRepository = identityRepository;
        this.passwordHashingPort = passwordHashingPort;
        this.activationSecretGenerator = activationSecretGenerator;
        this.protectorProvider = protectorProvider;
        this.clock = clock;
    }

    @Transactional
    public TenantProvisioningResult create(
            CreateTenantProvisioningCommand command,
            PlatformProvisioningActor actor,
            TenantProvisioningPolicy policy) {
        NormalizedCommand normalized = normalize(command);
        requireActor(actor);
        Objects.requireNonNull(policy, "policy");
        String fingerprint = fingerprint(normalized);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        repository.acquireLocks(
                actor.serviceId(),
                normalized.idempotencyKey(),
                normalized.tenantCode(),
                normalized.ownerUsername());

        TenantProvisioningRequest idempotent = repository
                .findByIdempotencyForUpdate(actor.serviceId(), normalized.idempotencyKey())
                .orElse(null);
        if (idempotent != null) {
            idempotent = expireIfNeeded(idempotent, now, actor.requestId());
            if (!idempotent.requestFingerprint().equals(fingerprint)) {
                throw new BusinessException(
                        IdentityErrorCode.TENANT_PROVISIONING_IDEMPOTENCY_CONFLICT);
            }
            return new TenantProvisioningResult(idempotent, false);
        }

        List<TenantProvisioningRequest> reservations =
                repository.findOpenReservationsForUpdate(
                        normalized.tenantCode(), normalized.ownerUsername());
        for (TenantProvisioningRequest reservation : reservations) {
            TenantProvisioningRequest current =
                    expireIfNeeded(reservation, now, actor.requestId());
            if ("REQUESTED".equals(current.status())) {
                throw new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_CONFLICT);
            }
        }
        if (repository.tenantExistsByCode(normalized.tenantCode())) {
            throw new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_CONFLICT);
        }

        ProvisioningUserReference existingUser = repository
                .findUserByUsernameForUpdate(normalized.ownerUsername())
                .orElse(null);
        if (existingUser != null && existingUser.status() != IdentityStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_USER_CONFLICT);
        }

        UUID requestId = repository.nextUuidV7();
        UUID tenantId = repository.nextUuidV7();
        UUID ownerSubjectId = existingUser == null
                ? repository.nextUuidV7()
                : existingUser.subjectId();
        Instant expiresAt = now.plus(existingUser == null
                ? policy.activationTtl()
                : policy.requestTtl());
        TenantProvisioningRequest request = new TenantProvisioningRequest(
                requestId,
                tenantId,
                normalized.tenantCode(),
                normalized.tenantName(),
                ownerSubjectId,
                normalized.ownerUsername(),
                existingUser == null
                        ? normalized.ownerDisplayName()
                        : existingUser.displayName(),
                existingUser != null,
                "REQUESTED",
                normalized.idempotencyKey(),
                fingerprint,
                actor.serviceId(),
                actor.requestId(),
                normalized.changeReference(),
                now,
                expiresAt,
                null,
                0);
        try {
            repository.insertRequest(request);
            createGrantAndNotification(request, normalized, policy, now);
            audit(
                    request,
                    "REQUESTED",
                    "SERVICE",
                    actor.serviceId(),
                    actor.requestId(),
                    now);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_CONFLICT);
        }
        return new TenantProvisioningResult(request, true);
    }

    @Transactional
    public TenantProvisioningRequest find(
            UUID provisioningRequestId,
            PlatformProvisioningActor actor) {
        requireActor(actor);
        Objects.requireNonNull(provisioningRequestId, "provisioningRequestId");
        TenantProvisioningRequest request = repository
                .findByIdForUpdate(provisioningRequestId)
                .orElseThrow(() ->
                        new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_NOT_FOUND));
        return expireIfNeeded(request, clock.instant(), actor.requestId());
    }

    @Transactional
    public TenantProvisioningCancellationResult cancel(
            UUID provisioningRequestId,
            String changeReference,
            PlatformProvisioningActor actor) {
        requireActor(actor);
        Objects.requireNonNull(provisioningRequestId, "provisioningRequestId");
        String normalizedChangeReference = normalizeChangeReference(changeReference);
        TenantProvisioningRequest request = repository
                .findByIdForUpdate(provisioningRequestId)
                .orElseThrow(() ->
                        new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_NOT_FOUND));
        Instant now = clock.instant();
        if ("REQUESTED".equals(request.status()) && !now.isBefore(request.expiresAt())) {
            return new TenantProvisioningCancellationResult(
                    expireIfNeeded(request, now, actor.requestId()),
                    false);
        }
        if ("CANCELLED".equals(request.status()) || "EXPIRED".equals(request.status())) {
            return new TenantProvisioningCancellationResult(request, false);
        }
        if (!"REQUESTED".equals(request.status())
                || !repository.markCancelled(request.id(), request.version(), now)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        boolean grantExpected = !request.ownerUserExists();
        boolean grantCancelled =
                repository.cancelActivationGrantByRequest(request.id(), now);
        if (grantCancelled != grantExpected) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        repository.cancelPendingNotificationByRequest(request.id());
        TenantProvisioningRequest cancelled = request.cancelled(now);
        audit(
                cancelled,
                "CANCELLED",
                "SERVICE",
                actor.serviceId(),
                actor.requestId(),
                normalizedChangeReference,
                now);
        return new TenantProvisioningCancellationResult(cancelled, true);
    }

    /**
     * 新用户凭一次性 grant 设置首个长期密码并激活 tenant。失败的 secret 尝试必须提交计数，因此
     * 此方法用显式 rejected result 返回需要对外映射的业务错误，调用方不得在事务内再次抛出。
     */
    @Transactional
    public TenantProvisioningCompletion activateNewUser(
            UUID grantId,
            String activationSecret,
            String rawPassword,
            String requestId) {
        Objects.requireNonNull(grantId, "grantId");
        requireSafeRequestId(requestId);
        TenantActivationGrant grant = repository
                .findActivationGrantByIdForUpdate(grantId)
                .orElseThrow(() -> new BusinessException(
                        IdentityErrorCode.TENANT_ACTIVATION_CREDENTIAL_INVALID));
        TenantProvisioningRequest request = repository
                .findByIdForUpdate(grant.provisioningRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Activation grant references a missing provisioning request"));
        Instant now = clock.instant();

        if (!"ACTIVE".equals(grant.status()) || !"REQUESTED".equals(request.status())) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_ACTIVATION_CREDENTIAL_INVALID);
        }
        if (!now.isBefore(grant.expiresAt()) || !now.isBefore(request.expiresAt())) {
            expireGrantAndRequest(grant, request, now, requestId);
            return TenantProvisioningCompletion.rejected(
                    request.expired(now),
                    IdentityErrorCode.TENANT_ACTIVATION_CREDENTIAL_INVALID);
        }
        if (!constantTimeSecretMatches(activationSecret, grant.secretHash())) {
            boolean lockGrant = grant.attemptCount() + 1 >= grant.maxAttempts();
            if (!repository.recordFailedActivationAttempt(
                    grant.id(), grant.attemptCount(), now, lockGrant)) {
                throw new BusinessException(
                        IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
            }
            TenantProvisioningRequest current = request;
            if (lockGrant) {
                current = cancelLockedRequest(request, now, requestId);
            }
            return TenantProvisioningCompletion.rejected(
                    current,
                    IdentityErrorCode.TENANT_ACTIVATION_CREDENTIAL_INVALID);
        }
        if (!validRawPassword(rawPassword)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_ACTIVATION_PASSWORD_INVALID);
        }
        if (request.ownerUserExists()
                || !request.tenantId().equals(grant.tenantId())
                || !request.ownerSubjectId().equals(grant.subjectId())) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }

        repository.acquireLocks(
                "activation-grant:" + grant.id(),
                request.idempotencyKey(),
                request.tenantCode(),
                request.ownerUsername());
        if (repository.tenantExistsByCode(request.tenantCode())
                || repository.findUserByUsernameForUpdate(request.ownerUsername()).isPresent()) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }

        ProvisionedIdentity identity = activateReservedNewUser(
                request, rawPassword, now);
        if (!repository.markActivationGrantConsumed(
                grant.id(), grant.attemptCount(), now)
                || !repository.markActivated(request.id(), request.version(), now)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        repository.cancelPendingNotificationByRequest(request.id());
        TenantProvisioningRequest activated = request.activated(now);
        audit(
                activated,
                "ACTIVATED",
                "ACTIVATION_GRANT",
                grant.id().toString(),
                requestId,
                now);
        return TenantProvisioningCompletion.activated(activated, identity);
    }

    @Transactional
    public TenantProvisioningCompletion acceptExistingUser(
            UUID provisioningRequestId,
            UUID actorSubjectId,
            String requestId) {
        Objects.requireNonNull(provisioningRequestId, "provisioningRequestId");
        Objects.requireNonNull(actorSubjectId, "actorSubjectId");
        requireSafeRequestId(requestId);
        TenantProvisioningRequest request = repository
                .findByIdForUpdate(provisioningRequestId)
                .orElseThrow(() ->
                        new BusinessException(IdentityErrorCode.TENANT_PROVISIONING_NOT_FOUND));
        Instant now = clock.instant();
        if (!"REQUESTED".equals(request.status())) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        if (!now.isBefore(request.expiresAt())) {
            TenantProvisioningRequest expired =
                    expireIfNeeded(request, now, requestId);
            return TenantProvisioningCompletion.rejected(
                    expired,
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        if (!request.ownerUserExists()
                || !request.ownerSubjectId().equals(actorSubjectId)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_ACCEPTANCE_FORBIDDEN);
        }

        repository.acquireLocks(
                "existing-user-acceptance:" + actorSubjectId,
                request.idempotencyKey(),
                request.tenantCode(),
                request.ownerUsername());
        ProvisioningUserReference user = repository
                .findUserBySubjectIdForUpdate(actorSubjectId)
                .orElseThrow(() -> new BusinessException(
                        IdentityErrorCode.TENANT_PROVISIONING_ACCEPTANCE_FORBIDDEN));
        if (user.status() != IdentityStatus.ACTIVE
                || !user.username().equals(request.ownerUsername())
                || repository.tenantExistsByCode(request.tenantCode())) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }

        IdentityTenant tenant = new IdentityTenant(
                request.tenantId(),
                request.tenantCode(),
                request.tenantName(),
                IdentityStatus.ACTIVE,
                now,
                now);
        TenantMembership membership = new TenantMembership(
                request.tenantId(),
                actorSubjectId,
                TenantRole.OWNER,
                false,
                IdentityStatus.ACTIVE,
                now,
                now);
        try {
            identityRepository.insertTenant(tenant);
            identityRepository.insertMembership(membership);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        if (!repository.markActivated(request.id(), request.version(), now)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        repository.cancelPendingNotificationByRequest(request.id());
        TenantProvisioningRequest activated = request.activated(now);
        audit(
                activated,
                "ACTIVATED",
                "USER",
                actorSubjectId.toString(),
                requestId,
                now);
        return TenantProvisioningCompletion.activated(
                activated,
                new ProvisionedIdentity(
                        request.tenantId(), actorSubjectId, request.ownerUsername()));
    }

    private void createGrantAndNotification(
            TenantProvisioningRequest request,
            NormalizedCommand command,
            TenantProvisioningPolicy policy,
            Instant now) {
        TenantProvisioningNotificationPayloadProtector protector =
                protectorProvider.getIfAvailable();
        if (protector == null) {
            throw new IllegalStateException(
                    "Tenant provisioning notification payload protector is not configured");
        }

        TenantProvisioningNotification notification;
        if (request.ownerUserExists()) {
            notification = new TenantProvisioningNotification(
                    TenantProvisioningNotificationType.EXISTING_USER_ACCEPTANCE,
                    request.id(),
                    request.tenantId(),
                    request.ownerSubjectId(),
                    "IDENTITY_SUBJECT",
                    request.ownerSubjectId().toString(),
                    null,
                    null,
                    request.expiresAt());
        } else {
            String activationSecret = activationSecretGenerator.generate();
            TenantActivationGrant grant = new TenantActivationGrant(
                    repository.nextUuidV7(),
                    request.id(),
                    request.tenantId(),
                    request.ownerSubjectId(),
                    sha256(activationSecret),
                    "ACTIVE",
                    0,
                    policy.activationMaxAttempts(),
                    now,
                    request.expiresAt(),
                    null,
                    null);
            repository.insertActivationGrant(grant);
            notification = new TenantProvisioningNotification(
                    TenantProvisioningNotificationType.NEW_USER_ACTIVATION,
                    request.id(),
                    request.tenantId(),
                    request.ownerSubjectId(),
                    command.deliveryChannel(),
                    command.deliveryAddress(),
                    grant.id(),
                    activationSecret,
                    request.expiresAt());
        }

        ProtectedTenantProvisioningNotification protectedNotification =
                protector.protect(notification);
        repository.insertNotification(
                new TenantProvisioningNotificationOutboxEntry(
                        repository.nextUuidV7(),
                        request.id(),
                        request.tenantId(),
                        request.ownerSubjectId(),
                        notification.type(),
                        1,
                        protectedNotification,
                        0,
                        now),
                now);
    }

    private ProvisionedIdentity activateReservedNewUser(
            TenantProvisioningRequest request,
            String rawPassword,
            Instant now) {
        IdentityTenant tenant = new IdentityTenant(
                request.tenantId(),
                request.tenantCode(),
                request.tenantName(),
                IdentityStatus.ACTIVE,
                now,
                now);
        IdentityUser user = new IdentityUser(
                request.ownerSubjectId(),
                request.ownerUsername(),
                passwordHashingPort.hash(rawPassword),
                request.ownerDisplayName(),
                IdentityStatus.ACTIVE,
                now,
                now);
        TenantMembership membership = new TenantMembership(
                request.tenantId(),
                request.ownerSubjectId(),
                TenantRole.OWNER,
                true,
                IdentityStatus.ACTIVE,
                now,
                now);
        try {
            identityRepository.insertTenant(tenant);
            identityRepository.insertUser(user);
            identityRepository.insertMembership(membership);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        return new ProvisionedIdentity(
                request.tenantId(),
                request.ownerSubjectId(),
                request.ownerUsername());
    }

    private TenantProvisioningRequest cancelLockedRequest(
            TenantProvisioningRequest request,
            Instant now,
            String requestId) {
        if (!repository.markCancelled(request.id(), request.version(), now)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        repository.cancelPendingNotificationByRequest(request.id());
        TenantProvisioningRequest cancelled = request.cancelled(now);
        audit(
                cancelled,
                "CANCELLED",
                "SYSTEM",
                LOCKOUT_ACTOR,
                requestId,
                now);
        return cancelled;
    }

    private void expireGrantAndRequest(
            TenantActivationGrant grant,
            TenantProvisioningRequest request,
            Instant now,
            String requestId) {
        if (!repository.markActivationGrantExpired(
                grant.id(), grant.attemptCount(), now)
                || !repository.markExpired(request.id(), request.version(), now)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        repository.cancelPendingNotificationByRequest(request.id());
        audit(
                request.expired(now),
                "EXPIRED",
                "SYSTEM",
                EXPIRY_ACTOR,
                requestId,
                now);
    }

    private TenantProvisioningRequest expireIfNeeded(
            TenantProvisioningRequest request,
            Instant now,
            String requestId) {
        if (!"REQUESTED".equals(request.status()) || now.isBefore(request.expiresAt())) {
            return request;
        }
        if (!repository.markExpired(request.id(), request.version(), now)) {
            throw new BusinessException(
                    IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        }
        repository.expireActivationGrantByRequest(request.id(), now);
        repository.cancelPendingNotificationByRequest(request.id());
        TenantProvisioningRequest expired = request.expired(now);
        audit(
                expired,
                "EXPIRED",
                "SYSTEM",
                EXPIRY_ACTOR,
                requestId,
                now);
        return expired;
    }

    private void audit(
            TenantProvisioningRequest request,
            String phase,
            String actorType,
            String actorId,
            String requestId,
            Instant occurredAt) {
        audit(
                request,
                phase,
                actorType,
                actorId,
                requestId,
                request.changeReference(),
                occurredAt);
    }

    private void audit(
            TenantProvisioningRequest request,
            String phase,
            String actorType,
            String actorId,
            String requestId,
            String changeReference,
            Instant occurredAt) {
        repository.insertAudit(
                request.id(),
                request.tenantId(),
                request.ownerSubjectId(),
                phase,
                actorType,
                actorId,
                requestId,
                changeReference,
                occurredAt);
    }

    private String normalizeChangeReference(String changeReference) {
        String normalized = trimmed(changeReference);
        if (!SAFE_REFERENCE.matcher(normalized).matches()) {
            throw invalidRequest();
        }
        return normalized;
    }

    private NormalizedCommand normalize(CreateTenantProvisioningCommand command) {
        if (command == null) {
            throw invalidRequest();
        }
        String tenantCode = lower(command.tenantCode());
        String tenantName = trimmed(command.tenantName());
        String ownerUsername = lower(command.ownerUsername());
        String ownerDisplayName = trimmed(command.ownerDisplayName());
        String deliveryChannel = trimmed(command.deliveryChannel()).toUpperCase(Locale.ROOT);
        String deliveryAddress = lower(command.deliveryAddress());
        String idempotencyKey = trimmed(command.idempotencyKey());
        String changeReference = trimmed(command.changeReference());
        if (!TENANT_CODE.matcher(tenantCode).matches()
                || tenantName.length() < 2
                || tenantName.length() > 80
                || !USERNAME.matcher(ownerUsername).matches()
                || ownerDisplayName.isEmpty()
                || ownerDisplayName.length() > 80
                || !"EMAIL".equals(deliveryChannel)
                || !EMAIL.matcher(deliveryAddress).matches()
                || !SAFE_REFERENCE.matcher(idempotencyKey).matches()
                || !SAFE_REFERENCE.matcher(changeReference).matches()) {
            throw invalidRequest();
        }
        return new NormalizedCommand(
                tenantCode,
                tenantName,
                ownerUsername,
                ownerDisplayName,
                deliveryChannel,
                deliveryAddress,
                idempotencyKey,
                changeReference);
    }

    private void requireActor(PlatformProvisioningActor actor) {
        if (actor == null
                || actor.tenantId() != null
                || !safe(actor.serviceId())
                || !safe(actor.requestId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private void requireSafeRequestId(String requestId) {
        if (!safe(requestId)) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }
    }

    private String fingerprint(NormalizedCommand command) {
        return sha256(String.join(
                "\u001f",
                command.tenantCode(),
                command.tenantName(),
                command.ownerUsername(),
                command.ownerDisplayName(),
                command.deliveryChannel(),
                command.deliveryAddress(),
                command.changeReference()));
    }

    private boolean constantTimeSecretMatches(String value, String expectedHash) {
        if (value == null || value.length() < 43 || value.length() > 128) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(expectedHash),
                    HexFormat.of().parseHex(sha256(value)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static boolean validRawPassword(String password) {
        return password != null && password.length() >= 12 && password.length() <= 128;
    }

    private static String lower(String value) {
        return trimmed(value).toLowerCase(Locale.ROOT);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean safe(String value) {
        return value != null && SAFE_REFERENCE.matcher(value).matches();
    }

    private static BusinessException invalidRequest() {
        return new BusinessException(
                IdentityErrorCode.INVALID_TENANT_PROVISIONING_REQUEST);
    }

    private record NormalizedCommand(
            String tenantCode,
            String tenantName,
            String ownerUsername,
            String ownerDisplayName,
            String deliveryChannel,
            String deliveryAddress,
            String idempotencyKey,
            String changeReference) {
    }
}
