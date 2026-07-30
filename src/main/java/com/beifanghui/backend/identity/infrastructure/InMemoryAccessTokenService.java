package com.beifanghui.backend.identity.infrastructure;

import com.beifanghui.backend.identity.application.AccessTokenService;
import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "app.auth.token-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryAccessTokenService implements AccessTokenService {

    private static final ZoneOffset CHINA_ZONE_OFFSET = ZoneOffset.ofHours(8);
    private final ConcurrentHashMap<String, AccessSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AccessSession issue(IdentityPrincipal principal) {
        String token = "mock_" + UUID.randomUUID().toString().replace("-", "");
        AccessSession session = new AccessSession(
                token,
                "Bearer",
                OffsetDateTime.now(CHINA_ZONE_OFFSET).plusHours(8),
                principal);
        sessions.put(token, session);
        return session;
    }

    @Override
    public AccessSession find(String accessToken) {
        AccessSession session = sessions.get(accessToken);
        if (session != null && session.expiresAt().isBefore(OffsetDateTime.now(CHINA_ZONE_OFFSET))) {
            sessions.remove(accessToken);
            return null;
        }
        return session;
    }

    @Override
    public void revoke(String accessToken) {
        sessions.remove(accessToken);
    }
}
