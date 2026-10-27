package com.huaweicloud.hdgatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 路由白名单配置（application.yml gateway.routes），url 支持环境变量覆盖 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayRoutesProperties {

    private List<RouteDef> routes = new ArrayList<>();

    public List<RouteDef> getRoutes() { return routes; }
    public void setRoutes(List<RouteDef> routes) { this.routes = routes == null ? new ArrayList<>() : routes; }

    public record RouteDef(String name, String url, String auth, List<String> methods, String appCode) {
    }
}
