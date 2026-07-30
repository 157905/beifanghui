package com.beifanghui.backend.identity.domain;

import java.time.OffsetDateTime;

public record AccessSession(
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt,
        IdentityPrincipal principal) {
}
