package dev.ainer.authorizationserver.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public final class AinerLoginPageController {

    static final String SERVICE_UNAVAILABLE_SESSION_ATTRIBUTE =
            AinerLoginPageController.class.getName() + ".SERVICE_UNAVAILABLE";

    private final AinerLoginPageRenderer renderer;

    public AinerLoginPageController(AinerLoginPageRenderer renderer) {
        this.renderer = renderer;
    }

    @GetMapping("/login")
    void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AinerLoginPageState state = state(request);
        int status = state == AinerLoginPageState.SERVICE_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE.value()
                : HttpStatus.OK.value();
        renderer.render(request, response, state, status);
    }

    private static AinerLoginPageState state(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null
                && Boolean.TRUE.equals(session.getAttribute(
                        SERVICE_UNAVAILABLE_SESSION_ATTRIBUTE))) {
            session.removeAttribute(SERVICE_UNAVAILABLE_SESSION_ATTRIBUTE);
            return AinerLoginPageState.SERVICE_UNAVAILABLE;
        }
        return request.getParameter("error") == null
                ? AinerLoginPageState.NORMAL
                : AinerLoginPageState.CREDENTIAL_ERROR;
    }
}
