# 本机独立 Milvus 配置清单

## 定位

Milvus 在本项目中按独立基础设施处理，和 MySQL、Redis 一样预先部署、预先启动，不由应用脚本自动拉起。

项目链路固定为：

- Frontend -> Java API
- Java API -> Python Agent
- Python Agent -> Milvus

Java 不直接访问 Milvus。

## 最低可用配置

```text
KNOWLEDGE_MILVUS_HOST=127.0.0.1
KNOWLEDGE_MILVUS_PORT=19530
KNOWLEDGE_MILVUS_COLLECTION=knowledge_chunks
KNOWLEDGE_MILVUS_METRIC_TYPE=COSINE
KNOWLEDGE_MILVUS_INDEX_TYPE=IVF_FLAT
KNOWLEDGE_VECTOR_DIM=384
KNOWLEDGE_EMBEDDING_PROVIDER=hash
KNOWLEDGE_EMBEDDING_MODEL=nomic-embed-text
KNOWLEDGE_OCR_LANG=chi_sim+eng
```

## 本机检查项

### 1. 端口连通

```powershell
Test-NetConnection 127.0.0.1 -Port 19530
```

预期：`TcpTestSucceeded = True`

### 2. Python 知识库健康检查

```powershell
Invoke-RestMethod http://localhost:8000/agent/knowledge/health
```

预期关键字段：

```json
{
  "status": "ok",
  "vectorStore": {
    "available": true
  }
}
```

### 3. Java 聚合健康检查

```powershell
Invoke-RestMethod http://localhost:8620/api/health
```

预期：Java 能正常拿到 Python health，不再返回连接拒绝。

## 文档上传能力边界

当前原型已支持：

- TXT
- MD / Markdown
- DOCX
- 图片 OCR
- 可提取文本的 PDF

当前原型暂未打通完整扫描版 PDF OCR 流水线。若上传扫描 PDF，会返回明确错误提示，而不是静默失败。

## OCR 前置要求

若要上传图片或图片型文档，需额外满足：

```powershell
tesseract --version
```

若命令不可用，需要先安装 Tesseract 并加入 PATH。

推荐语言包：

```text
chi_sim
eng
```

## 推荐联调顺序

```text
1. 启动独立 Milvus
2. 启动 Ollama
3. 启动 MySQL
4. 启动 Python Agent
5. 启动 Java API
6. 启动 Frontend
7. 新建知识库
8. 上传 TXT / MD / DOCX 做首轮验证
9. 在知识库页先跑一次检索测试
10. 再回到聊天页勾选“引用知识库”发问
```

## 首轮建议上传样本

首轮不要先用扫描 PDF，建议优先用：

- UTF-8 编码 TXT
- Markdown 文档
- 正常可复制文字的 DOCX
- 可复制文字的 PDF

这样可以先确认链路稳定，再扩展 OCR 场景。

## 常见故障定位

### 知识库向量服务不可用

优先排查：

- Milvus 是否已启动
- 19530 端口是否可达
- Python Agent 的 Milvus 环境变量是否正确
- Python health 中 `vectorStore.available` 是否为 true

### 上传成功但检索为空

优先排查：

- 文档是否真正完成索引
- 文档 `chunkCount` 是否大于 0
- 查询语句是否和文档表述差异过大
- 当前是否使用 hash embedding，若是，则召回质量只是联调用基线

### 图片 OCR 无结果

优先排查：

- Tesseract 是否安装并可执行
- `KNOWLEDGE_OCR_LANG` 是否包含对应语言包
- 图片本身是否清晰可识别
