package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** pdf / docx / txt / md */
    private String fileType;

    private Long fileSize;

    /** sha256，用于秒传去重 */
    @Column(unique = true, length = 64)
    private String sha256;

    /** PENDING / PROCESSING / DONE / FAILED */
    private String status;

    private Integer chunkCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    @CreationTimestamp
    private LocalDateTime createTime;
}
