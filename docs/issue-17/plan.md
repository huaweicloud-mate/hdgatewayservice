# Plan: Issue #17 — 请求体 JSON 解析失败映射 400

## 方案

在全局 advice `GlobalExceptionHandler` 中新增 `HttpMessageNotReadableException` 专属 handler，与既有 3 个 400 handler 同模式；`handleOther` 兜底与 `STATUS_MAP` 不动。

```
HTTP 请求体 ──Jackson──▶ 语法错误/空 body/非 JSON
  → HttpMessageNotReadableException（Spring MVC 转换层，未到 Controller 方法体）
  → GlobalExceptionHandler.handleUnreadableBody
  → 400 { code: HDGW_INVALID_REQUEST, message: e.getMostSpecificCause().getMessage(), traceId }
```

未识别异常仍走 `handleOther` → 500 `HDGW_INTERNAL`，语义不变。

## 改动清单

| 文件 | 改动 | 说明 |
|---|---|---|
| `src/main/java/.../controller/GlobalExceptionHandler.java` | +10 行 | 新增 `@ExceptionHandler(HttpMessageNotReadableException.class)`，400 + `HDGW_INVALID_REQUEST` |
| `src/test/java/.../controller/GlobalExceptionHandlerTest.java` | +4 用例 | MockMvc standalone（真实 Jackson + 真实 advice）覆盖 400/200 行为 |
| `deploy/PROD-DEPLOY.md` | +2 行 + 小节 | 故障排查表补 400 归因；补充 PowerShell `curl.exe` 引号剥离说明 |
| `docs/issue-17/*.md` | 新增 | 需求/方案/任务文档 |

## 测试 seam

**选在 HTTP 层**：`MockMvcBuilders.standaloneSetup(new InvokeController(mockInvokeService))`
`.setControllerAdvice(new GlobalExceptionHandler())`。

理由：缺陷本质是"请求体 → 异常 → HTTP 状态/业务码"的端到端映射，只有走真实 Jackson 转换层才能复现裸键报文/空 body/非 JSON 文本三类输入；直接 new 异常对象单测无法证明 MVC 链路真的把异常交给新 handler。复用已有 seam（既有 `GlobalExceptionHandlerTest` 纯单测保留不动），不新增组件。

## TDD 切片

1. 红：4 个 MockMvc 用例（裸键 → 400、空 body → 400、非 JSON → 400、合法 → 200）先失败（当前 500 / 或解析错误未被映射）。
2. 绿：新增 handler 使三个 400 用例转绿，合法报文 200 保持。
3. 文档同步 + 全量 `mvn test` 回归。

## 风险与处置

| 风险 | 处置 |
|---|---|
| 新 handler 影响既有异常分发 | 仅新增专属 handler，`handleOther`/`STATUS_MAP`/`handleInvoke`/`handleUpstream` 一行不动；全量测试回归 |
| message 透传 Jackson 原文过长 | 按 Spring 默认截断语义输出可读原因，不额外加工；不含凭证 |
| advice 全局生效影响其它接口 | 请求体不可读统一 400 与 Spring 默认语义一致，属预期改进 |