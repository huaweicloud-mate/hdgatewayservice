# Plan: Issue #12 — hdaccountservice-same-identity 路由

## 方案

纯配置变更，复用现有路由注册链路，零 Java 改动：

```
application.yml gateway.routes
  → GatewayRoutesProperties（@ConfigurationProperties("gateway") 绑定 List<RouteDef>）
  → RouteRegistry（name 小写索引 + auth/methods 归一化，未配置 url 时 configured=false）
  → InvokeService（按 route 名查表 → method 白名单 → URL 校验 → auth 选择 Transport）
  → PassThroughTransport（auth=NONE 直连上游）
```

`auth: NONE` 时 `InvokeService` 选择 `PassThroughTransport`，不透传任何绿区凭证，因此不会出现 APIG token 过期导致的 `401 APIGW.0307`。

## 改动清单

| 文件 | 改动 | 说明 |
|---|---|---|
| `src/main/resources/application.yml` | +5 行 | `gateway.routes` 末尾追加 `hdaccountservice-same-identity`（`application.yml:63` 注释行 1 行 + 路由定义 4 行） |
| `k8s/deployment.yaml` | +6 行 | prod：`GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL` ← secret `hdgw-secrets`（`optional: true`） |
| `k8s/test/deployment.yaml` | +6 行 | test：同上 |
| `deploy/PROD-DEPLOY.md` | +1 行 | secret 键清单补 `GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL` |
| `README.md` | +1 行 | 路由白名单表补一行（`NONE` 认证） |
| `src/test/java/.../config/SameIdentityRouteConfigTest.java` | 新增 | 配置绑定回归测试 |

## 测试策略（TDD）

- 先写失败测试：加载 `classpath:application.yml` → `YamlPropertySourceLoader` → `Binder` 绑定 `gateway.routes` → 断言存在 `hdaccountservice-same-identity`，`authOf=NONE`，`methodsOf=[POST]`，`url` 为 `${GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL:}`（空默认值，环境注入）。
- 再补实现（application.yml 一行路由）使测试转绿。
- 全量回归：`mvn test`（现有 56 个单测 + 新增）。

## 风险与处置

| 风险 | 处置 |
|---|---|
| `auth` 误配为 `APIG` → 401 APIGW.0307 | 单测硬断言 `auth=NONE`，评审可拦截 |
| `methods` 被放宽为 `GET,POST` | 单测断言 `methodsOf=[POST]` |
| 内部集群地址硬编码进仓库 | `application.yml` 与文档均只写占位符（`url` 空默认值 + `<集群内 hdaccountservice 上游地址>`），真实值只留在 secret/部署环境 |
| 环境变量未注入 → `HDGW_ROUTE_NOT_CONFIGURED` | k8s 清单已注入键；`deploy/PROD-DEPLOY.md` 记录 secret 键 |
| 调用方 `allowed_routes` 非空缺本路由 → 403 | 部署说明与 Issue 评论提示运维追加（数据侧，不在代码内） |

## 交付后验证（需 SRE/部署环境）

```bash
curl -s -X POST http://<host>/rest/developer/server/hdgatewayservice/invoke \
  -H "X-Api-Key: <调用方 API Key>" \
  -H "Content-Type: application/json" \
  -d '{"route":"hdaccountservice-same-identity","body":{...}}'
```

期望 200 且响应体来自 `hdaccountservice`；`GET /routes` 应能看到该路由 `auth=NONE`。