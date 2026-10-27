package com.huaweicloud.hdgatewayservice.service;

import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import com.huaweicloud.hdgatewayservice.config.RouteRegistry;
import com.huaweicloud.hdgatewayservice.model.InvokeRequest;
import com.huaweicloud.hdgatewayservice.model.RouteInfo;
import com.huaweicloud.hdgatewayservice.transport.ApigTransport;
import com.huaweicloud.hdgatewayservice.transport.AppCodeTransport;
import com.huaweicloud.hdgatewayservice.transport.HisTransport;
import com.huaweicloud.hdgatewayservice.transport.PassThroughTransport;
import com.huaweicloud.hdgatewayservice.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * invoke 编排：路由查表 → key 级路由授权 → 方法白名单 → path 校验 → 透传头清洗 → 绿区传输层 → 透传响应。
 * 网关持有全部绿区凭证，蓝区调用方只持自己的 API Key。
 */
@Service
public class InvokeService {

    private static final Logger log = LoggerFactory.getLogger(InvokeService.class);

    private final RouteRegistry registry;
    private final HisTransport hisTransport;
    private final ApigTransport apigTransport;
    private final AppCodeTransport appCodeTransport;
    private final PassThroughTransport passThroughTransport;
    private final GatewayConfig config;

    public InvokeService(RouteRegistry registry, HisTransport hisTransport,
                         ApigTransport apigTransport, AppCodeTransport appCodeTransport,
                         PassThroughTransport passThroughTransport, GatewayConfig config) {
        this.registry = registry;
        this.hisTransport = hisTransport;
        this.apigTransport = apigTransport;
        this.appCodeTransport = appCodeTransport;
        this.passThroughTransport = passThroughTransport;
        this.config = config;
    }

    public ResponseEntity<String> invoke(InvokeRequest req, Set<String> allowedRoutes) {
        GatewayRoutesProperties.RouteDef route = registry.find(req.route());
        if (route == null) {
            throw new InvokeException("HDGW_ROUTE_NOT_FOUND",
                    "路由不存在或未在白名单注册: " + req.route(), null);
        }
        if (allowedRoutes != null && !allowedRoutes.isEmpty()
                && !isRouteAuthorized(allowedRoutes, req.route())) {
            throw new InvokeException("HDGW_ROUTE_FORBIDDEN",
                    "该 API Key 未授权访问路由: " + req.route(), null);
        }
        if (route.url() == null || route.url().isBlank()) {
            throw new InvokeException("HDGW_ROUTE_NOT_CONFIGURED",
                    "路由上游地址未配置: " + req.route(), null);
        }

        String method = (req.method() == null || req.method().isBlank())
                ? "POST" : req.method().trim().toUpperCase(Locale.ROOT);
        List<String> allowed = RouteRegistry.methodsOf(route);
        if (!allowed.contains(method)) {
            throw new InvokeException("HDGW_METHOD_NOT_ALLOWED",
                    "方法 " + method + " 不在路由 " + req.route() + " 允许列表 " + allowed + " 内", null);
        }

        String path = (req.path() == null || req.path().isBlank()) ? "" : validatePath(req.path());
        String query = req.query() == null ? "" : req.query();
        Map<String, String> safeHeaders = sanitizeHeaders(req.headers());
        String body = req.body() == null ? "" : req.body().toString();

        Transport transport = transportOf(route);
        long start = System.currentTimeMillis();
        Transport.RawResponse r = transport.exchange(route, method, path, query, safeHeaders, body);
        log.info("[invoke] route={} {} {} -> {} dur={}ms", req.route(), method, path, r.status(),
                System.currentTimeMillis() - start);

        String traceId = MDC.get("traceID");
        return ResponseEntity.status(r.status())
                .header("x-traceID", traceId != null ? traceId : "")
                .body(r.body());
    }

    public List<RouteInfo> routes() {
        return registry.list();
    }

    /** key 级路由授权：集合项与目标路由均按小写归一后比对 */
    private boolean isRouteAuthorized(Set<String> allowedRoutes, String routeName) {
        String target = routeName.trim().toLowerCase(Locale.ROOT);
        for (String r : allowedRoutes) {
            if (r != null && r.trim().toLowerCase(Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    /** path 校验：必须以 / 开头；禁止 scheme 相对路径、路径穿越与控制字符 */
    private String validatePath(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new InvokeException("HDGW_INVALID_REQUEST", "path 必须以 / 开头", null);
        }
        if (path.startsWith("//") || path.contains("://")) {
            throw new InvokeException("HDGW_INVALID_REQUEST", "path 不允许包含 scheme", null);
        }
        if (path.contains("..")) {
            throw new InvokeException("HDGW_INVALID_REQUEST", "path 不允许包含路径穿越", null);
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new InvokeException("HDGW_INVALID_REQUEST", "path 不允许包含控制字符", null);
            }
        }
        return path;
    }

    /** 调用方透传头清洗：按黑名单（精确 + 前缀）丢弃凭证/主机/逐跳头，防凭证注入与头覆盖 */
    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String name = e.getKey() == null ? "" : e.getKey().trim();
            if (name.isEmpty() || e.getValue() == null) {
                continue;
            }
            if (isDenied(name.toLowerCase(Locale.ROOT))) {
                log.warn("[invoke] caller header denied and dropped: {}", name);
                continue;
            }
            out.put(name, e.getValue());
        }
        return out;
    }

    private boolean isDenied(String lowerName) {
        for (String deny : config.callerHeaderDenyList()) {
            if (lowerName.equals(deny) || lowerName.startsWith(deny)) {
                return true;
            }
        }
        return false;
    }

    private Transport transportOf(GatewayRoutesProperties.RouteDef route) {
        return switch (RouteRegistry.authOf(route)) {
            case RouteRegistry.AUTH_HIS -> hisTransport;
            case RouteRegistry.AUTH_APIG -> apigTransport;
            case RouteRegistry.AUTH_APPCODE -> appCodeTransport;
            default -> passThroughTransport;
        };
    }

    public static class InvokeException extends RuntimeException {
        private final String code;

        public InvokeException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() { return code; }
    }
}
