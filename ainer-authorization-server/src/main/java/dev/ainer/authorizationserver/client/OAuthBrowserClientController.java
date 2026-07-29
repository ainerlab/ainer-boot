package dev.ainer.authorizationserver.client;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/internal/oauth-browser-clients")
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.browser-client-control",
        name = "enabled",
        havingValue = "true")
@Validated
public class OAuthBrowserClientController {

    private static final String MANAGE_AUTHORITY =
            "SCOPE_" + AinerAuthorizationServerConfiguration.BROWSER_CLIENT_CONTROL_MANAGE_SCOPE;

    private final OAuthBrowserClientControlService service;

    public OAuthBrowserClientController(OAuthBrowserClientControlService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<OAuthBrowserClientControlService.BrowserClientView> create(
            @Valid @RequestBody CreateBrowserClientRequest body,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OAuthBrowserClientControlService.OperationActor actor = actor(authentication, servletRequest);
        return ApiResponse.success(service.create(
                new OAuthBrowserClientControlService.CreateCommand(
                        body.clientId(), body.clientName(),
                        body.redirectUri(), body.postLogoutRedirectUri(),
                        body.scopes(), body.changeReference()),
                actor), actor.requestId());
    }

    @GetMapping
    public ApiResponse<OAuthBrowserClientControlService.BrowserClientPage> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OAuthBrowserClientControlService.OperationActor actor = actor(authentication, servletRequest);
        return ApiResponse.success(service.list(page, size, actor), actor.requestId());
    }

    @GetMapping("/{clientId}")
    public ApiResponse<OAuthBrowserClientControlService.BrowserClientView> find(
            @PathVariable String clientId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OAuthBrowserClientControlService.OperationActor actor = actor(authentication, servletRequest);
        return ApiResponse.success(service.find(clientId, actor), actor.requestId());
    }

    @PostMapping("/{clientId}/rotations")
    public ApiResponse<OAuthBrowserClientControlService.BrowserClientView> rotate(
            @PathVariable String clientId,
            @Valid @RequestBody RotateBrowserClientRequest body,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OAuthBrowserClientControlService.OperationActor actor = actor(authentication, servletRequest);
        return ApiResponse.success(service.rotate(clientId,
                new OAuthBrowserClientControlService.RotateCommand(
                        body.replacementClientId(), body.replacementClientName(),
                        body.redirectUri(), body.postLogoutRedirectUri(),
                        body.changeReference()),
                actor), actor.requestId());
    }

    @PostMapping("/{clientId}/retirement")
    public ApiResponse<OAuthBrowserClientControlService.BrowserClientView> retire(
            @PathVariable String clientId,
            @Valid @RequestBody RetireBrowserClientRequest body,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OAuthBrowserClientControlService.OperationActor actor = actor(authentication, servletRequest);
        return ApiResponse.success(service.retire(clientId,
                new OAuthBrowserClientControlService.RetireCommand(body.changeReference()),
                actor), actor.requestId());
    }

    private static OAuthBrowserClientControlService.OperationActor actor(
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);
        service.requireAuthority(MANAGE_AUTHORITY);
        return new OAuthBrowserClientControlService.OperationActor(
                service.serviceId(), RequestIds.currentOrCreate(request));
    }

    public record CreateBrowserClientRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,99}") String clientId,
            @NotBlank @Size(max = 200) String clientName,
            @NotBlank String redirectUri,
            @NotBlank String postLogoutRedirectUri,
            @NotEmpty @Size(max = 16) Set<@Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String> scopes,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {}

    public record RotateBrowserClientRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,99}") String replacementClientId,
            @NotBlank @Size(max = 200) String replacementClientName,
            @NotBlank String redirectUri,
            @NotBlank String postLogoutRedirectUri,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {}

    public record RetireBrowserClientRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,200}") String changeReference) {}
}
