package com.huaweicloud.hdgatewayservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败锁定（Redis 实现，多 pod 共享计数）：按用户名与来源 IP 分别计数，
 * 15 分钟窗口内失败达到阈值即锁定，成功登录清零。Redis 异常时 fail-open（放行并告警）。
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final long WINDOW_SECONDS = 15 * 60;

    private final int maxUsernameFailures;
    private final int maxIpFailures;
    private final long lockSeconds;

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${hdgateway.auth.login-max-username-failures:5}") int maxUsernameFailures,
                               @Value("${hdgateway.auth.login-max-ip-failures:30}") int maxIpFailures,
                               @Value("${hdgateway.auth.login-lock-minutes:15}") long lockMinutes) {
        this.redis = redis;
        this.maxUsernameFailures = maxUsernameFailures;
        this.maxIpFailures = maxIpFailures;
        this.lockSeconds = lockMinutes * 60;
    }

    /** 返回 true 表示处于锁定期，应直接拒绝登录尝试 */
    public boolean isLocked(String username, String ip) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(lockKey("u", username)))
                    || Boolean.TRUE.equals(redis.hasKey(lockKey("ip", ip)));
        } catch (Exception e) {
            log.warn("[login-lock] redis failed, fail-open: {}", e.getMessage());
            return false;
        }
    }

    public void recordFailure(String username, String ip) {
        try {
            bump(failKey("u", username), maxUsernameFailures, lockKey("u", username));
            bump(failKey("ip", ip), maxIpFailures, lockKey("ip", ip));
        } catch (Exception e) {
            log.warn("[login-lock] redis failed, skip counting: {}", e.getMessage());
        }
    }

    public void recordSuccess(String username, String ip) {
        try {
            redis.delete(failKey("u", username));
            redis.delete(failKey("ip", ip));
        } catch (Exception e) {
            log.warn("[login-lock] redis failed, skip reset: {}", e.getMessage());
        }
    }

    private void bump(String failKey, int max, String lockKey) {
        Long count = redis.opsForValue().increment(failKey);
        if (count == null) return;
        if (count == 1) {
            redis.expire(failKey, Duration.ofSeconds(WINDOW_SECONDS));
        }
        if (count >= max) {
            redis.opsForValue().set(lockKey, "1", Duration.ofSeconds(lockSeconds));
        }
    }

    private String lockKey(String scope, String value) {
        return "login:lock:" + scope + ":" + (value == null ? "-" : value.toLowerCase());
    }

    private String failKey(String scope, String value) {
        return "login:fail:" + scope + ":" + (value == null ? "-" : value.toLowerCase());
    }
}
