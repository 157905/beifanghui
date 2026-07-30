package com.beifanghui.backend.shared.ratelimit;

import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisRateLimitInterceptorIT {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisRateLimitInterceptor interceptor;

    @BeforeEach
    void 连接Redis并设置两次限制() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        RateLimitProperties properties = new RateLimitProperties();
        properties.setWindow(Duration.ofSeconds(30));
        properties.setLoginLimit(2);
        interceptor = new RedisRateLimitInterceptor(redisTemplate, properties);
        clearTestKeys();
    }

    @AfterEach
    void 清理测试键并关闭连接() {
        if (redisTemplate != null) {
            clearTestKeys();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void 超过窗口限制返回429业务错误() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/app/auth/mock-login");
        request.setRemoteAddr("rate-limit-it");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        assertEquals("SYSTEM_429_001", exception.errorCode().code());
        assertEquals("2", response.getHeader("X-RateLimit-Limit"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertTrue(Integer.parseInt(response.getHeader("Retry-After")) > 0);
    }

    private void clearTestKeys() {
        Set<String> keys = redisTemplate.keys("bfh:rate:*:rate-limit-it");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
