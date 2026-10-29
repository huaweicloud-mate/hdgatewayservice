# AGENTS.md — hdgatewayservice

蓝区网关：互联网应用（蓝区）通过本服务调用绿区 HIS / APIG 接口。网关持有全部绿区凭证，调用方只持自己的 API Key。以 `hdkitservice` 为蓝本（JDK 21 + Spring Boot 3.3 + Maven）。

## 常用命令

```bash
mvn test                # 56 个单元测试（必须全绿才能提交）
mvn package             # 打包 target/hdgatewayservice-0.0.1.jar（Spring Boot fat jar）
mvn package -DskipTests # 跳过测试快速打包
```

本地运行（H2 内存库，免装 MySQL/Redis；Redis 缺失时限流 fail-open）：

```bash
# PowerShell
$env:SPRING_PROFILES_ACTIVE='local'   # application-local.yml：H2 + 配置文件直配 API Key
mvn spring-boot:run                    # 或 java -jar target/hdgatewayservice-0.0.1.jar（端口 3003）
```

部署环境验证（实网真实调用）：

```bash
curl.exe -s -X POST http://<host>/rest/developer/server/hdgatewayservice/invoke \
  -H "X-Api-Key: <key>" -H "Content-Type: application/json" \
  -d '{"route":"hdincentiveservice-coupon-check","body":{"customer_id":"<32位domainId>","scene_type":40}}'
```

## 架构

```
蓝区调用方（X-Api-Key）
  → ApiKeyInterceptor（鉴权 + allowedRoutes 注入）
  → InvokeController / AgreementController
  → InvokeService（路由查表 → key 级授权 → 方法白名单 → path 校验 → 透传头清洗）
      ├─ HisTransport      auth=HIS     4 头（X-APIG-APPCODE/X-HW-ID/X-HW-APPKEY/X-auth-token）
      ├─ ApigTransport     auth=APIG    仅 x-auth-token
      ├─ AppCodeTransport  auth=APPCODE X-Apig-AppCode
      └─ PassThroughTransport auth=NONE 直连
  → 绿区上游（响应状态码 + 响应体透传）
```

```
src/main/java/com/huaweicloud/hdgatewayservice/
├── controller/   # invoke / routes / agreement(query+sign) / 全局异常
├── service/      # InvokeService 编排、AgreementService 场景编排
├── transport/    # 绿区传输层（按 auth 类型选择）+ IamTokenProvider（token 缓存共用）
├── sign/         # SDK-HMAC-SHA256（预留，码道/果办类需调用方签名的接口不用网关）
├── config/       # GatewayConfig、RouteRegistry（路由白名单）、拦截器
├── ratelimit/    # Redis Token Bucket（Lua 原子，fail-open）
├── repository/   # ApiKeyRepository（只读校验，无管理接口）
├── model/        # DTO（record + jakarta validation）
└── util/         # Masker 日志脱敏
```

## 关键设计（改代码前必读）

- **路由白名单**：`application.yml` 的 `gateway.routes`，路由名 = `{上游服务名}-{接口动作}`（如 `hdincentiveservice-coupon-check`）。路由结构编译期固化（防 SSRF），仅 URL 可用环境变量 `GW_ROUTE_{大写路由名}_URL` 覆盖。改路由名/加路由必须重新构建镜像。
- **API Key**：一个调用方一个 key。来源两处：MySQL `api_key` 表（生产，运维直配，无管理接口）和 `hdgateway.static-api-keys`（local profile）。`allowed_routes` 逗号分隔（空=不限），key 级路由授权，越权返回 403 `HDGW_ROUTE_FORBIDDEN`。
- **安全红线**：绿区凭证只放环境变量/secret，**绝不进仓库、不进 yml 字面值、不进日志**；调用方透传头按 `hdgateway.caller-header-deny` 黑名单丢弃（防凭证注入）；path 禁止 scheme/路径穿越/控制字符；日志经 Masker 脱敏（PII 键：id_card_number/realname/contact_phone 等已入默认脱敏列表）。
- **错误模型**：`code`/`message`/`traceId` 三字段，错误码前缀 `HDGW_*`，HTTP 映射集中在 `GlobalExceptionHandler.STATUS_MAP`。新增业务码必须同时登记映射。
- **配置方式**：`@Value("${ENV_VAR:default}")` 只认 yml 里的 `${ENV_VAR:default}` 占位符链——**不要**指望 `@Value("${hdgateway.xxx}")` 直接读环境变量（无 relaxed binding）。profile yml 里的字面量会覆盖 application.yml 的占位符链，profile 覆盖项也必须写成 `${ENV:default}` 形式。

## 已踩过的坑

1. **改了 yml 必须重新 `mvn package`**——运行旧 jar 会表现为"改动不生效"；若 repackage 被中断（java 进程占用 jar），会得到无主清单的普通 jar（启动报"没有主清单属性"），先停 java 进程再打包。
2. **不要用 SimpleClientHttpRequestFactory**——它对 4xx/5xx 抛 IOException 吞掉上游错误体；已换 `JdkClientHttpRequestFactory`（`RestClientConfig`），上游错误体才能透传给调用方。
3. **log4j2 属性不能与 sys 属性同名**——`LOG_HOME` 自引用会死循环，已改名 `GW_LOG_HOME`。
4. **Windows 下日志在 `C:\opt\cloud\logs\hdgatewayservice\`**（log4j2 默认 LOG_HOME），排查问题看 run.log（应用）/ interface.log（入站）/ call.log（出站，全脱敏）。
5. **调用方 AK/SK 签名类接口不进网关**（码道/果办/DevStation 协议签署）：需要调用方本人 AK/SK 做 SDK-HMAC-SHA256 签名，与"网关持凭证"模型冲突，留在 hdkitservice。

## 测试约定

- 单测用 Mockito 纯单元 + 一个 `@SpringBootTest`（`application-test.yml`：H2 + test 路由），**不依赖真实网络与真实凭证**。
- 改动传输层/编排层时，优先用 mock Transport 断言路由选择、头清洗、错误码，不打真实上游。
- 真实联调用本地 `local` profile + `DEPLOY_ENV` 环境变量切换 token 策略（production=IAM 动态，test=静态 `GW_HIS_AUTH_TOKEN`）。

## 部署

- 镜像：根目录 `Dockerfile`（多阶段 maven 构建）→ SWR。
- k8s：`k8s/`（prod，namespace `backend`）与 `k8s/test/`（namespace `huaweicloud-agent`），配置全部来自 secret `hdgw-secrets`（键清单见 `deploy/PROD-DEPLOY.md`）。
- 改 secret 后需 `kubectl rollout restart`；改代码/路由需重建镜像。
