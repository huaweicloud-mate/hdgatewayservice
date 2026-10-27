package com.huaweicloud.hdgatewayservice.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * 绿区 HIS 传输层（自蓝本 ApigwGateway 移植扩展）：4 头 POST/GET
 * （X-APIG-APPCODE / X-HW-ID / X-HW-APPKEY / X-auth-token）。
 * IAM token 由共用 IamTokenProvider 提供；网关持有全部绿区凭证，蓝区调用方不接触。
 */
@Component
public class HisTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(HisTransport.class);

    private final GatewayConfig config;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final IamTokenProvider iamTokenProvider;

    public HisTransport(GatewayConfig config, ObjectMapper mapper, RestClient restClient,
                        IamTokenProvider iamTokenProvider) {
        this.config = config;
        this.mapper = mapper;
        this.restClient = restClient;
        this.iamTokenProvider = iamTokenProvider;
    }

    @Override
    public RawResponse exchange(GatewayRoutesProperties.RouteDef route, String method, String path,
                                String query, Map<String, String> callerHeaders, String body) {
        String url = buildUrl(route, path, query);
        try {
            String token = iamTokenProvider.getAuthToken();
            var spec = restClient.method(HttpMethod.valueOf(method))
                    .uri(url)
                    .header("X-APIG-APPCODE", config.hisAppCode())
                    .header("X-HW-ID", config.hisHwId())
                    .header("X-HW-APPKEY", config.hisAppKey())
                    .header("X-auth-token", token);
            spec = applyCallerHeaders(spec, callerHeaders);
            if (body != null && !body.isEmpty()) {
                spec = spec.body(body);
            }
            ResponseEntity<String> resp = spec.exchange((request, response) -> new ResponseEntity<>(
                    response.bodyTo(String.class), response.getHeaders(), response.getStatusCode()));
            return toRawResponse(resp.getStatusCode().value(), resp.getBody());
        } catch (UpstreamException e) {
            throw e;
        } catch (Exception e) {
            throw wrap(method, url, e);
        }
    }

    protected String buildUrl(GatewayRoutesProperties.RouteDef route, String path, String query) {
        String base = route.url().replaceFirst("/$", "");
        return base + path + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    protected RestClient.RequestBodySpec applyCallerHeaders(RestClient.RequestBodySpec spec,
                                                            Map<String, String> callerHeaders) {
        if (callerHeaders == null) {
            return spec;
        }
        for (Map.Entry<String, String> e : callerHeaders.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                spec = spec.header(e.getKey(), e.getValue());
            }
        }
        return spec;
    }

    protected RawResponse toRawResponse(int status, String respBody) {
        if (status >= 400) {
            JsonNode node = null;
            try {
                node = mapper.readTree(respBody);
            } catch (Exception ignore) {
                // 响应体非 JSON，按真正的服务故障处理
            }
            if (node == null || !node.has("error_code")
                    || node.path("error_code").asText().isEmpty()) {
                throw new UpstreamException("HTTP " + status
                        + (respBody != null && !respBody.isEmpty() ? ": " + respBody : ""), null, false);
            }
        }
        return new RawResponse(status, respBody);
    }

    protected UpstreamException wrap(String method, String url, Exception e) {
        if (e instanceof ResourceAccessException
                && e.getCause() instanceof SocketTimeoutException) {
            log.error("[his] {} {} timeout: {}", method, url, e.getMessage());
            return new UpstreamException("上游调用超时", e, true);
        }
        log.error("[his] {} {} failed: {}", method, url, e.getMessage());
        return new UpstreamException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e, false);
    }
}
