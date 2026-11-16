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
 * hdaccountservice-same-identity 路由，防止 methods 被放宽、上游地址被硬编码等回归。
 * 上游为绿区实名服务（hddprm.cn-north-4），auth 必须 APIG（x-auth-token 由网关 IAM 动态获取）。
 */
class SameIdentityRouteConfigTest {

    private static final String ROUTE_NAME = "hdaccountservice-same-identity";
    private static final String URL_ENV_KEY = "GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL";
    private static final String DEFAULT_URL =
            "https://hddprm.cn-north-4.myhuaweicloud.com/v1/hdaccountservice/realname/same-identity-by-userid";

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
    void registeredWithApigAuth() throws IOException {
        // 上游为绿区实名服务（hddprm.cn-north-4）：auth 必须 APIG（x-auth-token 由网关 IAM 动态获取）
        assertEquals(RouteRegistry.AUTH_APIG, RouteRegistry.authOf(sameIdentityRoute()));
    }

    @Test
    void methodsBoundAsPostOnly() throws IOException {
        // YAML 标量 methods 按单元素列表绑定，语义为「仅 POST」
        assertEquals(List.of("POST"), RouteRegistry.methodsOf(sameIdentityRoute()));
    }

    @Test
    void urlInjectedFromEnvVarWithApiGDefault() throws IOException {
        // 上游为绿区实名服务地址，默认值内置仓库配置；部署环境可用同名环境变量覆盖
        assertEquals("${" + URL_ENV_KEY + ":" + DEFAULT_URL + "}", sameIdentityRoute().url());
    }
}