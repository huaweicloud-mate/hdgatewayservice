package com.huaweicloud.hdgatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 配置文件直配的调用方 API Key（local profile / 免 DB 场景），校验时优先于数据库 */
@ConfigurationProperties(prefix = "hdgateway")
public class StaticApiKeysProperties {

    private List<StaticKey> staticApiKeys = new ArrayList<>();

    public List<StaticKey> getStaticApiKeys() { return staticApiKeys; }
    public void setStaticApiKeys(List<StaticKey> staticApiKeys) {
        this.staticApiKeys = staticApiKeys == null ? new ArrayList<>() : staticApiKeys;
    }

    public record StaticKey(String name, String key, String routes) {

        /** 允许的路由名（小写）；空 = 不限 */
        public Set<String> allowedRouteSet() {
            if (routes == null || routes.isBlank()) return Set.of();
            return Arrays.stream(routes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
