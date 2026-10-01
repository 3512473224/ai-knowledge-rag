package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    /** user / assistant */
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 命中的资料来源 JSON，前端渲染引用角标 */
    @Column(columnDefinition = "TEXT")
    private String sources;

    @CreationTimestamp
    private LocalDateTime createTime;
}
