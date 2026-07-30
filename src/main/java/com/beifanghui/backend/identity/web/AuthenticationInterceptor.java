package com.beifanghui.backend.identity.web;

import com.beifanghui.backend.identity.application.AccessTokenService;
import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = AuthenticationInterceptor.class.getName() + ".principal";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;

    public AuthenticationInterceptor(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "缺少有效的 Bearer Token");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        AccessSession session = accessTokenService.find(token);
        if (session == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录令牌无效或已过期");
        }

        IdentityPrincipal source = session.principal();
        String requiredAccountType = requiredAccountType(request.getRequestURI());
        if (!requiredAccountType.equals(source.accountType())) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    source.accountType() + " 账号不能访问 " + requiredAccountType + " 端接口");
        }

        request.setAttribute(PRINCIPAL_ATTRIBUTE, new AuthenticatedPrincipal(
                source.userId(), source.displayName(), source.accountType(), source.roles(),
                source.identityProvider(), source.externalIdentity()));
        return true;
    }

    private String requiredAccountType(String requestUri) {
        if (requestUri.startsWith("/api/v1/admin/")) {
            return "ADMIN";
        }
        if (requestUri.startsWith("/api/v1/ops/")) {
            return "OPS";
        }
        return "USER";
    }
}
