package com.huaweicloud.hdgatewayservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * invoke 请求体：
 * route   必填，路由白名单名称
 * path    可选，拼接在路由 URL 后，须以 / 开头；固定路由（URL 即完整接口）可省略
 * method  可选，默认 POST，须在路由 methods 白名单内
 * query   可选，原始 query string（已编码），如 "k=v&amp;x=%20"
 * headers 可选，业务透传头（凭证/主机/逐跳头会被丢弃）
 * body    可选，任意 JSON，按 JSON 透传给上游
 */
public record InvokeRequest(
        @NotBlank(message = "route 必填") String route,
        String path,
        String method,
        String query,
        Map<String, String> headers,
        JsonNode body) {
}
