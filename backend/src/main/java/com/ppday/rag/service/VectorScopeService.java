package com.ppday.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 向量元数据 scope 维护。
 *
 * 每个向量的 metadata 里有一个 scope 字段，格式为 "{kbId}:current" 或 "{kbId}:archived"。
 * 检索时用 scope 精确过滤，一次 eq 查询同时完成"限定知识库"和"只查当前版本"，
 * 避免在 Spring AI 的 FilterExpression 里拼 AND（不同版本 API 差异大，容易踩坑）。
 *
 * 为什么用 SQL 直接改 metadata：Spring AI 的 VectorStore 没有"更新元数据"接口，
 * 但 pgvector 的 vector_store 表 metadata 列就是 JSONB，一条 jsonb_set 即可。
 * 版本回滚因此是瞬时操作，不需要重新向量化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorScopeService {

    /** PgVectorStore 默认表名（initialize-schema 自动建表） */
    private static final String TABLE = "vector_store";

    private final JdbcTemplate jdbcTemplate;

    public String scopeOf(Long kbId, boolean current) {
        return kbId + (current ? ":current" : ":archived");
    }

    /**
     * 新版本入库前调用：把该文档名下所有旧向量标记为 archived，
     * 避免新旧版本向量混查。表不存在时（如纯 mock 环境）吞掉异常。
     */
    public void archiveAll(Long documentId, Long kbId) {
        updateScope(documentId, null, scopeOf(kbId, false));
    }

    /**
     * 版本回滚调用：先把该文档全部向量置 archived，再把目标版本置 current。
     */
    public void setCurrent(Long documentId, Long kbId, Long versionId) {
        updateScope(documentId, null, scopeOf(kbId, false));
        updateScope(documentId, versionId, scopeOf(kbId, true));
    }

    private void updateScope(Long documentId, Long versionId, String scope) {
        try {
            if (versionId == null) {
                jdbcTemplate.update(
                        "UPDATE " + TABLE + " SET metadata = jsonb_set(metadata, '{scope}', to_jsonb(?))"
                                + " WHERE metadata->>'documentId' = ?",
                        scope, documentId.toString());
            } else {
                jdbcTemplate.update(
                        "UPDATE " + TABLE + " SET metadata = jsonb_set(metadata, '{scope}', to_jsonb(?))"
                                + " WHERE metadata->>'documentId' = ? AND metadata->>'versionId' = ?",
                        scope, documentId.toString(), versionId.toString());
            }
        } catch (Exception e) {
            // 防御性：向量表异常不应该阻断版本状态流转，记日志即可
            log.warn("更新向量 scope 失败 documentId={} versionId={}", documentId, versionId, e);
        }
    }
}
