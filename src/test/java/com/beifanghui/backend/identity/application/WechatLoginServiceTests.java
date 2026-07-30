package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.api.LoginResponse;
import com.beifanghui.backend.identity.api.WechatLoginRequest;
import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.WechatCodeSession;
import com.beifanghui.backend.identity.infrastructure.InMemoryAccessTokenService;
import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WechatLoginServiceTests {

    private final InMemoryAccessTokenService tokenService = new InMemoryAccessTokenService();
    private final WechatLoginService service = new WechatLoginService(
            code -> new WechatCodeSession("openid-test-001", "unionid-test-001", "session-key"),
            tokenService);

    @Test
    void 微信Code换取用户身份并签发令牌() {
        LoginResponse response = service.login(new WechatLoginRequest("wx-code", "微信测试用户"));

        assertEquals("USER", response.user().accountType());
        assertEquals("微信测试用户", response.user().displayName());
        assertFalse(response.user().userId().contains("openid-test-001"));
        AccessSession session = tokenService.find(response.accessToken());
        assertEquals("WECHAT", session.principal().identityProvider());
        assertEquals("openid-test-001", session.principal().externalIdentity());
    }

    @Test
    void 拒绝空微信Code() {
        assertThrows(BusinessException.class,
                () -> service.login(new WechatLoginRequest(" ", "测试用户")));
    }
}
