package com.beifanghui.backend.identity.infrastructure;

import com.beifanghui.backend.identity.application.AccessTokenService;
import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.auth.token-store", havingValue = "redis")
public class RedisAccessTokenService implements AccessTokenService {

    private static final String KEY_PREFIX = "bfh:auth:token:";
    private static final String ROLE_SEPARATOR = "\u001f";
    private static final ZoneOffset CHINA_ZONE_OFFSET = ZoneOffset.ofHours(8);

    private final StringRedisTemplate redisTemplate;
    private final Duration tokenTtl;

    public RedisAccessTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.token-ttl:8h}") Duration tokenTtl) {
        this.redisTemplate = redisTemplate;
        this.tokenTtl = tokenTtl;
    }

    @Override
    public AccessSession issue(IdentityPrincipal principal) {
        String token = "bfa_" + UUID.randomUUID().toString().replace("-", "");
        AccessSession session = new AccessSession(
                token,
                "Bearer",
                OffsetDateTime.now(CHINA_ZONE_OFFSET).plus(tokenTtl),
                principal);
        String redisKey = key(token);
        redisTemplate.opsForHash().putAll(redisKey, Map.of(
                "tokenType", session.tokenType(),
                "expiresAt", session.expiresAt().toString(),
                "userId", principal.userId(),
                "displayName", principal.displayName(),
                "accountType", principal.accountType(),
                "roles", String.join(ROLE_SEPARATOR, principal.roles())));
        redisTemplate.expire(redisKey, tokenTtl);
        return session;
    }

    @Override
    public AccessSession find(String accessToken) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(accessToken));
        if (values.isEmpty()) {
            return null;
        }
        String rolesValue = field(values, "roles");
        List<String> roles = rolesValue.isBlank()
                ? List.of()
                : List.of(rolesValue.split(ROLE_SEPARATOR, -1));
        IdentityPrincipal principal = new IdentityPrincipal(
                field(values, "userId"),
                field(values, "displayName"),
                field(values, "accountType"),
                roles);
        AccessSession session = new AccessSession(
                accessToken,
                field(values, "tokenType"),
                OffsetDateTime.parse(field(values, "expiresAt")),
                principal);
        if (session.expiresAt().isBefore(OffsetDateTime.now(CHINA_ZONE_OFFSET))) {
            revoke(accessToken);
            return null;
        }
        return session;
    }

    @Override
    public void revoke(String accessToken) {
        redisTemplate.delete(key(accessToken));
    }

    private String key(String accessToken) {
        return KEY_PREFIX + accessToken;
    }

    private String field(Map<Object, Object> values, String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new IllegalStateException("Redis 登录会话缺少字段：" + name);
        }
        return value.toString();
    }
}
