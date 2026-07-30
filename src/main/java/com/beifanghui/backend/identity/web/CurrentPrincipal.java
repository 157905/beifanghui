package com.beifanghui.backend.identity.web;

import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentPrincipal {

    private CurrentPrincipal() {
    }

    public static AuthenticatedPrincipal from(HttpServletRequest request) {
        Object principal = request.getAttribute(AuthenticationInterceptor.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            return authenticatedPrincipal;
        }
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
