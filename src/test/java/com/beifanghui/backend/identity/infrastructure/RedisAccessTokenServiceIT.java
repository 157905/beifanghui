package com.beifanghui.backend.identity.infrastructure;

import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RedisAccessTokenServiceIT {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisAccessTokenService service;

    @BeforeEach
    void 连接本机Redis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        service = new RedisAccessTokenService(redisTemplate, Duration.ofMinutes(5));
    }

    @AfterEach
    void 关闭连接() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void 令牌可以跨服务实例读取并撤销() {
        IdentityPrincipal principal = new IdentityPrincipal(
                "redis-user-1", "Redis测试用户", "USER", List.of("ROLE_USER"));
        AccessSession issued = service.issue(principal);
        RedisAccessTokenService anotherInstance = new RedisAccessTokenService(
                redisTemplate, Duration.ofMinutes(5));

        AccessSession loaded = anotherInstance.find(issued.accessToken());
        assertNotNull(loaded);
        assertEquals(principal.userId(), loaded.principal().userId());

        anotherInstance.revoke(issued.accessToken());
        assertNull(service.find(issued.accessToken()));
    }
}
