package com.bank.customer.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 解析当前登录用户 ID：
 * 1. 优先读取网关校验 JWT 后注入的 X-User-Id 头；
 * 2. 兜底直接校验 Authorization Bearer token。
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    /**
     * @return 当前用户 ID，未认证时返回 null
     */
    public Long resolve(HttpServletRequest request) {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader != null) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                // 非法头，继续走 token 校验
            }
        }
        return resolveFromToken(request.getHeader(AUTHORIZATION_HEADER));
    }

    private Long resolveFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!tokenProvider.validateToken(token)) {
            return null;
        }
        return tokenProvider.getUserId(token);
    }
}
