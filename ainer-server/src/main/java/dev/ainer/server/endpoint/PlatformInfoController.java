package dev.ainer.server.endpoint;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.authorization.spring.EndpointAccess;
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
    @EndpointAccess(
            kind = EndpointAccess.Kind.PUBLIC,
            reason = "平台信息端点：只返回产品名、运行模式与 JDK feature 版本，不含租户或用户数据；"
                    + "默认列在 ainer.security.resource-server.public-paths 里，容器探活与客户端首连"
                    + "需要匿名可达（docs/security.md §3.4）。")
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
