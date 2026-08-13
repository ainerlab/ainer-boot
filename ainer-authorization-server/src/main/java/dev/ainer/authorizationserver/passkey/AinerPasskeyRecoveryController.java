package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Two-person Passkey recovery control plane for foundation accounts. */
@Validated
@RestController
@RequestMapping("/internal/passkey-recovery")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.passkey.recovery",
        name = "enabled",
        havingValue = "true")
public class AinerPasskeyRecoveryController {

    private static final String REQUEST = "SCOPE_passkey.recovery.request.all";
    private static final String APPROVE = "SCOPE_passkey.recovery.approve.all";

    private final AinerPasskeyAdminRecoveryService recoveryService;
    private final AinerPasskeyRecoveryProperties settings;
    private final Counter requestedCounter;
    private final Counter executedCounter;

    public AinerPasskeyRecoveryController(
            AinerPasskeyAdminRecoveryService recoveryService,
            AinerPasskeyRecoveryProperties settings,
            MeterRegistry meterRegistry) {
        this.recoveryService = recoveryService;
        this.settings = settings;
        this.requestedCounter = meterRegistry.counter("ainer.passkey.recovery.requested");
        this.executedCounter = meterRegistry.counter("ainer.passkey.recovery.executed");
    }

    @PostMapping("/accounts/{accountId}/recovery-requests")
    public ApiResponse<AccountRecoveryRequestResponse> requestRecovery(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountRecoveryRequestBody body,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAuthority(authentication, REQUEST);
        AccountRecoveryRequestResponse response = AccountRecoveryRequestResponse.from(
                recoveryService.requestRecoveryForAccount(
                        service.serviceId(), accountId, body.incidentReference(), settings.getApprovalTtl()));
        requestedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    @PostMapping("/accounts/{accountId}/recovery-requests/{requestId}/approvals")
    public ApiResponse<AccountRecoveryRequestResponse> approve(
            @PathVariable UUID accountId,
            @PathVariable UUID requestId,
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService service = requireAuthority(authentication, APPROVE);
        AccountRecoveryRequestResponse response = AccountRecoveryRequestResponse.from(
                recoveryService.approveAndExecuteForAccount(service.serviceId(), accountId, requestId));
        executedCounter.increment();
        return ApiResponse.success(response, RequestIds.currentOrCreate(request));
    }

    private AuthenticatedService requireAuthority(Authentication authentication, String authority) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        service.requireAuthority(authority);
        return service;
    }

    public record AccountRecoveryRequestBody(@NotNull String incidentReference) {
    }

    public record AccountRecoveryRequestResponse(
            UUID id, UUID accountId, String requestedBy, String approvedBy,
            String incidentReference, String status) {
        static AccountRecoveryRequestResponse from(
                AinerPasskeyAdminRecoveryService.AccountRecoveryRequest request) {
            return new AccountRecoveryRequestResponse(
                    request.id(), request.accountId(), request.requestedBy(), request.approvedBy(),
                    request.incidentReference(), request.status());
        }
    }
}
