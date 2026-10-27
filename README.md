# hdgatewayservice

蓝区网关：**蓝区（互联网）应用统一通过本网关调用绿区 HIS 接口 / 华为云 APIG 接口**，隐藏绿区凭证、签名与路由细节。以 `hdkitservice` 为蓝本构建（JDK 21 + Spring Boot 3.3，完整对齐其基础设施）。

## 架构

```
蓝区互联网应用（一个调用方一个 API Key）
      │  POST /invoke  头: X-Api-Key  体: {route, path, method, query, headers, body}
      ▼
hdgatewayservice (Spring Boot)
      │  InvokeController ──► InvokeService（API Key 鉴权 → 路由白名单查表 → 方法白名单 → path 校验 → 透传头清洗）
      │  ├─ HisTransport        ← 4 头 apigw（X-APIG-APPCODE/X-HW-ID/X-HW-APPKEY/X-auth-token）+ IAM token 获取/缓存
      │  ├─ AppCodeTransport    ← X-Apig-AppCode 简单认证（华为云 APIG）
      │  ├─ PassThroughTransport ← 直连（预置路由自带凭证）
      │  └─ Signer              ← SDK-HMAC-SHA256（预留 HMAC 类路由扩展）
      ▼
绿区 HIS 接口 / APIG 接口
```

安全边界：**网关持有全部绿区凭证，蓝区调用方不接触**；调用方只能访问路由白名单内的绿区上游（防 SSRF/越权），并支持 **key 级路由授权**（每个 API Key 可限制可调用的路由）。

## 技术栈

- JDK 21 + Spring Boot 3.3 + Maven（与蓝本一致）
- Spring Web（RestClient）+ JPA/MySQL（API Key）+ Redis（限流/登录锁定）
- log4j2 文件日志（interface/call/run 三路）+ 敏感信息脱敏（Masker）

## 对外接口

统一路径前缀 `/rest/developer/server/hdgatewayservice/`，仅 `GET`、`POST`。

### 1. 核心 invoke `POST /invoke`

```bash
curl -X POST http://<host>:<port>/rest/developer/server/hdgatewayservice/invoke \
  -H 'Content-Type: application/json' -H 'X-Api-Key: <API Key>' \
  -d '{
    "route": "his-demo",
    "path": "/v1/check",
    "method": "POST",
    "query": "k=v",
    "headers": {"X-Biz-Trace": "trace-1"},
    "body": {"customer_id": "<domainId>"}
  }'
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `route` | 是 | 路由白名单名称（见 `GET /routes`） |
| `path` | 是 | 拼接在上游 URL 后，必须以 `/` 开头；禁止 scheme、路径穿越、控制字符 |
| `method` | 否 | 默认 `POST`，须在路由 methods 白名单内 |
| `query` | 否 | 原始 query string（调用方自行 URL 编码），如 `"k=v&x=%20"` |
| `headers` | 否 | 业务透传头；凭证/主机/逐跳头按黑名单丢弃（host、authorization、cookie、x-apig*、x-hw*、x-auth*、x-api-key、x-traceid 等） |
| `body` | 否 | 任意 JSON，按 JSON 透传给上游 |

响应：**上游状态码 + 响应体透传**（HIS 业务错误如 `error_code` 原样透传），响应头携带 `x-traceID`。

```json
{ "error_code": "0000", "error_msg": "success", "result": { "issued_tag": 0 } }
```

### 2. 路由白名单 `GET /routes`

只暴露 `name`/`auth`/`methods`/`configured`，不暴露上游 URL 与任何凭证。

```json
[
  { "name": "his-demo", "auth": "HIS", "methods": ["GET","POST"], "configured": true },
  { "name": "apig-demo", "auth": "APPCODE", "methods": ["GET","POST"], "configured": true }
]
```

`auth` 取值：

| auth | 上游认证方式 | 凭证来源 |
|------|--------------|----------|
| `HIS` | 4 头 apigw（X-APIG-APPCODE / X-HW-ID / X-HW-APPKEY / X-auth-token），production 下 token 经 IAM 动态获取并缓存（提前 10min 刷新），test 下直接用静态 token | 网关 `GW_HIS_*` 环境变量 |
| `APPCODE` | `X-Apig-AppCode` 简单认证（华为云 APIG） | 路由级 `app-code`，回退全局 `GW_APPCODE` |
| `NONE` | 直连（预置路由自带凭证） | — |

路由在 `application.yml` 的 `gateway.routes` 注册（name 固定、url 用环境变量注入实际绿区地址），新增路由 = 加一条配置。

### 3. 管理面（admin JWT 保护）

- `POST /auth/login`：`{"username":"admin","password":"..."}` → `{token, username, expiresInHours}`（Redis 登录失败锁定，多 pod 共享计数）
- `POST /api-keys?source=<调用方名>&routes=<逗号分隔路由名>`：**一个调用方一个 API Key**；同 source 重复创建即轮换（key 替换、旧 key 失效），响应带 `rotated` 标记
  - `routes` 可选：**key 级路由授权**（如 `routes=his-demo,direct`），该 key 只能 invoke 白名单内的路由，未配置 = 不限
- `GET /api-keys`：列表
- `DELETE /api-keys/{id}`：禁用

### 错误响应

固定字段 `code` / `message` / `traceId`（`traceId` 驼峰）：

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 400 | `HDGW_INVALID_REQUEST` | 参数缺失/非法（path 校验失败等） |
| 401 | `HDGW_UNAUTHORIZED` | X-Api-Key 缺失/无效/已禁用，或管理面 JWT 无效 |
| 404 | `HDGW_ROUTE_NOT_FOUND` | 路由未在白名单注册 |
| 403 | `HDGW_ROUTE_FORBIDDEN` | 该 API Key 未授权访问此路由（key 级路由授权） |
| 405 | `HDGW_METHOD_NOT_ALLOWED` | method 不在路由 methods 白名单 |
| 429 | `HDGW_RATE_LIMITED` | 限流（Redis Token Bucket，多 pod 共享，fail-open） |
| 502 | `HDGW_UPSTREAM_ERROR` / `HDGW_ROUTE_NOT_CONFIGURED` | 上游故障（附真实原因）/ 路由 URL 未配置 |
| 504 | `HDGW_TIMEOUT` | 上游调用超时 |

## 快速开始

### 构建与测试

```bash
mvn test       # 56 个单元测试
mvn package    # 打包 target/hdgatewayservice-0.0.1.jar
```

### 本地运行

```bash
export MYSQL_HOST=127.0.0.1 MYSQL_DATABASE=hdgatewayservice MYSQL_USER=root MYSQL_PASSWORD=<pwd>
export REDIS_HOST=127.0.0.1 DEPLOY_ENV=test
export GW_HIS_APPCODE=<绿区 AppCode> GW_HIS_HW_ID=<HW-ID> GW_HIS_APPKEY=<APPKEY> GW_HIS_AUTH_TOKEN=<token>
export GW_ROUTE_HIS_DEMO_URL=https://<绿区 HIS 基址>
export GW_ROUTE_APIG_DEMO_URL=https://<APIG 基址>

java -jar target/hdgatewayservice-0.0.1.jar   # 端口 3003
```

### 部署

见 [deploy/PROD-DEPLOY.md](deploy/PROD-DEPLOY.md)（dockerfile + k8s 清单齐全，prod `backend` / test `huaweicloud-agent` 命名空间）。

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PORT` | `3003` | 服务端口 |
| `MYSQL_*` | 本机 | API Key 存储 |
| `REDIS_*` | 本机 | 限流/登录锁定 |
| `GW_HIS_APPCODE` / `GW_HIS_HW_ID` / `GW_HIS_APPKEY` | 空 | 绿区 apigw 4 头凭证 |
| `GW_HIS_AUTH_TOKEN` | 空 | test 环境 HIS 静态 token |
| `GW_HIS_IAM_ENDPOINT/USERNAME/PASSWORD/DOMAIN_NAME` | 空 | production 下 IAM 动态取 token |
| `GW_APPCODE` | 空 | APPCODE 路由全局兜底 AppCode |
| `GW_ROUTE_<NAME>_URL` | 空 | 路由上游基址（环境变量注入） |
| `GW_ROUTE_<NAME>_APP_CODE` | 空 | 路由级 AppCode |
| `DEPLOY_ENV` | `production` | `test` 时 HIS 走静态 token |
| `GW_CONNECT_TIMEOUT_MS` / `GW_READ_TIMEOUT_MS` | `10000` / `30000` | 上游超时 |
| `GW_CALLER_HEADER_DENY` | 见 yml | 调用方透传头黑名单（精确 + 前缀） |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `JWT_SECRET` | 见 yml | 管理面 |

## 与蓝本（hdkitservice）的对应关系

| 组件 | 来源 |
|------|------|
| 绿区 4 头传输 + IAM token 缓存 | 移植自蓝本 `ApigwGateway`（扩展为按 method/path 调用） |
| SDK-HMAC-SHA256 签名 | 移植自蓝本 `sign/Signer` |
| API Key 模型/拦截器 | 对齐蓝本 `ApiKey`/`ApiKeyInterceptor`（增加 source 唯一 + 轮换） |
| 错误模型/全局异常 | 对齐蓝本（`HDGW_*` 错误码族） |
| 限流/脱敏/日志/CI | 完整复用蓝本实现 |

## 项目结构

```
src/main/java/com/huaweicloud/hdgatewayservice/
├── controller/        # invoke / api-keys / auth / 全局异常
├── service/           # InvokeService 编排、JWT、登录锁定
├── transport/         # 绿区传输层（HIS 4 头 / APIG AppCode / 直连）
├── sign/              # SDK-HMAC-SHA256（预留）
├── config/            # 配置、路由白名单注册表、拦截器
├── ratelimit/         # Redis Token Bucket
├── repository/        # API Key 存储
├── model/             # DTO
└── util/              # 脱敏
```

## License

Apache License 2.0（见蓝本仓库 LICENSE，本仓库待补充同名文件）
