package com.huaweicloud.hdgatewayservice.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 绿区 IAM token 获取/缓存（HIS 与 APIG 传输层共用）：production 下按
 * hdgateway.his.iam-* 配置动态获取并缓存（提前 10min 刷新）；test 下直接用静态 token。
 */
@Component
public class IamTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(IamTokenProvider.class);

    private final GatewayConfig config;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    private String cachedIamToken;
    private long cachedIamTokenExpiry;

    public IamTokenProvider(GatewayConfig config, ObjectMapper mapper, RestClient restClient) {
        this.config = config;
        this.mapper = mapper;
        this.restClient = restClient;
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
        log.info("[iam] fetching token from {}", endpoint);
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
            log.info("[iam] token acquired, expires at {}",
                    java.time.Instant.ofEpochMilli(cachedIamTokenExpiry));
            return token;
        } catch (Exception e) {
            throw new Transport.UpstreamException("获取 IAM Token 失败: " + e.getMessage(), e, false);
        }
    }

    public String buildJson(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> fn) {
        var node = mapper.createObjectNode();
        fn.accept(node);
        return node.toString();
    }
}
