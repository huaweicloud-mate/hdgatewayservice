# Spec: Issue #12 — 新增 hdaccountservice-same-identity 路由（auth=NONE / POST）

> 目标基线：`main`（`$GRAPE_WORK_BRANCH` 未注入，取仓库默认分支；开发分支 `feat/issue-12`）
> 来源：Issue #12 正文 + Issue Agent Spec（评论 6790198771）

## 目标

在 `src/main/resources/application.yml` 的 `gateway.routes` 注册一条固定路由 `hdaccountservice-same-identity`，使蓝区应用可用 `POST` 经网关转发到集群内蓝区 `hdaccountservice:8080` 的 `same-identity-by-userid` 接口。

## 范围

### In

- `application.yml`：`gateway.routes` 末尾（`hdgrowservice-get-user-list` 之后）追加本路由，`url` 由 `${GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL:}` 注入。
- 部署侧：`k8s/deployment.yaml`、`k8s/test/deployment.yaml` 从 secret `hdgw-secrets` 注入该环境变量；`deploy/PROD-DEPLOY.md` 的 secret 键清单补一条。
- 文档：`README.md` 路由白名单表补一行。

### Out

- 不改 `RouteRegistry` / `GatewayRoutesProperties` / `ApiKeyInterceptor` / `InvokeService` 等 Java 代码（现有机制已支持任意 route 名 + `auth: NONE`）。
- 不改已有 8 条路由的 `url` / `auth` / `methods`。
- 不新增 API Key 管理接口（`api_key.allowed_routes` 由运维直接改表）。

## 关键约束（防误配）

| 项 | 值 | 理由 |
|---|---|---|
| `auth` | `NONE` | 上游是集群内蓝区服务，无需绿区凭证；配 `APIG` 会注入过期绿区 token，导致 401 `APIGW.0307` |
| `methods` | `POST` | 与接口契约一致；`RouteDef.methods` 为 `List<String>`，YAML 标量按单元素列表绑定（与现有 8 条一致） |
| `url` 默认值 | 空串 | 集群内地址按环境注入，禁止把内部地址硬编码进仓库配置 |
| `api_key.allowed_routes` | 非空时追加本路由名 | 否则该 key 调用 403 `HDGW_ROUTE_FORBIDDEN` |

## 验收标准

- [ ] `gateway.routes` 存在 `hdaccountservice-same-identity`，`auth=NONE`，`methods` 仅 `POST`。
- [ ] 注入 `GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL` 后 `POST` 经网关 200 转发（需 SRE 部署环境验证）。
- [ ] 无 401 `APIGW.0307`。
- [ ] API Key 调用无 403 `HDGW_ROUTE_FORBIDDEN`（`allowed_routes` 非空时已含本路由）。
- [ ] `mvn test` 全绿。

## 测试边界（seam）

复用现有配置绑定 seam：`GatewayRoutesProperties`（`@ConfigurationProperties("gateway")`）。新增单测直接加载 `application.yml` 并绑定为 `GatewayRoutesProperties`，断言该路由的 `name` / `auth` / `methods` / `url` 占位符契约——不启动 Spring 上下文、不依赖真实上游。