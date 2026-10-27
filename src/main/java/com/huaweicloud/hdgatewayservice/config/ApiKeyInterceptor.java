package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.model.ApiKey;
import com.huaweicloud.hdgatewayservice.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 调用方鉴权：一个调用方一个 API Key（X-Api-Key），配置文件直配（hdgateway.static-api-keys）优先，回退数据库校验；
 * 通过后注入 source 与 allowedRoutes 供限流/key 级路由授权使用 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final ApiKeyRepository apiKeyRepository;
    private final StaticApiKeysProperties staticApiKeys;

    public ApiKeyInterceptor(ApiKeyRepository apiKeyRepository, StaticApiKeysProperties staticApiKeys) {
        this.apiKeyRepository = apiKeyRepository;
        this.staticApiKeys = staticApiKeys;
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
            return unauthorized(response, "Missing X-Api-Key header");
        }

        for (StaticApiKeysProperties.StaticKey k : staticApiKeys.getStaticApiKeys()) {
            if (k.key() != null && k.key().equals(apiKey)) {
                request.setAttribute("source", k.name());
                request.setAttribute("allowedRoutes", k.allowedRouteSet());
                return true;
            }
        }

        ApiKey key = apiKeyRepository.findByApiKeyAndEnabledTrue(apiKey).orElse(null);
        if (key == null) {
            return unauthorized(response, "Invalid or disabled API key");
        }

        request.setAttribute("source", key.getSource());
        request.setAttribute("allowedRoutes", key.allowedRouteSet());
        return true;
    }

    private boolean unauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"HDGW_UNAUTHORIZED\",\"error\":\"" + message + "\"}");
        return false;
    }
}
