package com.huaweicloud.hdgatewayservice.transport;

import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 绿区 APIG 传输层：仅 X-auth-token 头（IAM 动态 token，与 HIS 共用同一套 IAM 凭证与缓存）。
 * 适用于 developer.myhuaweicloud.com 等只校验 x-auth-token 的绿区接口。
 */
@Component
public class ApigTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(ApigTransport.class);

    private final RestClient restClient;
    private final IamTokenProvider iamTokenProvider;

    public ApigTransport(RestClient restClient, IamTokenProvider iamTokenProvider) {
        this.restClient = restClient;
        this.iamTokenProvider = iamTokenProvider;
    }

    @Override
    public RawResponse exchange(GatewayRoutesProperties.RouteDef route, String method, String path,
                                String query, Map<String, String> callerHeaders, String body) {
        String base = route.url().replaceFirst("/$", "");
        String url = base + path + (query == null || query.isEmpty() ? "" : "?" + query);
        try {
            String token = iamTokenProvider.getAuthToken();
            var spec = restClient.method(HttpMethod.valueOf(method))
                    .uri(url)
                    .header("x-auth-token", token);
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
}
