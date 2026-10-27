package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.model.ApiKey;
import com.huaweicloud.hdgatewayservice.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 调用方鉴权：一个调用方一个 API Key（X-Api-Key，DB 校验），通过后注入 source 供限流/审计使用 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyInterceptor(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"HDGW_UNAUTHORIZED\",\"error\":\"Missing X-Api-Key header\"}");
            return false;
        }

        ApiKey key = apiKeyRepository.findByApiKeyAndEnabledTrue(apiKey).orElse(null);
        if (key == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"HDGW_UNAUTHORIZED\",\"error\":\"Invalid or disabled API key\"}");
            return false;
        }

        request.setAttribute("source", key.getSource());
        request.setAttribute("allowedRoutes", key.allowedRouteSet());
        return true;
    }
}
