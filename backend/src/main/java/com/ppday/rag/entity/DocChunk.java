package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * chunk 明文也存一份关系表，给"关键词检索（pg_trgm）"用，
 * 与向量检索做 RRF 融合 = 混合检索（Hybrid Search）。
 */
@Data
@Entity
@Table(name = "kb_chunk")
public class DocChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long documentId;

    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String fileName;
}
