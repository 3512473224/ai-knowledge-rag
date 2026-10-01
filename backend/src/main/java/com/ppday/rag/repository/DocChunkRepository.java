package com.ppday.rag.repository;

import com.ppday.rag.entity.DocChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    /**
     * 关键词检索：pg_trgm 三字符相似度，对中文按字切分同样有效。
     * 与向量检索互补：向量擅长语义（"去年Q3财报"~"2024年第三季度财报"），
     * 关键词擅长精确匹配（型号、数字、人名），两者 RRF 融合。
     */
    @Query(value = """
            SELECT id, document_id, chunk_index, content, file_name,
                   similarity(content, :query) AS sim
            FROM kb_chunk
            WHERE content % :query
            ORDER BY sim DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkSimRow> keywordSearch(@Param("query") String query, @Param("limit") int limit);

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
