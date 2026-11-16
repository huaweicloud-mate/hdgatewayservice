# Spec: Issue #17 — invoke 请求体 JSON 解析失败应回 400 而非 500 HDGW_INTERNAL

> 来源：Issue #17 开发者反馈（含日志证据与复现报文）；Developability: ready | 目标分支: main

## 目标

`POST /rest/developer/server/hdgatewayservice/invoke` 收到客户端非法请求体（JSON 语法错误，如键缺双引号的裸键报文）时，返回 **400 + `HDGW_INVALID_REQUEST`**，而不是当前的 **500 + `HDGW_INTERNAL`**。

## 现状（缺陷）

- `GlobalExceptionHandler` 的 `handleOther(Exception)` 兜底把未单独映射的异常统一转成 `500 / HDGW_INTERNAL / 服务内部错误`；
- Jackson 解析失败抛出的 `HttpMessageNotReadableException`（Spring MVC 标准异常）无专属 handler，落入该兜底 → 500；
- 日志证明失败发生在网关自身、未到上游（`AccessLogFilter` 记录 `dur=3ms`）：`JSON parse error: Unexpected character ('r' (code 114)): was expecting double-quote to start field name`；
- 后果：客户端报文错误被误归因为服务故障，2026-11-16 实际把排障引向域名入口 / 实例 / 路由配置。

## 期望行为

1. 新增 `@ExceptionHandler(HttpMessageNotReadableException.class)`：HTTP 400，错误码沿用既有 `HDGW_INVALID_REQUEST`（`STATUS_MAP` 已映射 400），与同文件既有 `handleMissingHeader` / `handleMissingParam` / `handleValidation` 保持"客户端入参错误 → 400"的一致性。
2. message 保留可读原因（`e.getMostSpecificCause().getMessage()`），traceId 正常返回。
3. 其它未识别异常仍由 `handleOther` 返回 500 `HDGW_INTERNAL`，兜底语义不变。
4. 排障文档补充：Windows PowerShell 下 `curl.exe` 会剥掉内嵌双引号，是裸键报文的常见来源，给出 `--data-binary @file` 或转义写法。

## 范围

### In Scope
- `controller/GlobalExceptionHandler.java`：新增 `HttpMessageNotReadableException` handler
- `controller/GlobalExceptionHandlerTest.java`：裸键报文 / 空 body / 非 JSON 文本 / 合法报文回归
- `deploy/PROD-DEPLOY.md` 故障排查：PowerShell 引号剥离说明

### Out of Scope
- 不新增 `HDGW_BAD_REQUEST` 错误码（沿用 `HDGW_INVALID_REQUEST`，避免码表膨胀）
- 不改动既有 `InvokeException` / 上游异常映射、路由、认证、传输层

## 验收标准

- [ ] 裸键报文 `{route:hdgrowservice-get-order-list,body:{page_no:1,page_size:2}}` → HTTP 400，`code=HDGW_INVALID_REQUEST`，message 含 Jackson 解析原因，traceId 正常返回
- [ ] 空 body / 非 JSON 文本 → HTTP 400 而非 500
- [ ] 合法报文（键带双引号）→ 仍为 200，行为不变
- [ ] `GlobalExceptionHandlerTest` 新增用例通过；`InvokeException`、上游异常等既有映射不受影响
- [ ] 排障文档补充 PowerShell `curl.exe` 引号剥离说明
- [ ] `mvn test` 全量通过