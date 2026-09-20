package com.bank.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部服务鉴权过滤器。
 * <p>
 * 各微服务自身的 Spring Security 是全部放行的（信任网关已完成认证），
 * 因此在网关之外直接访问 8081~8085 曾经可以伪造 X-User-Id 冒充任意客户。
 * 本过滤器要求所有请求携带网关注入（或服务间调用显式声明）的内部令牌，
 * 使「绕过网关」这条路即使在本机可达的端口上也拿不到身份。
 */
@Slf4j
public class InternalApiAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expectedToken;

    public InternalApiAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken == null
                ? new byte[0]
                : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // 令牌为空一律拒绝：配置缺失时宁可 401，也不能退化成"不校验"
        if (expectedToken.length == 0 || !MessageDigest.isEqual(
                expectedToken,
                provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("内部令牌校验失败，拒绝请求: {} {} (来自 {})",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"INTERNAL_UNAUTHORIZED\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 由各服务通过 {@code @Import} 引入，注册到过滤器链最前端。
     */
    public static class Registration {

        @Bean
        FilterRegistrationBean<InternalApiAuthFilter> internalApiAuthFilterRegistration(
                @Value("${internal.api.token:}") String internalApiToken) {
            if (internalApiToken == null || internalApiToken.isBlank()) {
                log.error("internal.api.token 未配置，所有内部接口将返回 401");
            }
            FilterRegistrationBean<InternalApiAuthFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new InternalApiAuthFilter(internalApiToken));
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
