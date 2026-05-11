# 企业级改造后续计划

## Summary
目标：在“单机内网部署”前提下，优先做工程规范和可维护性改造，同时补齐前后端分离、接口契约、测试、错误处理、日志、配置、文件存储、异步任务和知识库能力，使项目从原型升级为可稳定交付的企业内部系统。

默认优先级：
1. 工程规范与模块拆分
2. 后端接口与数据可靠性
3. 前端可维护性与用户体验
4. Python Agent 运行稳定性
5. 测试、部署、运维和文档

## Key Changes

### 1. 项目结构与代码规范
- 前端从单文件 `main.tsx/api.ts` 拆分为：
  - `pages/`：Agent、知识库、图库、设置、登录
  - `components/`：通用按钮、表单、列表、状态标签、文件卡片
  - `api/`：按领域拆分 auth、agent、knowledge、assets、settings
  - `types/`：统一前后端 DTO 类型
  - `hooks/`：认证、全局错误、上传、轮询、会话状态
- 后端按领域整理包结构：
  - `auth`、`agent`、`knowledge`、`asset`、`conversation`、`admin`、`common`
  - Controller 只处理 HTTP，Service 只处理业务，Repository 只处理数据访问
- Java DTO 与 Entity 分离：
  - Controller 不直接返回 Entity
  - 新增 request/response DTO，避免数据库字段泄漏到前端
- 统一命名：
  - 前端接口字段继续使用 camelCase
  - 数据库字段继续使用 snake_case
  - Java 内部统一 camelCase
- 注释策略：
  - 只在复杂业务边界加短注释，例如异步索引状态机、文件路径安全校验、Agent 调用失败映射
  - 不给简单 getter/setter、显而易见代码加注释

### 2. 后端企业级改造
- 引入统一异常体系：
  - `BusinessException`
  - `NotFoundException`
  - `UnauthorizedException`
  - `ExternalServiceException`
  - `FileStorageException`
  - `ValidationException`
- 所有接口统一错误响应：
  - `code`
  - `message`
  - `requestId`
  - `timestamp`
  - `path`
- 引入统一请求追踪：
  - 接收或生成 `X-Request-Id`
  - Java 调 Python Agent 时透传 `X-Request-Id`
  - 日志中打印 requestId、userId、action、耗时、结果
- 数据库迁移：
  - 禁用生产环境 `ddl-auto:update`
  - 引入 Flyway 或 Liquibase
  - 所有表结构变更走版本化 migration
- 鉴权与权限：
  - 当前 Bearer Token 保留
  - 增加会话过期、刷新、注销、管理员权限校验
  - 管理接口必须校验 `role=ADMIN`
  - 用户数据查询必须全部带 `userId` 隔离
- 文件存储：
  - 保留本地真实文件存储
  - 增加文件元数据 DTO
  - 文件删除、重命名、下载、预览接口
  - 增加文件 hash、重复上传检测、病毒扫描预留接口
- 异步任务：
  - 当前 `@Async` 作为 v1 保留
  - 增加任务状态表或扩展知识库文档状态：
    - `QUEUED`
    - `PROCESSING`
    - `COMPLETED`
    - `FAILED`
    - `CANCELLED`
  - 后续可替换为本地队列或 Redis/RabbitMQ，不影响前端接口
- Agent 调用：
  - Java 统一通过 `AgentServiceClient`
  - 增加超时、重试、熔断、错误分类
  - Python Agent 不可用时返回明确业务错误，不让前端看到底层异常

### 3. 前端企业级改造
- API 层统一：
  - 所有请求走统一 client
  - 自动带 token
  - 自动处理 401、403、500、网络失败
  - 统一错误提示模型
- 登录态：
  - token 不再散落在页面逻辑中
  - 建立 `AuthProvider/useAuth`
  - 401 自动清理登录态并跳转登录页
- 页面状态：
  - 每个页面有明确 loading、empty、error、success 状态
  - 上传任务显示进度、大小限制、失败原因
  - 知识库异步索引增加自动轮询，完成或失败后停止
- 组件化：
  - `StatusBadge`
  - `FileUploadButton`
  - `AssetCard`
  - `KnowledgeDocumentList`
  - `ErrorBanner`
  - `ConfirmDialog`
- 表单体验：
  - 前端校验文件类型、大小、必填字段
  - 后端仍保留完整校验，前端校验只用于体验
- 前后端分离：
  - 前端只访问 Java `/api/*`
  - 前端不直接访问 Python Agent、MySQL、Milvus、Ollama
  - API base URL 只通过 `VITE_API_BASE_URL` 配置
- 可读性：
  - 页面组件不超过合理长度
  - 复杂流程抽到 hook 或 service
  - 类型定义与 API 响应保持一致

### 4. 功能优化计划

#### 超级 Agent
- 增加 Run 状态：
  - `PENDING`
  - `RUNNING`
  - `SUCCEEDED`
  - `FAILED`
- 前端展示完整执行过程：
  - Trace
  - 工具调用
  - Agent 步骤
  - 最终结果
- 支持重新执行、复制结果、失败重试
- Agent 输入增加结构化校验，避免空消息、非法 capability、无效知识库 ID

#### 知识库
- 文档上传后立即显示 `QUEUED`
- 前端自动轮询索引状态
- 增加文档删除、重新索引、失败重试
- 搜索结果展示来源文档、片段、页码、分数
- 后端搜索只返回当前用户和当前知识库的数据
- Python 侧增加解析失败分类：
  - 文件类型不支持
  - OCR 不可用
  - PDF 无文本
  - Milvus 不可用
  - Embedding 失败

#### 图库/文件资产
- 图片、视频、文档统一文件资产模型
- 上传前端显示大小、类型、上传进度
- 后端增加删除接口，同时删除数据库记录和真实文件
- 生成类资产与真实上传资产明确区分：
  - 生成类只展示元数据
  - 上传类可预览/下载
- 文件接口禁止越权访问，继续通过 userId 校验

#### 会话与历史记录
- 会话列表分页
- 消息分页或按时间加载
- 会话标题支持修改
- 删除会话时级联处理消息和 Run 关联
- 记忆摘要过程异步化，避免阻塞聊天

#### AI Provider 设置
- API Key 继续加密存储
- 设置页增加连接测试
- 增加启用/禁用、默认模型、超时时间配置
- 管理员可配置全局默认 Provider，用户可覆盖

#### 管理后台
- 用户管理：
  - 禁用/启用用户
  - 重置密码
  - 查看角色
- 运行监控：
  - Agent Run 列表
  - 失败任务列表
  - 知识库索引失败列表
- 审计日志：
  - 登录
  - 上传
  - 删除
  - 管理操作
  - AI Provider 修改

### 5. API / Interface Changes
- 所有后端接口返回 DTO，不直接返回 Entity。
- 所有错误统一为：
  ```json
  {
    "code": "ERROR_CODE",
    "message": "human readable message",
    "requestId": "uuid",
    "timestamp": "2026-05-09T00:00:00Z",
    "path": "/api/..."
  }
  ```
- 新增或补齐接口：
  - `GET /api/assets/files/{fileAssetId}/content`
  - `DELETE /api/assets/files/{fileAssetId}`
  - `GET /api/knowledge-bases/{id}/documents`
  - `DELETE /api/knowledge-bases/{id}/documents/{documentId}`
  - `POST /api/knowledge-bases/{id}/documents/{documentId}/reindex`
  - `GET /api/agent-runs/{runId}/traces`
  - `POST /api/ai-providers/{id}/test`
- 保持前后端分离边界：
  - 浏览器只访问 Java API
  - Java 内部访问 Python Agent
  - Python Agent 继续用内部 token 保护 `/agent/*`

## Test Plan
- 后端单元测试：
  - 鉴权成功/失败
  - 用户数据隔离
  - 上传大小限制
  - 文件路径越界拦截
  - 知识库上传状态流转
  - Agent 调用失败映射
- 后端集成测试：
  - H2 test profile
  - Mock Python Agent
  - 文件上传、预览、删除
  - 知识库索引成功、失败、重试
- 前端测试：
  - 登录态恢复
  - API 错误提示
  - 上传前校验
  - 知识库索引轮询
  - 图库真实文件打开
  - 401 自动退出
- Python Agent 测试：
  - 内部 token 校验
  - 上传大小限制
  - 文档解析成功/失败
  - Milvus 不可用降级错误
  - 搜索返回结构稳定
- 构建验证：
  - `mvn test`
  - `npm run build`
  - `python -m compileall app`
  - 后续增加一键 CI 脚本执行全部检查

## Assumptions
- 部署目标按“单机内网部署”规划。
- 当前优先级按“工程规范”规划，稳定性和功能完善紧随其后。
- Java 后端继续作为唯一对外 API 网关。
- Python Agent 不直接暴露给浏览器。
- MySQL、Milvus、Ollama 仍作为本机或内网基础服务存在。
- 当前项目先不引入 Kubernetes；如后续需要云原生部署，再单独规划容器镜像、配置中心、服务发现和横向扩展。
