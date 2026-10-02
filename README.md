# 知库问答 · 团队知识库问答产品

一个**真实可用的知识库问答产品**：按主题建多个知识库，上传文档自动解析入库，
AI 只基于你的资料回答、每句话标注来源、答不上来就直说不知道。
用户点"无用"的回答会进入待优化看板，顺着它补文档，回答质量越用越好。

基于 **Spring Boot 3 + Spring AI + PostgreSQL(pgvector) + Vue3**。

## 产品功能

| 模块 | 功能 |
|---|---|
| 问答对话 | ChatGPT 式对话、SSE 流式输出、引用来源卡片（点击定位到文档）、回答下可点"有用/无用" |
| 知识库 | 多 workspace（公开/私有）、卡片式管理、文档数/问答数统计 |
| 文档管理 | 上传自动解析向量化、**同名文件重复上传生成新版本**、版本时间线、**一键回滚旧版本**、pdf/txt/md 在线预览 |
| 问答历史 | 按知识库筛选、标题关键词搜索、继续对话、删除 |
| 待优化看板 | 被标"无用"的回答集中展示：问题、回答摘要、用户备注、引用文档，处理后标记 fixed |
| 数据看板 | 问答总数、无答案率、热门问题 Top10、反馈好评率、Golden Set 评估结果 |

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2、Spring AI 1.0（OpenAI 兼容：硅基流动 / DeepSeek / OpenAI / Ollama） |
| 向量库 | PostgreSQL 16 + pgvector（HNSW 索引，业务库与向量库一体化） |
| 缓存 | Redis |
| 文档解析 | Apache Tika |
| 前端 | Vue 3 + Vite + Element Plus（自定义 emerald/slate 主题），SSE 流式打字机 + Markdown 渲染 |
| 部署 | Docker Compose 一键启动 |

## 系统架构

```
  知识库 (kb_base)
     └─ 文档 (kb_document) ─┬─ 版本 v3 (current) ──▶ 向量 scope="kbId:current" ◀── 检索只查这里
                            ├─ 版本 v2 (archived) ─▶ 向量 scope="kbId:archived"
                            └─ 版本 v1 (archived)

  上传 ──▶ Tika 解析 ──▶ 两级切分 ──▶ @Async 异步向量化 ──▶ pgvector + chunk 明文表
                                                        （旧版本向量 scope 翻为 archived）

  提问 ──▶ 混合检索（向量 scope 精确过滤 + pg_trgm 关键词，RRF 融合）
         ──▶ 无命中直接拒答（记 refused，供看板统计无答案率）
         ──▶ 有命中则组装 prompt → 大模型 SSE 流式生成 → token/sources/messageId/done
```

## 快速启动

```bash
# 1. 准备大模型 Key（OpenAI 兼容接口均可：硅基流动 / DeepSeek / OpenAI / Ollama）
export LLM_API_KEY=sk-你的key

# 2. 一键启动（postgres+redis+backend+frontend）
docker compose up -d --build

# 3. 打开 http://localhost，按下面 3 分钟脚本走一遍
```

本地开发（不走 Docker）：

```bash
# 先起 postgres(pgvector) 和 redis，建库 ragdb
cd backend && mvn spring-boot:run      # 需配置 LLM_API_KEY 环境变量
cd frontend && npm install && npm run dev
```

### 3 分钟演示脚本

1. **建库**：知识库页 → 新建"公司考勤制度"（私有）→ 点卡片进入。
2. **传文档**：上传一份考勤制度 pdf → 等状态变"已入库"；再改几个字重新上传同名文件 → 版本时间线出现 v2。
3. **问答**：问答页 → 选"公司考勤制度" → 问"迟到几次扣绩效？"→ 看流式回答 + 引用来源卡片 → 点卡片跳转到文档。
4. **反馈闭环**：在回答下点"👎 无用"并备注原因 → 待优化看板出现该条目 → 标记已处理。
5. **回滚**：文档详情页 → 版本时间线 → 把 v1 设为当前版本 → 检索立即切回 v1 内容。
6. **看板**：数据看板 → 看无答案率、热门问题；`cd eval && python3 eval.py --kb-id 1` 跑评估 → 刷新看板看 Recall@5。

## 核心亮点（简历可写）

1. **多知识库 workspace**：文档按库隔离，问答先选库；向量 metadata 的 scope 字段一次过滤完成"限定库 + 只查当前版本"。
2. **文档版本管理**：同名文件重复上传追加版本；回滚=翻转 DB 的 isCurrent + 向量 scope，瞬时生效，无需重新向量化。
3. **混合检索 Hybrid Search**：向量检索（语义，阈值 0.70 过滤）+ pg_trgm 关键词检索（字面精确），RRF 公式融合。解决纯向量检索的经典翻车——"去年 Q3 财报"被召回成"今年 Q3 财报"。
4. **反馈闭环运营**：回答下"有用/无用"按钮 → 无用进待优化看板（含用户备注、引用文档）→ 补文档 → 标记 fixed。知识库越用越准的产品机制。
5. **数据看板**：无答案率（拒答/回答总数）、热门问题 Top10、反馈好评率；Golden Set 评估结果（Recall@5/拒答正确率/首字延迟）同屏展示，调参有数据说话。
6. **防幻觉三件套**：相似度阈值过滤 + 无命中直接拒答 + System Prompt 强制"只基于资料回答并标注来源"。
7. **异步向量化管线**：上传接口立即返回，独立 Bean 的 `@Async` 方法后台做解析→切分→批量 embedding（避开自调用导致异步失效的坑）；sha 指纹实现秒传去重；@Transactional 上传与异步线程间的可见性竞态用重试兜底。
8. **数据一致性**：删除文档/知识库时同步清理其全部版本向量 + chunk 明文 + 落盘文件，避免脏数据被继续召回。

## 配置说明

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_API_KEY` | （必填） | 大模型 Key |
| `LLM_BASE_URL` | `https://api.siliconflow.cn` | OpenAI 兼容接口地址 |
| `LLM_CHAT_MODEL` | `deepseek-ai/DeepSeek-V3` | 对话模型 |
| `LLM_EMBEDDING_MODEL` | `BAAI/bge-m3` | 向量模型（1024 维，需与 pgvector 表一致） |
| `PG_HOST/PG_PORT/PG_DB/PG_USER/PG_PASSWORD` | `localhost/5432/ragdb/rag/rag123` | PostgreSQL |

> 注意：本分支（redo/muse-v2）新增了 kb_base / kb_document_version / kb_feedback 等表，
> 建议用全新数据库启动（`docker compose down -v` 后再 up），避免旧表结构干扰。
