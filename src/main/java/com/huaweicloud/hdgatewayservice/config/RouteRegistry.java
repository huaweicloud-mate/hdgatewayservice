package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.model.RouteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 路由白名单注册表：route 名（小写）→ 上游定义。
 * 蓝区调用方只能通过已注册的 route 名访问绿区上游，防 SSRF/越权。
 */
@Component
public class RouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(RouteRegistry.class);

    public static final String AUTH_HIS = "HIS";
    public static final String AUTH_APPCODE = "APPCODE";
    public static final String AUTH_NONE = "NONE";

    private final Map<String, GatewayRoutesProperties.RouteDef> routes = new LinkedHashMap<>();

    public RouteRegistry(GatewayRoutesProperties properties) {
        for (GatewayRoutesProperties.RouteDef def : properties.getRoutes()) {
            if (def.name() == null || def.name().isBlank()) {
                continue;
            }
            String key = def.name().trim().toLowerCase(Locale.ROOT);
            if (routes.containsKey(key)) {
                log.warn("[route] duplicate route name ignored: {}", key);
                continue;
            }
            routes.put(key, def);
        }
        log.info("[route] registered {} routes: {}", routes.size(), routes.keySet());
    }

    /** 未注册返回 null */
    public GatewayRoutesProperties.RouteDef find(String name) {
        if (name == null || name.isBlank()) return null;
        return routes.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** 对外路由列表：只暴露 name/auth/methods，不暴露上游 URL */
    public List<RouteInfo> list() {
        List<RouteInfo> out = new ArrayList<>();
        for (GatewayRoutesProperties.RouteDef def : routes.values()) {
            out.add(new RouteInfo(def.name(), authOf(def), methodsOf(def), configured(def)));
        }
        return out;
    }

    public static String authOf(GatewayRoutesProperties.RouteDef def) {
        String auth = def.auth() == null ? AUTH_NONE : def.auth().trim().toUpperCase(Locale.ROOT);
        return switch (auth) {
            case AUTH_HIS, AUTH_APPCODE, AUTH_NONE -> auth;
            default -> AUTH_NONE;
        };
    }

    public static List<String> methodsOf(GatewayRoutesProperties.RouteDef def) {
        List<String> out = new ArrayList<>();
        if (def.methods() != null) {
            for (String m : def.methods()) {
                if (m != null && !m.isBlank()) out.add(m.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    public static boolean configured(GatewayRoutesProperties.RouteDef def) {
        return def.url() != null && !def.url().isBlank();
    }
}
