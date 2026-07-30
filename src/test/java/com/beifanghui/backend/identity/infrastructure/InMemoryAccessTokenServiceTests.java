package com.beifanghui.backend.identity.infrastructure;

import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryAccessTokenServiceTests {

    private final InMemoryAccessTokenService service = new InMemoryAccessTokenService();

    @Test
    void 签发后可以查询令牌() {
        AccessSession session = service.issue(
                new IdentityPrincipal("user-1", "测试用户", "USER", List.of("ROLE_USER")));

        assertNotNull(service.find(session.accessToken()));
    }

    @Test
    void 撤销后令牌立即失效() {
        AccessSession session = service.issue(
                new IdentityPrincipal("user-1", "测试用户", "USER", List.of("ROLE_USER")));

        service.revoke(session.accessToken());

        assertNull(service.find(session.accessToken()));
    }
}
