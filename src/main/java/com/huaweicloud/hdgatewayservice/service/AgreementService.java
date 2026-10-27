package com.huaweicloud.hdgatewayservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import com.huaweicloud.hdgatewayservice.config.RouteRegistry;
import com.huaweicloud.hdgatewayservice.model.AgreementSignRequest;
import com.huaweicloud.hdgatewayservice.model.AgreementSignResponse;
import com.huaweicloud.hdgatewayservice.transport.ApigTransport;
import com.huaweicloud.hdgatewayservice.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 协议场景编排（蓝本式）：查询协议状态 → 过滤未签/旧版（sign_status != 1）→ 签署 → 返回结果。
 * 上游均为绿区 APIG（x-auth-token），网关持有 IAM 凭证。
 */
@Service
public class AgreementService {

    private static final Logger log = LoggerFactory.getLogger(AgreementService.class);

    public static final String ROUTE_QUERY = "agreement-query";
    public static final String ROUTE_SIGN = "agreement-sign";

    private final RouteRegistry registry;
    private final ApigTransport apigTransport;
    private final ObjectMapper mapper;

    public AgreementService(RouteRegistry registry, ApigTransport apigTransport, ObjectMapper mapper) {
        this.registry = registry;
        this.apigTransport = apigTransport;
        this.mapper = mapper;
    }

    /** 查询协议状态（封装 query_sign_info_list 组装），透传上游 sign_info_list */
    public JsonNode query(AgreementSignRequest req) {
        GatewayRoutesProperties.RouteDef route = requireRoute(ROUTE_QUERY);
        var root = mapper.createObjectNode();
        root.put("developer_id", req.developerId());
        var arr = root.putArray("query_sign_info_list");
        for (Integer agrType : req.agrTypes()) {
            var o = arr.addObject();
            o.put("agr_type", agrType);
            o.put("count", 1);
            o.put("country", req.country() == null ? "cn" : req.country());
        }
        Transport.RawResponse r = exchange(route, root.toString());
        log.info("[agreement] query developer_id={} agrTypes={} -> {}", mask(req.developerId()),
                req.agrTypes(), r.status());
        return readBody(r);
    }

    /** 签署：查询 → 过滤 sign_status != 1（未签/旧版）→ 签署最新版 → 返回结果 */
    public AgreementSignResponse sign(AgreementSignRequest req) {
        JsonNode queryResult = query(req);
        JsonNode items = queryResult.path("sign_info_list");

        var signRoot = mapper.createObjectNode();
        var signArr = signRoot.putArray("sign_info_list");
        List<AgreementSignResponse.Item> results = new ArrayList<>();
        for (JsonNode item : items) {
            int agrType = item.path("agr_type").asInt(-1);
            int signStatus = item.path("sign_status").asInt(-1);
            long latestVersion = item.path("latest_version").asLong(0);
            boolean needSign = signStatus != 1;
            if (needSign) {
                var o = signArr.addObject();
                o.put("agr_type", agrType);
                o.put("count", 1);
                o.put("country", req.country() == null ? "cn" : req.country());
                o.put("language", req.language() == null ? "zh_cn" : req.language());
                o.put("version", latestVersion);
            }
            results.add(new AgreementSignResponse.Item(agrType, signStatus, latestVersion, !needSign));
        }

        if (signArr.isEmpty()) {
            log.info("[agreement] sign developer_id={} all signed, nothing to do", mask(req.developerId()));
            return new AgreementSignResponse(true, 0, results);
        }

        GatewayRoutesProperties.RouteDef signRoute = requireRoute(ROUTE_SIGN);
        Transport.RawResponse r = exchange(signRoute, signRoot.toString());
        readBody(r);
        log.info("[agreement] sign developer_id={} signed {} agreements", mask(req.developerId()), signArr.size());
        return new AgreementSignResponse(true, signArr.size(), results);
    }

    private GatewayRoutesProperties.RouteDef requireRoute(String name) {
        GatewayRoutesProperties.RouteDef route = registry.find(name);
        if (route == null || !RouteRegistry.configured(route)) {
            throw new InvokeService.InvokeException("HDGW_ROUTE_NOT_CONFIGURED",
                    "路由不存在或上游地址未配置: " + name, null);
        }
        return route;
    }

    private Transport.RawResponse exchange(GatewayRoutesProperties.RouteDef route, String body) {
        return apigTransport.exchange(route, "POST", "", "", Map.of(), body);
    }

    private JsonNode readBody(Transport.RawResponse r) {
        if (r.status() >= 400) {
            throw new InvokeService.InvokeException("HDGW_UPSTREAM_ERROR",
                    "上游 " + r.status() + ": " + (r.body() == null ? "" : r.body()), null);
        }
        try {
            return mapper.readTree(r.body());
        } catch (Exception e) {
            throw new InvokeService.InvokeException("HDGW_UPSTREAM_ERROR", "上游响应非 JSON: " + r.body(), e);
        }
    }

    private String mask(String developerId) {
        if (developerId == null || developerId.length() <= 8) return "***";
        return developerId.substring(0, 4) + "***" + developerId.substring(developerId.length() - 4);
    }
}
