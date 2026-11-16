# Tasks: Issue #17 — 请求体 JSON 解析失败映射 400

| # | 任务 | 产出 | 状态 |
|---|---|---|---|
| 1 | 需求/方案/任务文档 | `docs/issue-17/spec.md`、`plan.md`、`task.md` | done |
| 2 | 红：MockMvc 用例（裸键 400 / 空 body 400 / 非 JSON 400 / 合法 200） | `GlobalExceptionHandlerTest` | done |
| 3 | 绿：`GlobalExceptionHandler` 新增 `HttpMessageNotReadableException` handler | 400 + `HDGW_INVALID_REQUEST` | done |
| 4 | 文档同步：`deploy/PROD-DEPLOY.md` 故障排查补 400 归因与 PowerShell 引号剥离说明 | `deploy/PROD-DEPLOY.md` | done |
| 5 | 全量回归 `mvn test` | 测试全绿 | done |
| 6 | 自审查（5 轴 + pre-pr 增量检查）→ 建 PR → Issue 评论 → A2A QA | PR | 进行中 |