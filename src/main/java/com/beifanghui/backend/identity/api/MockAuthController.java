package com.beifanghui.backend.identity.api;

import com.beifanghui.backend.identity.application.MockLoginService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/auth")
public class MockAuthController {

    private final MockLoginService mockLoginService;

    public MockAuthController(MockLoginService mockLoginService) {
        this.mockLoginService = mockLoginService;
    }

    @PostMapping("/mock-login")
    public ApiResponse<MockLoginResponse> mockLogin(
            @RequestBody MockLoginRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("模拟登录成功", mockLoginService.login(body), TraceIds.from(request));
    }
}
