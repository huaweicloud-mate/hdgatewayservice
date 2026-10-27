package com.huaweicloud.hdgatewayservice.transport;

import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 华为云 APIG 简单认证传输层：X-Apig-AppCode 头。
 * app-code 路由级优先，未配置时回退全局兜底，均缺失则报未配置。
 */
@Component
public class AppCodeTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(AppCodeTransport.class);

    private final GatewayConfig config;
    private final RestClient restClient;

    public AppCodeTransport(GatewayConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public RawResponse exchange(GatewayRoutesProperties.RouteDef route, String method, String path,
                                String query, Map<String, String> callerHeaders, String body) {
        String appCode = appCodeOf(route);
        if (appCode == null || appCode.isEmpty()) {
            throw new IllegalStateException("路由 app-code 未配置");
        }
        String base = route.url().replaceFirst("/$", "");
        String url = base + path + (query == null || query.isEmpty() ? "" : "?" + query);
        try {
            var spec = restClient.method(HttpMethod.valueOf(method))
                    .uri(url)
                    .header("X-Apig-AppCode", appCode);
            if (callerHeaders != null) {
                for (Map.Entry<String, String> e : callerHeaders.entrySet()) {
                    if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                        spec = spec.header(e.getKey(), e.getValue());
                    }
                }
            }
            if (body != null && !body.isEmpty()) {
                spec = spec.body(body);
            }
            ResponseEntity<String> resp = spec.exchange((request, response) -> new ResponseEntity<>(
                    response.bodyTo(String.class), response.getHeaders(), response.getStatusCode()));
            return new RawResponse(resp.getStatusCode().value(), resp.getBody());
        } catch (Transport.UpstreamException e) {
            throw e;
        } catch (Exception e) {
            log.error("[apig] {} {} failed: {}", method, url, e.getMessage());
            boolean timeout = e instanceof org.springframework.web.client.ResourceAccessException
                    && e.getCause() instanceof java.net.SocketTimeoutException;
            throw new UpstreamException(e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName(), e, timeout);
        }
    }

    public String appCodeOf(GatewayRoutesProperties.RouteDef route) {
        if (route.appCode() != null && !route.appCode().isBlank()) {
            return route.appCode().trim();
        }
        return config.globalAppCode();
    }
}
