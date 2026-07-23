package dev.ainer.server.endpoint;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.spring.runtime.AinerRuntimeProperties;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformInfoController {

    private final AinerRuntimeProperties runtimeProperties;

    public PlatformInfoController(AinerRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    @GetMapping("/info")
    public ApiResponse<PlatformInfo> info(HttpServletRequest request) {
        PlatformInfo info = new PlatformInfo(
                "Ainer Boot",
                runtimeProperties.getMode().name(),
                Runtime.version().feature());
        return ApiResponse.success(info, RequestIds.currentOrCreate(request));
    }

    public record PlatformInfo(String name, String runtimeMode, int javaFeatureVersion) {
    }
}
