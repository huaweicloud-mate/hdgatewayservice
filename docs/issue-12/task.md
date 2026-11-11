# Tasks: Issue #12 — hdaccountservice-same-identity 路由

| # | 任务 | 产出 | 状态 |
|---|---|---|---|
| 1 | 需求/方案文档 | `docs/issue-12/spec.md`、`plan.md` | done |
| 2 | 红：配置绑定失败测试（断言路由存在、auth=NONE、methods=POST、url 空默认占位符） | `SameIdentityRouteConfigTest` | done |
| 3 | 绿：`application.yml` 追加路由 | 路由定义 4 行 | done |
| 4 | 部署注入：prod/test k8s 清单 + PROD-DEPLOY secret 键清单 | `k8s/deployment.yaml`、`k8s/test/deployment.yaml`、`deploy/PROD-DEPLOY.md` | done |
| 5 | 文档同步：README 路由表 | `README.md` | done |
| 6 | 全量回归 `mvn test` | 测试全绿 | done |
| 7 | 自审查（5 轴 + pre-pr 增量检查）→ 建 PR → Issue 评论 → A2A QA | PR | 进行中 |

## 非代码交付（运维/部署侧）

- `kubectl create secret` 时补 `GW_ROUTE_HDACCOUNTSERVICE_SAME_IDENTITY_URL=http://hdaccountservice:8080/rest/developer/hdaccountservice/v1/same-identity-by-userid`（或 `kubectl patch secret`），改完 `kubectl rollout restart`。
- `api_key.allowed_routes` 非空的调用方，追加 `hdaccountservice-same-identity`，否则该 key 调用返回 403 `HDGW_ROUTE_FORBIDDEN`。