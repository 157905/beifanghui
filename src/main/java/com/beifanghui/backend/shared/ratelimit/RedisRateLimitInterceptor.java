package com.beifanghui.backend.shared.ratelimit;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.identity.web.AuthenticationInterceptor;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class RedisRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "bfh:rate:";
    private static final Pattern PAYMENT_PATH = Pattern.compile("^/api/v1/app/orders/\\d+/mock-pay$");
    private static final Pattern REFUND_PATH = Pattern.compile("^/api/v1/app/orders/\\d+/refund-applications$");
    private static final Pattern VERIFICATION_PATH =
            Pattern.compile("^/api/v1/(admin|ops)/verifications/consume$");
    private static final DefaultRedisScript<List> COUNTER_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RedisRateLimitInterceptor(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Rule rule = matchRule(request.getRequestURI());
        if (rule == null) {
            return true;
        }

        String subject = resolveSubject(request);
        String key = KEY_PREFIX + rule.name() + ":" + sanitize(subject);
        List<?> result = redisTemplate.execute(
                COUNTER_SCRIPT,
                List.of(key),
                String.valueOf(properties.getWindow().toMillis()));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis 未返回有效的限流结果");
        }

        long current = ((Number) result.get(0)).longValue();
        long ttlMillis = Math.max(0, ((Number) result.get(1)).longValue());
        long retryAfterSeconds = Math.max(1, (ttlMillis + 999) / 1000);
        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rule.limit() - current)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(retryAfterSeconds));

        if (current > rule.limit()) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            throw new BusinessException(
                    CommonErrorCode.RATE_LIMITED,
                    "请求过于频繁，请在 " + retryAfterSeconds + " 秒后重试");
        }
        return true;
    }

    private Rule matchRule(String uri) {
        if ("/api/v1/app/auth/mock-login".equals(uri)
                || "/api/v1/app/auth/wechat-login".equals(uri)) {
            return new Rule("login", properties.getLoginLimit());
        }
        if ("/api/v1/app/orders".equals(uri)) {
            return new Rule("create-order", properties.getCreateOrderLimit());
        }
        if (PAYMENT_PATH.matcher(uri).matches()) {
            return new Rule("payment", properties.getPaymentLimit());
        }
        if (REFUND_PATH.matcher(uri).matches()) {
            return new Rule("refund", properties.getRefundLimit());
        }
        if (VERIFICATION_PATH.matcher(uri).matches()) {
            return new Rule("verification", properties.getVerificationLimit());
        }
        return null;
    }

    private String resolveSubject(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticationInterceptor.PRINCIPAL_ATTRIBUTE);
        if (value instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return request.getRemoteAddr();
    }

    private String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9:_-]", "_");
    }

    private record Rule(String name, int limit) {
    }
}
