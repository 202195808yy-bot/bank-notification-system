package com.bank.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.List;

/**
 * 全局 JWT 认证过滤器：
 * 1. 放行公开接口（登录、注册）；
 * 2. 校验 JWT，拒绝无效请求；
 * 3. 管理路径（模板、事件）要求 ADMIN 角色；
 * 4. 清除客户端伪造的同名头，向下游注入 X-User-Id / X-User-Role / X-Internal-Token。
 *    内部令牌是下游服务识别「请求确实经过网关」的依据，见 InternalApiAuthFilter。
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /**
     * 需要 ADMIN 角色的路径前缀。必须是网关的真实路由（Path 谓词见 docker-compose.yml），
     * 早期写的 "/admin/" 在这套路由里永远匹配不上，等于没有角色校验。
     */
    private static final List<String> ADMIN_PATH_PREFIXES = List.of(
            "/api/templates",
            "/api/events"
    );

    /** 无需认证即可访问的路径前缀 */
    private final List<String> openPathPrefixes = List.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    private final Key key;
    private final String internalApiToken;

    public JwtAuthFilter(@Value("${jwt.secret}") String base64Secret,
                         @Value("${internal.api.token:}") String internalApiToken) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.internalApiToken = internalApiToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 放行公开接口（登录、注册）——仍然要盖内部令牌，否则下游过滤器会拒
        if (openPathPrefixes.stream().anyMatch(path::startsWith)) {
            log.debug("放行公开路径: {}", path);
            return chain.filter(withInternalToken(exchange));
        }

        // 2. 检查 Authorization 头
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("缺少有效 Authorization 头: {}", path);
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        // 3. 验证 JWT，提取用户信息
        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        // 4. 授权拦截：模板与事件管理接口需要 ADMIN 角色
        if (isAdminPath(path) && !"ADMIN".equals(role)) {
            log.warn("角色不足，拒绝访问: {} (role={})", path, role);
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // 5. 清除客户端伪造的同名头，注入内部令牌与可信用户信息后传递给下游微服务
        return chain.filter(withInternalToken(exchange, userId, role));
    }

    /** 仅透传内部令牌（公开接口没有登录身份） */
    private ServerWebExchange withInternalToken(ServerWebExchange exchange) {
        return withInternalToken(exchange, null, null);
    }

    private ServerWebExchange withInternalToken(ServerWebExchange exchange, String userId, String role) {
        var builder = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.remove(INTERNAL_TOKEN_HEADER);
                })
                .header(INTERNAL_TOKEN_HEADER, internalApiToken == null ? "" : internalApiToken);
        if (userId != null) {
            builder.header(USER_ID_HEADER, userId)
                    .header(USER_ROLE_HEADER, role == null ? "" : role);
        }
        return exchange.mutate().request(builder.build()).build();
    }

    private boolean isAdminPath(String path) {
        return ADMIN_PATH_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
