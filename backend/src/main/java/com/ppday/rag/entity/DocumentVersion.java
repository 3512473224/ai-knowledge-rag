package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 文档版本：同一文档重复上传不再建新文档行，而是追加版本。
 * 检索永远只查 isCurrent=true 的版本，回滚=切换 isCurrent，开销极小。
 */
@Data
@Entity
@Table(name = "kb_document_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"documentId", "versionNo"}))
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long documentId;

    private Integer versionNo;

    private String fileName;

    /** pdf / docx / txt / md */
    private String fileType;

    private Long fileSize;

    @Column(length = 64)
    private String sha256;

    private Integer chunkCount = 0;

    /** PENDING / PROCESSING / DONE / FAILED */
    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    /** 是否当前生效版本 */
    private Boolean isCurrent = false;

    @CreationTimestamp
    private LocalDateTime createTime;
}
