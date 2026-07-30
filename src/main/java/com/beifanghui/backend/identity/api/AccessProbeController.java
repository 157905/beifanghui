package com.beifanghui.backend.identity.api;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccessProbeController {

    @GetMapping("/app/access/me")
    public ApiResponse<AuthenticatedPrincipal> appMe(HttpServletRequest request) {
        return current(request);
    }

    @GetMapping("/admin/access/me")
    public ApiResponse<AuthenticatedPrincipal> adminMe(HttpServletRequest request) {
        return current(request);
    }

    @GetMapping("/ops/access/me")
    public ApiResponse<AuthenticatedPrincipal> opsMe(HttpServletRequest request) {
        return current(request);
    }

    private ApiResponse<AuthenticatedPrincipal> current(HttpServletRequest request) {
        return ApiResponse.success(CurrentPrincipal.from(request), TraceIds.from(request));
    }
}
