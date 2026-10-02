package com.ppday.rag.repository;

import com.ppday.rag.entity.DocChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    /**
     * 关键词检索：pg_trgm 三字符相似度，对中文按字切分同样有效。
     * 只查当前生效版本（JOIN kb_document_version 过滤 is_current），
     * 且限定在指定知识库内——与向量检索的 scope 过滤语义一致。
     */
    @Query(value = """
            SELECT c.id, c.document_id, c.chunk_index, c.content, c.file_name,
                   similarity(c.content, :query) AS sim
            FROM kb_chunk c
            JOIN kb_document_version v ON v.id = c.version_id
            JOIN kb_document d ON d.id = c.document_id
            WHERE v.is_current = true
              AND d.kb_id = :kbId
              AND c.content % :query
            ORDER BY sim DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkSimRow> keywordSearch(@Param("query") String query,
                                   @Param("kbId") Long kbId,
                                   @Param("limit") int limit);

    List<DocChunk> findByVersionIdOrderByChunkIndexAsc(Long versionId);

    void deleteByDocumentId(Long documentId);

    interface ChunkSimRow {
        Long getId();

        Long getDocumentId();

        Integer getChunkIndex();

        String getContent();

        String getFileName();

        Double getSim();
    }
}
