package com.huaweicloud.hdgatewayservice.transport;

import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 直连传输层（auth=NONE）：不加任何认证头，供内网/白名单自带凭证的预置路由使用 */
@Component
public class PassThroughTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(PassThroughTransport.class);

    private final RestClient restClient;

    public PassThroughTransport(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RawResponse exchange(GatewayRoutesProperties.RouteDef route, String method, String path,
                                String query, Map<String, String> callerHeaders, String body) {
        String base = route.url().replaceFirst("/$", "");
        String url = base + path + (query == null || query.isEmpty() ? "" : "?" + query);
        try {
            var spec = restClient.method(HttpMethod.valueOf(method)).uri(url);
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
            log.error("[passthrough] {} {} failed: {}", method, url, e.getMessage());
            boolean timeout = e instanceof org.springframework.web.client.ResourceAccessException
                    && e.getCause() instanceof java.net.SocketTimeoutException;
            throw new UpstreamException(e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName(), e, timeout);
        }
    }
}
