package com.bank.customer.security;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtTokenProviderTest {

    /** 33 字节解码后 ≥256 位，满足 HS256；仅测试用，不与任何环境配置关联 */
    private static final String SECRET = "bXktc2VjcmV0LWtleS1mb3ItYmFuay1ub3RpZmljYXRpb24=";
    private static final String OTHER_SECRET = "c29tZS1vdGhlci1zZWNyZXQta2V5LWZvci1iYW5rLTEyMzQ1Njc4";

    @Test
    public void shouldCreateAndValidateToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000L);
        String token = provider.createToken(1L, "USER");
        assertTrue(provider.validateToken(token));
        assertEquals(1L, provider.getUserId(token));
    }

    @Test
    public void shouldRejectExpiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1_000L);
        assertFalse(provider.validateToken(provider.createToken(1L, "USER")));
    }

    @Test
    public void shouldRejectTokenSignedWithAnotherKey() {
        String token = new JwtTokenProvider(SECRET, 60_000L).createToken(1L, "ADMIN");
        assertFalse(new JwtTokenProvider(OTHER_SECRET, 60_000L).validateToken(token));
    }
}
