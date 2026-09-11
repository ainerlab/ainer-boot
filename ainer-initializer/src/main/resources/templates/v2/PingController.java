package {{package.name}}.ping;

import dev.ainer.authorization.spring.EndpointAccess;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    @GetMapping
    @EndpointAccess(
            kind = EndpointAccess.Kind.AUTHENTICATED,
            reason = "脚手架自检端点：只回显 pong，不含业务数据。ADR-0052 规定生成工程只公开 "
                    + "/actuator/health，/api/ping 需要有效 JWT，所以这里是 AUTHENTICATED 而不是 "
                    + "PUBLIC；真实业务端点请写 @AinerAuthorize 或 DELEGATED。")
    public ApiResponse<String> ping(HttpServletRequest request) {
        return ApiResponse.success("pong", RequestIds.currentOrCreate(request));
    }
}
