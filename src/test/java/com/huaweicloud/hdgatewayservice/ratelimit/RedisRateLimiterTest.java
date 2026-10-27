package com.huaweicloud.hdgatewayservice.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisRateLimiter(redis);
    }

    @Test
    void allowedWhenScriptReturnsOne() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("rl:invoke:key", 200, 100));
    }

    @Test
    void deniedWhenScriptReturnsZero() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(0L);
        assertFalse(limiter.tryAcquire("rl:invoke:key", 200, 100));
    }

    @Test
    void deniedWhenScriptReturnsNull() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(null);
        assertFalse(limiter.tryAcquire("rl:invoke:key", 200, 100));
    }
}
