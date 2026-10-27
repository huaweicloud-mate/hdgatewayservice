package com.huaweicloud.hdgatewayservice.model;

/** 对外路由信息：不暴露上游 URL 与任何凭证 */
public record RouteInfo(String name, String auth, java.util.List<String> methods, boolean configured) {
}
