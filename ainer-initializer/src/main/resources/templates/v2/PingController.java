package {{package.name}}.ping;

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
    public ApiResponse<String> ping(HttpServletRequest request) {
        return ApiResponse.success("pong", RequestIds.currentOrCreate(request));
    }
}
