package dev.ainer.authorizationserver.identity;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.identity.account.application.NotificationGatewayActor;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationDeliveryStatus;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceipt;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptCommand;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptResult;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(
        "/internal/identity/tenant-provisioning-notification-receipts")
@ConditionalOnProperty(
        prefix = "ainer.identity.provisioning-notification-receipts",
        name = "enabled",
        havingValue = "true")
public class TenantProvisioningNotificationReceiptController {

    private final TenantProvisioningNotificationReceiptService service;
    private final NotificationGatewayActorResolver actorResolver;
    private final Counter deliveredCounter;
    private final Counter failedCounter;

    public TenantProvisioningNotificationReceiptController(
            TenantProvisioningNotificationReceiptService service,
            NotificationGatewayActorResolver actorResolver,
            MeterRegistry meterRegistry) {
        this.service = service;
        this.actorResolver = actorResolver;
        this.deliveredCounter = meterRegistry.counter(
                "ainer.identity.tenant.provisioning.notification.delivered");
        this.failedCounter = meterRegistry.counter(
                "ainer.identity.tenant.provisioning.notification.failed");
    }

    @PostMapping
    public ApiResponse<ReceiptResponse> record(
            @Valid @RequestBody ReceiptRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        NotificationGatewayActor actor =
                actorResolver.require(authentication, request);
        TenantProvisioningNotificationReceiptResult result = service.record(
                new TenantProvisioningNotificationReceiptCommand(
                        body.eventId(),
                        body.notificationId(),
                        body.status(),
                        body.occurredAt(),
                        body.failureCode()),
                actor);
        if (result.created()) {
            counter(result.receipt().status()).increment();
        }
        return ApiResponse.success(
                ReceiptResponse.from(result), actor.requestId());
    }

    private Counter counter(
            TenantProvisioningNotificationDeliveryStatus status) {
        return status == TenantProvisioningNotificationDeliveryStatus.DELIVERED
                ? deliveredCounter
                : failedCounter;
    }

    public record ReceiptRequest(
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,128}")
            String eventId,
            @NotNull UUID notificationId,
            @NotNull TenantProvisioningNotificationDeliveryStatus status,
            @NotNull Instant occurredAt,
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")
            String failureCode) {
    }

    public record ReceiptResponse(
            UUID id,
            UUID notificationId,
            String eventId,
            String status,
            String failureCode,
            Instant occurredAt,
            Instant receivedAt,
            boolean created) {

        static ReceiptResponse from(
                TenantProvisioningNotificationReceiptResult result) {
            TenantProvisioningNotificationReceipt receipt = result.receipt();
            return new ReceiptResponse(
                    receipt.id(),
                    receipt.notificationId(),
                    receipt.gatewayEventId(),
                    receipt.status().name(),
                    receipt.failureCode(),
                    receipt.occurredAt(),
                    receipt.receivedAt(),
                    result.created());
        }
    }
}
