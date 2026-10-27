package com.huaweicloud.hdgatewayservice.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 限流主体解析：按 KeyType 从请求提取限流 key，取不到时回退 IP */
@Component
public class RateLimitKeyResolver {

    public String resolve(RateLimit.KeyType type, HttpServletRequest request) {
        return switch (type) {
            case CREDENTIAL -> firstNonBlank(
                    request.getHeader("X-Api-Key"),
                    clientIp(request));
            case IP -> clientIp(request);
        };
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
