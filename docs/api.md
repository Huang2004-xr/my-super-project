# API 说明

## Java API

### GET /api/health

返回 Java 自身状态和 Python 子服务状态。Python 不可用时该接口仍返回 Java 可读状态。

### GET /api/business-modules

返回五个业务模块的 key、名称、说明、默认 Agent 类型和默认目标。

### POST /api/agent-runs

请求：

```json
{
  "businessModule": "MATRIX_PUBLISH",
  "agentType": "ContentAgent",
  "goal": "选择矩阵账号并创建一条视频发布任务",
  "input": {
    "platform": "douyin",
    "accountId": "matrix-account-001",
    "materialId": "video-material-001"
  }
}
```

响应：

```json
{
  "runId": "uuid",
  "businessModule": "MATRIX_PUBLISH",
  "agentType": "ContentAgent",
  "goal": "选择矩阵账号并创建一条视频发布任务",
  "status": "completed",
  "plan": [],
  "steps": [],
  "toolCalls": [],
  "traces": [],
  "artifacts": [],
  "finalResult": "..."
}
```

### 错误响应

```json
{
  "code": "BAD_REQUEST",
  "message": "unsupported businessModule: UNKNOWN",
  "requestId": "uuid",
  "timestamp": "2026-05-06T00:00:00Z"
}
```

## Python API

Java 调用 Python 的接口与 Java API 语义保持一致，但路径使用 `/agent/*`。Python 接口不对前端开放。
