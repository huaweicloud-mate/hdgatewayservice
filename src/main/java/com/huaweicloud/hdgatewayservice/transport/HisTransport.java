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
 * （X-APIG-APPCODE / X-HW-ID / X-HW-APPKEY / X-auth-token）与 IAM token 的获取/缓存。
 * 网关持有全部绿区凭证，蓝区调用方不接触。
 */
@Component
public class HisTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(HisTransport.class);

    private final GatewayConfig config;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    private String cachedIamToken;
    private long cachedIamTokenExpiry;

    public HisTransport(GatewayConfig config, ObjectMapper mapper, RestClient restClient) {
        this.config = config;
        this.mapper = mapper;
        this.restClient = restClient;
    }

    @Override
    public RawResponse exchange(GatewayRoutesProperties.RouteDef route, String method, String path,
                                String query, Map<String, String> callerHeaders, String body) {
        String url = buildUrl(route, path, query);
        try {
            String token = getAuthToken();
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

    public String getAuthToken() {
        if ("test".equalsIgnoreCase(config.deployEnv())) {
            return config.hisAuthToken();
        }
        if (cachedIamToken != null && System.currentTimeMillis() < cachedIamTokenExpiry - 600000) {
            return cachedIamToken;
        }
        return fetchIamToken();
    }

    private String fetchIamToken() {
        String endpoint = config.hisIamEndpoint();
        log.info("[his] fetching IAM token from {}", endpoint);
        try {
            String body = buildJson(ob -> {
                var auth = ob.putObject("auth");
                var identity = auth.putObject("identity");
                identity.putArray("methods").add("password");
                var password = identity.putObject("password");
                var user = password.putObject("user");
                var domain = user.putObject("domain");
                domain.put("name", config.hisIamDomainName());
                user.put("name", config.hisIamUsername());
                user.put("password", config.hisIamPassword());
            });

            var respEntity = restClient.method(HttpMethod.POST)
                    .uri(endpoint)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            String token = respEntity.getHeaders().getFirst("X-Subject-Token");
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("IAM response missing X-Subject-Token header");
            }

            String respBody = respEntity.getBody();
            if (respBody != null && !respBody.isEmpty()) {
                JsonNode root = mapper.readTree(respBody);
                if (root.has("token") && root.get("token").has("expires_at")) {
                    String expiresAt = root.get("token").get("expires_at").asText();
                    cachedIamTokenExpiry = java.time.Instant.parse(expiresAt).toEpochMilli();
                } else {
                    cachedIamTokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000L;
                }
            } else {
                cachedIamTokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000L;
            }
            cachedIamToken = token;
            log.info("[his] IAM token acquired, expires at {}",
                    java.time.Instant.ofEpochMilli(cachedIamTokenExpiry));
            return token;
        } catch (Exception e) {
            throw new UpstreamException("获取 IAM Token 失败: " + e.getMessage(), e, false);
        }
    }

    public String buildJson(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> fn) {
        var node = mapper.createObjectNode();
        fn.accept(node);
        return node.toString();
    }
}
