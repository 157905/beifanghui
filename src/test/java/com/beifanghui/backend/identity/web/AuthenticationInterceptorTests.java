package com.beifanghui.backend.identity.web;

import com.beifanghui.backend.identity.api.MockLoginRequest;
import com.beifanghui.backend.identity.api.MockLoginResponse;
import com.beifanghui.backend.identity.application.MockLoginService;
import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationInterceptorTests {

    private final MockLoginService loginService = new MockLoginService();
    private final AuthenticationInterceptor interceptor = new AuthenticationInterceptor(loginService);

    @Test
    void shouldAuthenticateMatchingUserEndpoint() {
        MockLoginResponse login = loginService.login(new MockLoginRequest("殷子聪", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/app/access/me");
        request.addHeader("Authorization", "Bearer " + login.accessToken());

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertNotNull(request.getAttribute(AuthenticationInterceptor.PRINCIPAL_ATTRIBUTE));
    }

    @Test
    void shouldRejectCrossTerminalAccess() {
        MockLoginResponse login = loginService.login(new MockLoginRequest("普通用户", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/access/me");
        request.addHeader("Authorization", "Bearer " + login.accessToken());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals("AUTH_403_001", exception.errorCode().code());
    }
}
