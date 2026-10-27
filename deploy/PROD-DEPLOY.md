# hdgatewayservice（蓝区网关）部署指南

目标集群：`cce`（prod 命名空间 `backend`；test 命名空间 `huaweicloud-agent`，清单在 `k8s/test/`）

## 定位

蓝区（互联网）应用通过本网关调用绿区 HIS 接口 / APIG 接口。网关持有全部绿区凭证（apigw 4 头 + IAM token / APIG AppCode），蓝区调用方只持自己的 API Key。

## 前置条件

- 已登录 SWR（`hcloud SWR CreateSecret --cli-region=cn-south-1` 可获取临时登录凭证）
- 具备集群 kubectl 访问权限（CCE 控制台下载 kubeconfig）
- 镜像已推送：`swr.cn-south-1.myhuaweicloud.com/huaweicloud-devkit-prod/hdgatewayservice:<tag>`
  （构建：`docker build -f Dockerfile -t <SWR 地址>:<tag> . && docker push ...`）

## 步骤 1：更新镜像拉取凭证

SWR 临时凭证约 24h 过期，部署前务必刷新：

```bash
AUTH=$(hcloud SWR CreateSecret --cli-region=cn-south-1 \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["auths"]["swr.cn-south-1.myhuaweicloud.com"]["auth"])')
U=$(echo $AUTH | base64 -d | cut -d: -f1)
P=$(echo $AUTH | base64 -d | cut -d: -f2)
kubectl create secret docker-registry swr-secret -n backend \
  --docker-server=swr.cn-south-1.myhuaweicloud.com \
  --docker-username="$U" --docker-password="$P" \
  --dry-run=client -o yaml | kubectl apply -f -
```

## 步骤 2：创建/更新应用配置 Secret

将 `<值>` 替换为实际值（`MYSQL_*`/`REDIS_*`/`GW_HIS_*`/路由 URL 必配；`DEPLOY_ENV=production` 时 HIS token 走 IAM 动态获取，`test` 时直接用 `GW_HIS_AUTH_TOKEN`）：

```bash
kubectl create secret generic hdgw-secrets -n backend \
  --from-literal=MYSQL_HOST=<MySQL 地址> \
  --from-literal=MYSQL_PORT=3306 \
  --from-literal=MYSQL_USER=<MySQL 用户> \
  --from-literal=MYSQL_PASSWORD=<MySQL 密码> \
  --from-literal=MYSQL_DATABASE=hdgatewayservice \
  --from-literal=REDIS_HOST=<Redis 地址> \
  --from-literal=REDIS_PORT=6379 \
  --from-literal=DEPLOY_ENV=production \
  --from-literal=GW_HIS_APPCODE=<绿区 apigw AppCode> \
  --from-literal=GW_HIS_HW_ID=<绿区 HW-ID> \
  --from-literal=GW_HIS_APPKEY=<绿区 APPKEY> \
  --from-literal=GW_HIS_IAM_ENDPOINT=https://iam.cn-north-4.myhuaweicloud.com/v3/auth/tokens \
  --from-literal=GW_HIS_IAM_USERNAME=<IAM 用户名> \
  --from-literal=GW_HIS_IAM_PASSWORD=<IAM 密码> \
  --from-literal=GW_HIS_IAM_DOMAIN_NAME=<IAM 域名> \
  --from-literal=GW_ROUTE_HIS_DEMO_URL=<绿区 HIS 上游基址> \
  --from-literal=GW_ROUTE_APIG_DEMO_URL=<华为云 APIG 上游基址> \
  --from-literal=GW_ROUTE_APIG_DEMO_APPCODE=<APIG AppCode> \
  --from-literal=ADMIN_USERNAME=admin \
  --from-literal=ADMIN_PASSWORD=<管理密码> \
  --from-literal=JWT_SECRET=<随机串> \
  --dry-run=client -o yaml | kubectl apply -f -
```

## 步骤 3：部署应用

> 日志持久卷：SFS 按用量计费，首次创建后会持续产生费用。仅在首次部署时执行 PVC 创建。

```bash
kubectl apply -f k8s/pvc.yaml        # 创建日志持久卷（仅首次）
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

test 环境（命名空间 `huaweicloud-agent`）：

```bash
kubectl apply -f k8s/test/pvc.yaml
kubectl apply -f k8s/test/deployment.yaml
kubectl apply -f k8s/test/service.yaml
```

## 步骤 4：验证

```bash
kubectl get pods -n backend -l app=hdgatewayservice
kubectl exec -n backend -l app=hdgatewayservice -- tail -n 50 /opt/cloud/logs/hdgatewayservice/run.log
```

> 本服务日志写入文件（不写控制台），因此用 `kubectl exec ... tail` 查看日志文件，`kubectl logs` 看不到业务日志。

预期日志关键行（`run.log`）：

```
Started HdGatewayApplication           # 应用启动完成
[route] registered N routes: [...]     # 路由白名单加载完成
```

接口验证：

```bash
kubectl port-forward -n backend svc/hdgatewayservice-svc 3003:3003 &

# 1) 管理面登录换 JWT
curl -s -X POST http://127.0.0.1:3003/rest/developer/server/hdgatewayservice/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<管理密码>"}'

# 2) 给调用方签发 API Key（一个调用方一个）
curl -s -X POST "http://127.0.0.1:3003/rest/developer/server/hdgatewayservice/api-keys?source=my-internet-app" \
  -H 'Authorization: Bearer <JWT>'

# 3) 蓝区调用方经网关 invoke 绿区接口
curl -s -X POST http://127.0.0.1:3003/rest/developer/server/hdgatewayservice/invoke \
  -H 'Content-Type: application/json' -H 'X-Api-Key: <API Key>' \
  -d '{"route":"his-demo","path":"/v1/check","method":"POST","body":{"customer_id":"<domainId>"}}'
```

## 故障排查

| 现象 | 处理 |
|------|------|
| ErrImagePull / 401 | swr-secret 过期，重跑步骤 1 |
| Pending (Insufficient cpu) | 清理遗留 Pod 释放资源 |
| MySQL 连接失败 | 确认 MYSQL_* 值正确、RDS 安全组放行 3306 |
| invoke 返回 `HDGW_ROUTE_NOT_CONFIGURED` | 对应路由 URL 未注入（hdgw-secrets 缺键），重跑步骤 2 |
| invoke 返回 `HDGW_UPSTREAM_ERROR: 获取 IAM Token 失败` | 检查 `GW_HIS_IAM_*` 四项配置与 IAM 账号有效性 |
| invoke 返回 `HDGW_UNAUTHORIZED` | API Key 不存在/已禁用/已轮换，重新签发或核对 |
