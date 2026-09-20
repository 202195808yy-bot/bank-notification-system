package com.bank.customer.security;

import com.bank.common.constant.AppConstants;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final long validityInMilliseconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiration:" + AppConstants.JWT_EXPIRATION_MS + "}") long expiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.validityInMilliseconds = expiration;
    }

    /**
     * 创建 JWT Token
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return JWT 字符串
     */
    public String createToken(Long userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMilliseconds);
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(
                Jwts.parserBuilder().setSigningKey(key).build()
                        .parseClaimsJws(token).getBody().getSubject()
        );
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}