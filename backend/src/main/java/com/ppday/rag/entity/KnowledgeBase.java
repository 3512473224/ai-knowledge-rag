package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 知识库（workspace）：文档按知识库隔离，问答时先选库再检索。
 * visibility=public 的库可被所有人查看，private 仅 owner 可见。
 */
@Data
@Entity
@Table(name = "kb_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** public / private */
    @Column(nullable = false, length = 16)
    private String visibility = "private";

    /** 预留多用户字段，当前单用户场景固定为 default */
    private String ownerId = "default";

    @CreationTimestamp
    private LocalDateTime createTime;
}
