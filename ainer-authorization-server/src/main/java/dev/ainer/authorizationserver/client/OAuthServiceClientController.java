package dev.ainer.authorizationserver.client;

import dev.ainer.authorizationserver.client.OAuthServiceClientControlService.ClientView;
import dev.ainer.authorizationserver.client.OAuthServiceClientControlService.CreateCommand;
import dev.ainer.authorizationserver.client.OAuthServiceClientControlService.IssuedClient;
import dev.ainer.authorizationserver.client.OAuthServiceClientControlService.OperationActor;
import dev.ainer.authorizationserver.client.OAuthServiceClientControlService.RetireCommand;
import dev.ainer.authorizationserver.client.OAuthServiceClientControlService.RotateCommand;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/internal/oauth-service-clients")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.client-control",
        name = "enabled",
        havingValue = "true")
public class OAuthServiceClientController {

    private static final String MANAGE_AUTHORITY = "SCOPE_" + OAuthClientControlConfiguration.MANAGE_SCOPE;

    private final OAuthServiceClientControlService service;

    public OAuthServiceClientController(OAuthServiceClientControlService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<IssuedClient> create(
            @Valid @RequestBody CreateServiceClientRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        OperationActor actor = actor(authentication, request);
        IssuedClient issued = service.create(
                new CreateCommand(
                        body.clientId(),
                        body.clientName(),
                        body.tenantId(),
                        body.scopes(),
                        body.changeReference()),
                actor);
        return ApiResponse.success(issued, actor.requestId());
    }

    @GetMapping("/{clientId}")
    public ApiResponse<ClientView> find(
            @PathVariable String clientId,
            Authentication authentication,
            HttpServletRequest request) {
        OperationActor actor = actor(authentication, request);
        return ApiResponse.success(service.find(clientId, actor), actor.requestId());
    }

    @PostMapping("/{clientId}/rotations")
    public ApiResponse<IssuedClient> rotate(
            @PathVariable String clientId,
            @Valid @RequestBody RotateServiceClientRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        OperationActor actor = actor(authentication, request);
        IssuedClient issued = service.rotate(
                clientId,
                new RotateCommand(
                        body.replacementClientId(),
                        body.replacementClientName(),
                        body.changeReference()),
                actor);
        return ApiResponse.success(issued, actor.requestId());
    }

    @PostMapping("/{clientId}/retirement")
    public ApiResponse<ClientView> retire(
            @PathVariable String clientId,
            @Valid @RequestBody RetireServiceClientRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        OperationActor actor = actor(authentication, request);
        return ApiResponse.success(
                service.retire(clientId, new RetireCommand(body.changeReference()), actor),
                actor.requestId());
    }

    private OperationActor actor(Authentication authentication, HttpServletRequest request) {
        AuthenticatedService authenticatedService = JwtAuthenticatedServiceFactory.from(authentication);
        authenticatedService.requireAuthority(MANAGE_AUTHORITY);
        return new OperationActor(
                authenticatedService.serviceId(),
                authenticatedService.tenantId(),
                RequestIds.currentOrCreate(request));
    }

    public record CreateServiceClientRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,99}") String clientId,
            @NotBlank @Size(max = 200) String clientName,
            @NotNull UUID tenantId,
            @NotEmpty @Size(max = 16) Set<@Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String> scopes,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }

    public record RotateServiceClientRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,99}") String replacementClientId,
            @Size(max = 200) String replacementClientName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }

    public record RetireServiceClientRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {
    }
}
