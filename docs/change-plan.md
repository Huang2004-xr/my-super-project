# 外部模型接入整改计划

## 阅读范围

参考仓库：`pengchengneo/Claude-Code`，本次读取的提交为 `b78dd22a091b717c8938ab98c736bc04825a8ee8`。该仓库是恢复版源码，适合作为工程结构参考，不建议直接复制实现。

已重点阅读的模块：

- 模型和供应商：`src/utils/model/*`、`src/services/api/client.ts`、`src/services/api/withRetry.ts`、`src/services/api/claude.ts`
- API 日志和错误：`src/services/api/logging.ts`、`src/services/api/errors.ts`、`src/services/api/errorUtils.ts`
- 设置体系：`src/utils/settings/*`
- 权限体系：`src/utils/permissions/*`
- MCP 配置和连接：`src/services/mcp/*`
- 工具执行：`src/services/tools/toolExecution.ts`、`src/services/tools/toolOrchestration.ts`
- 子代理和任务：`src/tools/AgentTool/*`、`src/tasks/*`、`src/coordinator/*`
- 安全相关：`src/utils/subprocessEnv.ts`、`src/bridge/*`

## 总体结论

Claude-Code 的价值不在于某个供应商表单，而在于把“模型选择、别名映射、配置来源、协议客户端、验证、重试、日志脱敏、工具权限”拆成独立层。我们当前项目已经完成了外部 Provider 的第一版，但仍有几个风险：预设写在前端、模型别名解析散在服务中、失败原因不够结构化、Python 端回退过宽、配置 JSON 和 UI 文案存在编码问题。

我们的产品是 Web + Java + Python 架构，不能照搬它的 CLI 环境变量切换方案。更合适的方向是：后端拥有 Provider 预设和模型规则，前端只负责展示和编辑，Java 负责选型和密钥安全，Python 只接收一次运行所需的最小 providerConfig。

## P0 必须整改

1. 修复前端中文乱码

当前 `frontend/src/settings/AiProviderSettings.tsx` 已出现中文文案乱码，例如能力标签显示为异常字符。需要统一把源码文件保存为 UTF-8，并恢复所有中文文案。后续提交前增加一次 `rg "�|鑱|鍥|瑙|鐭|浣|銆"` 检查，避免乱码继续进入仓库。

2. Provider 预设从前端迁移到后端

当前 `PROVIDER_PRESETS` 写在 `frontend/src/settings/AiProviderSettings.tsx`，这会导致预设、模型、Base URL 和协议规则无法被后端测试、审计和复用。新增后端 `AiProviderPresetService` 和 `AiProviderPresetController`，提供 `GET /api/ai-provider-presets`。前端改为请求后端预设，保留手动模式。

3. 增加模型别名解析服务

参考 Claude-Code 的 `aliases.ts`、`model.ts`、`configs.ts`，新增 Java 侧 `AiProviderModelResolver`。统一解析 `main`、`thinking`、`fast`、`strong`、`chat`、`imagePrompt`、`videoScript`、`knowledge`。优先级固定为：显式能力模型 > modelAliases > defaultModel > legacy modelName。自定义模型名必须保留大小写，但已知预设可以给出大小写提示。

4. 测试连接返回结构化错误码

当前 `ProviderAdapterSupport.httpError` 主要返回字符串，前端难以判断原因。新增错误码：`AUTH_FAILED`、`MODEL_NOT_FOUND`、`ENDPOINT_NOT_FOUND`、`RATE_LIMITED`、`OVERLOADED`、`TIMEOUT`、`PROTOCOL_MISMATCH`、`BALANCE_OR_QUOTA`、`UNKNOWN`。`AiProviderTestResponse` 增加 `errorCode`、`httpStatus`、`providerRequestId` 字段，列表页展示短原因，详情页展示原始摘要。

5. 收紧回退策略

当前 Python `LlmClient._chat_external` 只要失败且 `enableFallback` 开启就尝试下一个 provider。应改为只对网络错误、超时、429、529、5xx 进行自动回退；401/403、模型不存在、协议不匹配、Base URL 错误不回退，直接提示配置错误。这样可以避免用户误以为错误 provider 已生效。

6. 全链路密钥脱敏测试

Java 列表接口已不返回明文 key，但还需要覆盖：`providerConfig` 不写入 run detail，Python trace 不记录 `apiKey`，异常消息不拼接 key，请求日志只记录 provider、apiFormat、model、status。参考 Claude-Code 对 API 日志的做法，只记录可诊断的元数据。

7. 修正 MiMo 预设和校验提示

MiMo Token Plan CN 应默认使用 `anthropic_messages`、Base URL `https://token-plan-cn.xiaomimimo.com/anthropic`、认证字段 `x-api-key`、模型 `mimo-v2.5-pro`。如果用户填写 `MiMo-V2.5-Pro` 这类大小写不一致模型，前端和后端测试结果应提示“该服务通常使用小写模型 id”，不要只显示供应商返回的 400。

## P1 高优先级

1. 把协议适配器用于运行前校验和运行时

Java 已有 `OpenAiChatCompletionsAdapter`、`AnthropicMessagesAdapter`、`OpenAiResponsesAdapter`，但 Python 端仍自己拼请求。建议抽出 Python `ProviderClient` 工厂，并复用同一套 URL 拼接、认证头、错误分类规则，避免“测试通过、运行失败”。

2. 增加请求 ID 和可诊断元数据

参考 Claude-Code 的 `x-client-request-id` 思路，每次 provider 测试和 Python 运行都生成 `requestId`。Java、Python、前端展示同一个 ID，便于定位供应商错误。记录 provider 名称、协议、模型、耗时、HTTP 状态，不记录 prompt 全文和密钥。

3. 增加模型验证缓存

参考 `validateModel.ts` 的 `validModelCache`。同一个 provider、apiFormat、baseUrl、model 在短时间内验证成功后可缓存 10 到 30 分钟，减少用户反复测试和运行时的额外请求。缓存键不能包含明文 key，可使用 providerId + keyVersion。

4. 增加后端模型选项接口

新增 `GET /api/ai-provider-presets/{presetKey}/model-options` 或统一 `GET /api/ai-provider-model-options`。前端模型输入框可以给出主模型、快速模型、强力模型、推理模型建议，用户仍可手填。国内模型优先按 OpenAI-compatible；Anthropic 和 MiMo 走 Messages 协议。

5. 让配置 JSON 自动补全但不覆盖用户修改

当前前端已经有 `jsonManuallyEdited` 思路，但需要更清晰：前面字段变更时自动生成 JSON；用户手动编辑后停止自动覆盖；提供“重新生成配置 JSON”按钮；JSON 保存前在前端和后端都校验格式。

6. 有效配置接口增加健康摘要

`GET /api/ai-providers/effective` 应返回每个能力当前 provider、model、apiFormat、region、lastTestStatus、lastTestedAt、lastTestMessage、fallbackEnabled。这样设置页顶部可以准确显示“本地 Ollama / 外部 API / 混合配置”。

7. 代理配置先隐藏或真正实现

前端已经预留代理配置，但后端和 Python 不使用。要么在第一版 UI 中标注“暂未启用”并禁用保存，要么把代理 URL、用户名、密码纳入 providerConfig 并在 Java 测试和 Python 调用中统一使用。

## P2 后续增强

1. 成本和用量统计

参考 `modelCost.ts` 的结构，为 Provider 增加 `pricingJson`，记录输入/输出 token 单价、成本倍率和计费模式。第一阶段可以只显示估算，不做强计费。

2. 工具并发和权限模型

Claude-Code 对只读工具并发、写操作串行、权限规则来源做得很细。我们后续如果增强 Agent 工具执行，应参考它的分层：工具定义、权限判断、执行器、hook、日志各自独立。

3. MCP 接入

参考 `services/mcp/types.ts`、`config.ts` 的做法，MCP 需要配置 schema、来源 scope、连接状态、工具归属、去重和鉴权。当前任务重点是模型供应商，不建议现在把 MCP 混入 Provider 配置。

4. 子代理和任务状态

`AgentTool`、`runAgent.ts`、`tasks/*` 的价值在于任务生命周期、进度摘要、后台执行和隔离工作区。我们可以后续借鉴任务状态机，但不应在 Provider 接入尚未稳定时引入多代理复杂度。

5. 功能开关

Claude-Code 大量使用 feature gate。我们项目可以只保留轻量配置开关，例如 `enableExternalProviders`、`enableProviderFallback`、`enableResponsesApi`，避免过度复杂。

## 具体代码整改位置

后端：

- `backend-java/src/main/java/com/example/agentplatform/service/AiProviderService.java`：拆出预设、模型解析、fallback 决策、错误分类。
- `backend-java/src/main/java/com/example/agentplatform/service/aiprovider/*`：统一返回结构化 `ProviderTestResult`，补充请求 ID、HTTP 状态和错误码。
- 新增 `backend-java/src/main/java/com/example/agentplatform/service/AiProviderPresetService.java`
- 新增 `backend-java/src/main/java/com/example/agentplatform/controller/AiProviderPresetController.java`
- 新增 DTO：`AiProviderPresetResponse`、`AiProviderModelOptionResponse`、`ProviderErrorCode`

Python：

- `agent-python/app/core/llm.py`：拆成 `ProviderClient` 工厂，按协议分文件或分 class；新增失败分类；限制自动 fallback 条件；确保 trace 不含密钥。
- `agent-python/app/core/runtime.py`：只记录 provider、apiFormat、model、requestId、capability。

前端：

- `frontend/src/settings/AiProviderSettings.tsx`：修复乱码；预设改为后端加载；配置 JSON 增加“重新生成”；模型字段接入后端模型选项。
- `frontend/src/api.ts`：增加预设和模型选项 API。
- `frontend/src/types.ts`：补齐 `AiProviderPreset`、结构化测试错误类型。
- `frontend/src/styles.css`：保持配置页滚动条和固定底部栏，不让按钮遮挡表单。

## 分阶段实施

第一阶段：清理和稳定

- 修复前端乱码。
- 迁移 Provider 预设到 Java 后端。
- 新增模型解析服务。
- 测试连接返回结构化错误。
- 保证 `npm run build`、`mvn test`、`python -m compileall app` 通过。

第二阶段：运行链路一致

- Python 拆出协议客户端。
- Java 测试和 Python 运行使用相同协议规则。
- fallback 只对可恢复错误生效。
- 增加密钥脱敏和 trace 测试。

第三阶段：用户体验

- 前端加载预设和模型选项。
- 配置 JSON 自动生成、手动编辑保护、重新生成按钮。
- effective 接口显示健康状态和当前能力映射。
- MiMo、DeepSeek、Qwen、Kimi、智谱、OpenAI、Anthropic、OpenRouter 预设可一键填充。

第四阶段：高级能力

- 代理配置真实接入或移除 UI。
- 成本估算和用量统计。
- MCP、工具权限、多 Agent 任务状态机按需求逐步引入。

## 不建议照搬的部分

- 不建议照搬环境变量全局切换。我们的配置是用户级、数据库级，应以后端 Provider 表为准。
- 不建议照搬 Anthropic-only 的模型族命名。可以借鉴别名层，但需要支持国内模型和 OpenAI-compatible 模型。
- 不建议现在引入完整 CLI 命令体系、插件体系、MCP 管理和远程任务桥接。
- 不建议直接接 Bedrock、Vertex、Foundry SDK，除非后续明确要做企业云部署。

## 验收标准

- 供应商预设由后端返回，前端不再硬编码核心预设。
- 添加、编辑、测试、启用、禁用、删除 Provider 流程可用。
- API Key 不在任何列表、trace、run detail、异常日志中明文出现。
- MiMo 使用推荐预设可测试成功；模型大小写错误能给出明确提示。
- 外部 Provider 失败时错误原因可区分认证、模型、协议、端点、限额、超时。
- 开启 fallback 后只在可恢复错误下切换备用 Provider。
- `npm run build`、`mvn test`、`python -m compileall app` 全部通过。
