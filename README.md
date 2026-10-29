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
- Spring Web（RestClient）+ JPA/MySQL（API Key，运维直接建表配置）+ Redis（限流）
- log4j2 文件日志（interface/call/run 三路）+ 敏感信息脱敏（Masker）

## 对外接口

统一路径前缀 `/rest/developer/server/hdgatewayservice/`，仅 `GET`、`POST`。

### 1. 核心 invoke `POST /invoke`

固定路由（URL 即完整绿区接口，如激励金查券）时 `path` 省略：

```bash
curl -X POST http://<host>:<port>/rest/developer/server/hdgatewayservice/invoke \
  -H 'Content-Type: application/json' -H 'X-Api-Key: <API Key>' \
  -d '{
    "route": "coupon-check",
    "body": {"customer_id": "<domainId>"}
  }'
```

通用透传路由（route.url 为上游基址）时带 `path`：

```bash
curl -X POST http://<host>:<port>/rest/developer/server/hdgatewayservice/invoke \
  -H 'Content-Type: application/json' -H 'X-Api-Key: <API Key>' \
  -d '{
    "route": "his-open",
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
| `path` | 否 | 拼接在路由 URL 后，必须以 `/` 开头；禁止 scheme、路径穿越、控制字符；**固定路由（URL 即完整接口）省略** |
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
  { "name": "coupon-check", "auth": "HIS", "methods": ["POST"], "configured": true },
  { "name": "codearts-package", "auth": "NONE", "methods": ["GET"], "configured": true }
]
```

`auth` 取值：

| auth | 上游认证方式 | 凭证来源 |
|------|--------------|----------|
| `HIS` | 4 头 apigw（X-APIG-APPCODE / X-HW-ID / X-HW-APPKEY / X-auth-token），production 下 token 经 IAM 动态获取并缓存（提前 10min 刷新），test 下直接用静态 token | 网关 `GW_HIS_*` 环境变量 |
| `APIG` | 仅 `x-auth-token` 头（IAM 动态 token，与 HIS 共用同一套 IAM 凭证与缓存），适用于 developer.myhuaweicloud.com 等只校验 x-auth-token 的绿区接口 | 同 `GW_HIS_IAM_*` |
| `APPCODE` | `X-Apig-AppCode` 简单认证（华为云 APIG） | 路由级 `app-code`，回退全局 `GW_APPCODE` |
| `NONE` | 直连（预置路由自带凭证） | — |

路由在 `application.yml` 的 `gateway.routes` 注册（name 固定、url 用环境变量注入实际绿区地址），新增路由 = 加一条配置。**首版已预置 hdkitservice 正在调用的 5 个绿区接口**：

| 网关路由 | 对应 hdkitservice 调用 | 认证 | method |
|----------|------------------------|------|--------|
| `coupon-check` | 激励金查券 `INCENTIVE_CHECK_URL` | HIS（4头+IAM） | POST |
| `coupon-issue` | 激励金发券 `INCENTIVE_ISSUE_URL`（v2） | HIS（4头+IAM） | POST |
| `competition-signup-query` | 竞赛报名状态查询 | HIS（4头+IAM） | POST |
| `agreement-query` | 协议查询 `agreementservice/developer/agreement/query` | APIG（x-auth-token） | GET,POST |
| `agreement-sign` | 协议签署 `agreementservice/developer/agreement/sign` | APIG（x-auth-token） | POST |
| `realname-developer-info` | 实名信息 `hdaccountservice/realname/get-developer-info` | APIG（x-auth-token） | GET,POST |

> 码道（codearts）/果办（officeace）校验接口**不纳入网关**：需用调用方本人 AK/SK 做 SDK-HMAC-SHA256 签名且 URL 含动态路径，与网关凭证模型冲突，保留在 hdkitservice。

> 这 5 条均为**固定路由**：url 注入完整接口地址，invoke 时 `path` 省略。后续接入新绿区接口 = 加一条路由配置。

### 3. 协议场景接口（X-Api-Key 鉴权）

蓝本式编排：查询 → 过滤未签/旧版（`sign_status != 1`）→ 签署最新版。

**`POST /agreement/query`** —— 查询协议签署状态（封装 `query_sign_info_list` 组装），透传上游 `sign_info_list`：

```json
{ "developerId": "2d7f01301d1a4138893fd10bbbe54aab", "agrTypes": [90103], "country": "cn" }
```

**`POST /agreement/sign`** —— 签署协议（幂等：已签最新版自动跳过，`signedCount=0`）：

```json
{ "developerId": "...", "agrTypes": [90103], "country": "cn", "language": "zh_cn" }
```

```json
{ "signed": true, "signedCount": 0,
  "results": [{ "agrType": 90103, "signStatus": 1, "latestVersion": 2025112029, "signed": true }] }
```

`country` 默认 `cn`，`language` 默认 `zh_cn`。

### 4. API Key 配置（数据库直配，无管理接口）

本服务**不提供 API Key 管理接口**，由运维直接在 MySQL `api_key` 表配置（JPA `ddl-auto: update` 自动建表，也可手工建）：

```sql
-- 一个调用方一条记录；allowed_routes 逗号分隔（小写），空串 = 不限（可调所有已注册路由）
INSERT INTO api_key (api_key, source, allowed_routes, enabled, created_at)
VALUES ('<32位随机串>', '<调用方标识>', 'his-demo,direct', 1, NOW());

-- 轮换：更新 api_key 值；禁用：enabled=0
UPDATE api_key SET api_key = '<新随机串>' WHERE source = '<调用方标识>';
UPDATE api_key SET enabled = 0 WHERE source = '<调用方标识>';
```

校验规则（`ApiKeyInterceptor`）：`X-Api-Key` 头必须匹配 `enabled=1` 的记录，通过后注入 `source` 与 `allowed_routes` 供限流/key 级路由授权使用。

### 错误响应

固定字段 `code` / `message` / `traceId`（`traceId` 驼峰）：

| HTTP | 业务码 | 说明 |
|------|--------|------|
| 400 | `HDGW_INVALID_REQUEST` | 参数缺失/非法（path 校验失败等） |
| 401 | `HDGW_UNAUTHORIZED` | X-Api-Key 缺失/无效/已禁用（数据库 `api_key` 表无匹配） |
| 404 | `HDGW_ROUTE_NOT_FOUND` | 路由未在白名单注册 |
| 403 | `HDGW_ROUTE_FORBIDDEN` | 该 API Key 未授权访问此路由（key 级路由授权） |
| 405 | `HDGW_METHOD_NOT_ALLOWED` | method 不在路由 methods 白名单 |
| 429 | `HDGW_RATE_LIMITED` | 限流（Redis Token Bucket，多 pod 共享，fail-open） |
| 502 | `HDGW_UPSTREAM_ERROR` / `HDGW_ROUTE_NOT_CONFIGURED` | 上游故障（附真实原因）/ 路由 URL 未配置 |
| 504 | `HDGW_TIMEOUT` | 上游调用超时 |

## 快速开始

### 构建与测试

```bash
mvn test       # 53 个单元测试
mvn package    # 打包 target/hdgatewayservice-0.0.1.jar
```

### 本地运行

```bash
export MYSQL_HOST=127.0.0.1 MYSQL_DATABASE=hdgatewayservice MYSQL_USER=root MYSQL_PASSWORD=<pwd>
export REDIS_HOST=127.0.0.1 DEPLOY_ENV=test
export GW_HIS_APPCODE=<绿区 AppCode> GW_HIS_HW_ID=<HW-ID> GW_HIS_APPKEY=<APPKEY> GW_HIS_AUTH_TOKEN=<token>
export GW_ROUTE_COUPON_CHECK_URL=https://<绿区激励金查券完整地址>
export GW_ROUTE_COUPON_ISSUE_URL=https://<绿区激励金发券完整地址>
export GW_ROUTE_COMPETITION_SIGNUP_URL=https://<绿区竞赛报名完整地址>
export GW_ROUTE_CODEARTS_PACKAGE_URL=https://<码道套餐查询完整地址>
export GW_ROUTE_OFFICEACE_SUBSCRIPTION_URL=https://<果办订阅查询完整地址>

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

## 与蓝本（hdkitservice）的对应关系

| 组件 | 来源 |
|------|------|
| 绿区 4 头传输 + IAM token 缓存 | 移植自蓝本 `ApigwGateway`（扩展为按 method/path 调用） |
| SDK-HMAC-SHA256 签名 | 移植自蓝本 `sign/Signer` |
| API Key 模型/拦截器 | 对齐蓝本 `ApiKey`/`ApiKeyInterceptor`（增加 source 唯一 + allowed_routes 授权，管理改为数据库直配） |
| 错误模型/全局异常 | 对齐蓝本（`HDGW_*` 错误码族） |
| 限流/脱敏/日志/CI | 完整复用蓝本实现 |

## 项目结构

```
src/main/java/com/huaweicloud/hdgatewayservice/
├── controller/        # invoke / routes / 全局异常
├── service/           # InvokeService 编排
├── transport/         # 绿区传输层（HIS 4 头 / APIG AppCode / 直连）
├── sign/              # SDK-HMAC-SHA256（预留）
├── config/            # 配置、路由白名单注册表、ApiKey 拦截器
├── ratelimit/         # Redis Token Bucket
├── repository/        # API Key 存储（只读校验）
├── model/             # DTO
└── util/              # 脱敏
```

## License

Apache License 2.0（见蓝本仓库 LICENSE，本仓库待补充同名文件）
