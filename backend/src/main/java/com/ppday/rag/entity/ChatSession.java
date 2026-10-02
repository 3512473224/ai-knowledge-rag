package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    /** 会话创建时选定的知识库，检索范围锁定该库 */
    private Long kbId;

    @CreationTimestamp
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
