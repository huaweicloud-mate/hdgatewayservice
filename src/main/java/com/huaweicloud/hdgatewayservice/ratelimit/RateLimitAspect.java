package com.huaweicloud.hdgatewayservice.ratelimit;

import com.huaweicloud.hdgatewayservice.service.InvokeService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 限流切面：方法级注解优先，类级注解兜底；Redis 异常时放行（fail-open）并记错误日志 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimitKeyResolver keyResolver;
    private final RedisRateLimiter limiter;

    public RateLimitAspect(RateLimitKeyResolver keyResolver, RedisRateLimiter limiter) {
        this.keyResolver = keyResolver;
        this.limiter = limiter;
    }

    @Around("@annotation(com.huaweicloud.hdgatewayservice.ratelimit.RateLimit) || @within(com.huaweicloud.hdgatewayservice.ratelimit.RateLimit)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RateLimit rule = resolveRule(pjp);
        if (rule == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = currentRequest();
        String key = request != null
                ? keyResolver.resolve(rule.key(), request)
                : "unknown";

        boolean allowed;
        try {
            allowed = limiter.tryAcquire("rl:" + rule.name() + ":" + key, rule.capacity(), rule.refillPerSecond());
        } catch (Exception e) {
            log.error("[rate-limit] redis failed, fail-open for {}: {}", rule.name(), e.getMessage());
            allowed = true;
        }

        if (!allowed) {
            throw new InvokeService.InvokeException("HDGW_RATE_LIMITED", "Too many requests", null);
        }
        return pjp.proceed();
    }

    private RateLimit resolveRule(ProceedingJoinPoint pjp) {
        var method = pjp.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature sig
                ? sig.getMethod() : null;
        if (method != null) {
            RateLimit m = method.getAnnotation(RateLimit.class);
            if (m != null) return m;
        }
        return pjp.getTarget().getClass().getAnnotation(RateLimit.class);
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
