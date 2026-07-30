package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.api.LoginResponse;
import com.beifanghui.backend.identity.api.WechatLoginRequest;
import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import com.beifanghui.backend.identity.domain.WechatCodeSession;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class WechatLoginService {

    private final WechatCodeSessionGateway codeSessionGateway;
    private final AccessTokenService accessTokenService;

    public WechatLoginService(
            WechatCodeSessionGateway codeSessionGateway,
            AccessTokenService accessTokenService) {
        this.codeSessionGateway = codeSessionGateway;
        this.accessTokenService = accessTokenService;
    }

    public LoginResponse login(WechatLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.code())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "微信登录 code 不能为空");
        }
        String displayName = normalizeDisplayName(request.displayName());
        WechatCodeSession codeSession = codeSessionGateway.exchange(request.code().trim());
        String internalUserId = "wx-" + UUID.nameUUIDFromBytes(
                ("WECHAT:" + codeSession.openId()).getBytes(StandardCharsets.UTF_8));
        IdentityPrincipal principal = new IdentityPrincipal(
                internalUserId,
                displayName,
                "USER",
                List.of("ROLE_USER"),
                "WECHAT",
                codeSession.openId());
        AccessSession accessSession = accessTokenService.issue(principal);
        return LoginResponse.from(accessSession);
    }

    private String normalizeDisplayName(String value) {
        if (!StringUtils.hasText(value)) {
            return "微信用户";
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "displayName 最长为 64 个字符");
        }
        return normalized;
    }
}
