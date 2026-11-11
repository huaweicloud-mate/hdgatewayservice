package com.huaweicloud.hdgatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * application.yml 路由配置契约测试：以配置绑定 seam（{@link GatewayRoutesProperties}）直接校验
 * hdaccountservice-same-identity 路由，防止 auth 误配为 APIG（会注入过期绿区 token → 401 APIGW.0307）、
 * methods 被放宽、上游地址被硬编码等回归。
 */
class SameIdentityRouteConfigTest {

    private static final String ROUTE_NAME = "hdaccountservice-same-identity";
    private static final String URL_ENV_KEY = "GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL";

    private GatewayRoutesProperties routesFromApplicationYml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        Iterable<ConfigurationPropertySource> propertySources = ConfigurationPropertySources.from(sources);
        return new Binder(propertySources)
                .bind("gateway", Bindable.of(GatewayRoutesProperties.class))
                .orElseGet(GatewayRoutesProperties::new);
    }

    private GatewayRoutesProperties.RouteDef sameIdentityRoute() throws IOException {
        Optional<GatewayRoutesProperties.RouteDef> def = routesFromApplicationYml().getRoutes().stream()
                .filter(r -> ROUTE_NAME.equals(r.name()))
                .findFirst();
        assertNotNull(def.orElse(null), "application.yml gateway.routes 缺少路由 " + ROUTE_NAME);
        return def.get();
    }

    @Test
    void registeredWithNoneAuth() throws IOException {
        // 上游是集群内蓝区服务：auth 必须 NONE，配成 APIG 会注入过期绿区 token → 401 APIGW.0307
        assertEquals(RouteRegistry.AUTH_NONE, RouteRegistry.authOf(sameIdentityRoute()));
    }

    @Test
    void methodsBoundAsPostOnly() throws IOException {
        // YAML 标量 methods 按单元素列表绑定，语义为「仅 POST」
        assertEquals(List.of("POST"), RouteRegistry.methodsOf(sameIdentityRoute()));
    }

    @Test
    void urlInjectedFromEnvVarWithEmptyDefault() throws IOException {
        // 集群内上游地址由部署环境注入，不允许把内部地址写进仓库配置
        assertEquals("${" + URL_ENV_KEY + ":}", sameIdentityRoute().url());
    }
}