package com.beifanghui.backend.identity.api;

import java.time.OffsetDateTime;
import java.util.List;

public record MockLoginResponse(
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt,
        MockPrincipal user) {

    public record MockPrincipal(String userId, String displayName, String accountType, List<String> roles) {
    }
}
