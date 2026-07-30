package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.api.MockLoginRequest;
import com.beifanghui.backend.identity.api.MockLoginResponse;
import com.beifanghui.backend.identity.infrastructure.InMemoryAccessTokenService;
import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockLoginServiceTests {

    private final InMemoryAccessTokenService tokenService = new InMemoryAccessTokenService();
    private final MockLoginService service = new MockLoginService(tokenService);

    @Test
    void 默认创建用户端会话() {
        MockLoginResponse response = service.login(new MockLoginRequest("殷子聪", null));

        assertEquals("USER", response.user().accountType());
        assertEquals("Bearer", response.tokenType());
        assertNotNull(tokenService.find(response.accessToken()));
    }

    @Test
    void 拒绝未知账号类型() {
        assertThrows(BusinessException.class,
                () -> service.login(new MockLoginRequest("测试用户", "UNKNOWN")));
    }
}
