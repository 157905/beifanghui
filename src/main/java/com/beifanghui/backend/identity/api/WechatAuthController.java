package com.beifanghui.backend.identity.api;

import com.beifanghui.backend.identity.application.WechatLoginService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/auth")
public class WechatAuthController {

    private final WechatLoginService wechatLoginService;

    public WechatAuthController(WechatLoginService wechatLoginService) {
        this.wechatLoginService = wechatLoginService;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<LoginResponse> wechatLogin(
            @RequestBody WechatLoginRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("微信登录成功", wechatLoginService.login(body), TraceIds.from(request));
    }
}
