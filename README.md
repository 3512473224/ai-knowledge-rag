# 智能知识库问答系统（RAG）

基于 **Spring Boot 3 + Spring AI + PostgreSQL(pgvector) + Vue3** 的企业级 RAG 问答系统。
上传 pdf / docx / txt / md 文档后自动解析、切分、向量化入库；提问时走"向量检索 → 阈值过滤 → 大模型流式生成"链路，回答附带引用来源，答不上来直接拒答、绝不幻觉。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2、Spring AI 1.0（OpenAI 兼容：硅基流动 / DeepSeek / OpenAI / Ollama） |
| 向量库 | PostgreSQL 16 + pgvector（HNSW 索引，业务库与向量库一体化） |
| 缓存 | Redis |
| 文档解析 | Apache Tika |
| 前端 | Vue 3 + Vite + Element Plus，SSE 流式打字机 + Markdown 渲染 |
| 部署 | Docker Compose 一键启动 |

## 系统架构

```
                    ┌──────────────┐
  上传文档 ────────▶│ DocumentService│── Tika 解析 ──▶ MarkdownSplitter（标题粗切+滑动窗口细切）
                    │  @Async 异步   │──▶ Embedding ──▶ pgvector 入库（metadata: documentId/chunkIndex）
                    └──────────────┘
                    ┌──────────────┐
  用户提问 ────────▶│  ChatService   │──▶ 向量召回(topK=8,阈值0.70) + pg_trgm关键词召回
                    │  SSE 流式推送  │──▶ RRF融合 → 去重取top5 → 组装Prompt（引用约束）
                    └──────────────┘──▶ 大模型流式生成 ──▶ token/sources/done 事件
```

## 快速启动

```bash
# 1. 准备大模型 Key（以硅基流动为例，免费额度够跑 demo）
export LLM_API_KEY=sk-你的key

# 2. 一键启动（postgres+redis+backend+frontend）
docker compose up -d --build

# 3. 打开 http://localhost → 文档管理上传资料 → 问答对话提问
```

本地开发（不走 Docker）：

```bash
# 先起 postgres(pgvector) 和 redis，建库 ragdb
cd backend && mvn spring-boot:run      # 需配置 LLM_API_KEY 环境变量
cd frontend && npm install && npm run dev
```

## 核心亮点（简历可写）

1. **混合检索 Hybrid Search**：向量检索（语义，阈值 0.70 过滤）+ pg_trgm 关键词检索（字面精确），RRF 公式融合。解决纯向量检索的经典翻车——"去年 Q3 财报"被召回成"今年 Q3 财报"。
2. **两级文本切分**：按 Markdown 标题语义粗切 + 滑动窗口细切（800 字 / 重叠 120 字），chunk 带标题前缀，解决"答案落在切分边界"问题。
3. **防幻觉三件套**：相似度阈值过滤 + 无命中直接拒答 + System Prompt 强制"只基于资料回答并标注来源 [来源n]"。
4. **评估体系**：自建 golden 测试集（`eval/golden.jsonl`），`eval/eval.py` 一键跑出 Recall@5 / 拒答正确率 / 首字延迟，调参有数据说话——这是和"调包侠"拉开差距的关键。
5. **异步向量化管线**：上传接口立即返回 documentId，`@Async` 线程池后台做解析→切分→批量 embedding，前端轮询进度；sha 指纹实现秒传去重。
6. **SSE 流式问答**：`SseEmitter` 推送 token / sources / done 三类事件，nginx 关闭 `proxy_buffering` 保证实时性；引用来源带"向量/关键词"命中类型标签。
7. **数据一致性**：删除文档时同步删除其向量（`FilterExpressionBuilder` 按 documentId 过滤）+ 关系表 chunk 明文，避免脏数据被继续召回。

## 配置说明

所有 RAG 关键参数集中在 `application.yml` 的 `rag:` 段，可按需调优：
`rag.chunk.max-chars / overlap-chars`、`rag.retrieval.top-k / similarity-threshold / final-top-k`。
换模型只改环境变量：`LLM_BASE_URL`、`LLM_CHAT_MODEL`、`LLM_EMBEDDING_MODEL`
（注意 embedding 维度要与 `spring.ai.vectorstore.pgvector.dimensions` 一致，bge-m3=1024）。
