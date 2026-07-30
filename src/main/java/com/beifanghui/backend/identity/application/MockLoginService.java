package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.api.MockLoginRequest;
import com.beifanghui.backend.identity.api.MockLoginResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockLoginService {

    private static final Set<String> ACCOUNT_TYPES = Set.of("USER", "ADMIN", "OPS");
    private final ConcurrentHashMap<String, MockLoginResponse> sessions = new ConcurrentHashMap<>();

    public MockLoginResponse login(MockLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.displayName())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "displayName 不能为空");
        }
        String displayName = request.displayName().trim();
        if (displayName.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "displayName 最长 64 个字符");
        }
        String accountType = StringUtils.hasText(request.accountType())
                ? request.accountType().trim().toUpperCase(Locale.ROOT)
                : "USER";
        if (!ACCOUNT_TYPES.contains(accountType)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "accountType 仅支持 USER、ADMIN、OPS");
        }

        String token = "mock_" + UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusHours(8);
        String stableUserId = "mock-" + UUID.nameUUIDFromBytes(
                (accountType + ":" + displayName).getBytes(StandardCharsets.UTF_8));
        MockLoginResponse.MockPrincipal principal = new MockLoginResponse.MockPrincipal(
                stableUserId, displayName, accountType, List.of("ROLE_" + accountType));
        MockLoginResponse response = new MockLoginResponse(token, "Bearer", expiresAt, principal);
        sessions.put(token, response);
        return response;
    }

    public MockLoginResponse findSession(String token) {
        MockLoginResponse session = sessions.get(token);
        if (session != null && session.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.ofHours(8)))) {
            sessions.remove(token);
            return null;
        }
        return session;
    }
}
