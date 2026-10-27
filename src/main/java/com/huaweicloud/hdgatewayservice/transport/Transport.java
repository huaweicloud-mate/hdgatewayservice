package com.huaweicloud.hdgatewayservice.transport;

import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;

import java.util.Map;

/** 绿区上游传输层公共接口：按路由认证方式选择实现 */
public interface Transport {

    /**
     * 执行绿区上游调用并透传响应。
     *
     * @param route         路由定义（url/appCode）
     * @param method        HTTP 方法（已大写）
     * @param path          请求路径（以 / 开头）
     * @param query         原始 query string（可为空）
     * @param callerHeaders 已清洗的调用方透传头
     * @param body          请求体 JSON 字符串（GET 等无体请求为空串）
     * @return 上游状态码 + 响应体（上游业务错误也透传）
     * @throws UpstreamException 上游不可达/超时/非 JSON 故障
     */
    RawResponse exchange(GatewayRoutesProperties.RouteDef route, String method, String path,
                         String query, Map<String, String> callerHeaders, String body);

    record RawResponse(int status, String body) {}

    class UpstreamException extends RuntimeException {
        private final boolean timeout;

        public UpstreamException(String message, Throwable cause, boolean timeout) {
            super(message, cause);
            this.timeout = timeout;
        }

        public boolean isTimeout() { return timeout; }
    }
}
