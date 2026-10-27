package com.huaweicloud.hdgatewayservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "test-jwt-secret-key-0123456789abcdef");
        ReflectionTestUtils.setField(jwtService, "expirationHours", 24L);
    }

    @Test
    void generatedTokenValidates() {
        String token = jwtService.generateToken("admin");
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void garbageTokenRejected() {
        assertFalse(jwtService.validateToken("not-a-token"));
        assertFalse(jwtService.validateToken(""));
    }

    @Test
    void tokenSignedWithDifferentSecretRejected() {
        String token = jwtService.generateToken("admin");
        JwtService other = new JwtService();
        ReflectionTestUtils.setField(other, "secret", "another-jwt-secret-key-0123456789abcd");
        ReflectionTestUtils.setField(other, "expirationHours", 24L);
        assertFalse(other.validateToken(token));
    }
}
