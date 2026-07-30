package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.api.LoginResponse;
import com.beifanghui.backend.identity.api.MockLoginRequest;
import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MockLoginService {

    private static final Set<String> ACCOUNT_TYPES = Set.of("USER", "ADMIN", "OPS");
    private final AccessTokenService accessTokenService;

    public MockLoginService(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    public LoginResponse login(MockLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.displayName())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "displayName 不能为空");
        }
        String displayName = request.displayName().trim();
        if (displayName.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "displayName 最长为 64 个字符");
        }
        String accountType = StringUtils.hasText(request.accountType())
                ? request.accountType().trim().toUpperCase(Locale.ROOT)
                : "USER";
        if (!ACCOUNT_TYPES.contains(accountType)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "accountType 仅支持 USER、ADMIN、OPS");
        }

        String stableUserId = "mock-" + UUID.nameUUIDFromBytes(
                (accountType + ":" + displayName).getBytes(StandardCharsets.UTF_8));
        IdentityPrincipal principal = new IdentityPrincipal(
                stableUserId, displayName, accountType, List.of("ROLE_" + accountType));
        AccessSession session = accessTokenService.issue(principal);
        return LoginResponse.from(session);
    }
}
