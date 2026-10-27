package com.huaweicloud.hdgatewayservice.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Token Bucket：Lua 原子执行「按流逝时间补充 → 扣减 → 判定」。
 * 使用 Redis 服务器时间（redis.call('TIME')）计算补充量，多 pod 时钟偏移不影响正确性。
 * 桶空闲自动过期（容量/速率 × 2），无 key 泄漏。
 */
@Component
public class RedisRateLimiter {

    private static final String LUA = """
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            local b = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(b[1]) or tonumber(ARGV[1])
            local ts = tonumber(b[2]) or now
            local delta = math.max(0, now - ts)
            tokens = math.min(tonumber(ARGV[1]), tokens + math.floor(delta / 1000 * tonumber(ARGV[2])))
            local allowed = 1
            if tokens < 1 then allowed = 0 else tokens = tokens - 1 end
            redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', KEYS[1], math.max(60000, math.ceil(tonumber(ARGV[1]) / tonumber(ARGV[2]) * 2000)))
            return allowed
            """;

    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** true=放行（已扣减一个令牌）；false=被限流 */
    public boolean tryAcquire(String bucketKey, int capacity, int refillPerSecond) {
        Long allowed = redis.execute(SCRIPT, List.of(bucketKey),
                String.valueOf(capacity), String.valueOf(refillPerSecond));
        return allowed != null && allowed == 1;
    }
}
