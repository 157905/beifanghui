package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.api.MockLoginRequest;
import com.beifanghui.backend.identity.api.MockLoginResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockLoginServiceTests {

    private final MockLoginService service = new MockLoginService();

    @Test
    void shouldCreateUserSessionByDefault() {
        MockLoginResponse response = service.login(new MockLoginRequest("殷子聪", null));

        assertEquals("USER", response.user().accountType());
        assertEquals("Bearer", response.tokenType());
        assertNotNull(service.findSession(response.accessToken()));
    }

    @Test
    void shouldRejectUnknownAccountType() {
        assertThrows(BusinessException.class,
                () -> service.login(new MockLoginRequest("测试用户", "UNKNOWN")));
    }
}
