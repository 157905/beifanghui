package com.beifanghui.backend.identity.web;

import com.beifanghui.backend.shared.ratelimit.RedisRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebAuthenticationConfiguration implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final RedisRateLimitInterceptor rateLimitInterceptor;

    public WebAuthenticationConfiguration(
            AuthenticationInterceptor authenticationInterceptor,
            RedisRateLimitInterceptor rateLimitInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/api/v1/app/**", "/api/v1/admin/**", "/api/v1/ops/**")
                .excludePathPatterns(
                        "/api/v1/app/system/health",
                        "/api/v1/app/auth/mock-login");
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
