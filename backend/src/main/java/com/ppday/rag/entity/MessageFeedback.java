package com.ppday.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户对某条 AI 回答的反馈：有用 / 无用（可备注原因）。
 * 被标"无用"的进入待优化看板，status=open 待处理，fixed=已处理。
 */
@Data
@Entity
@Table(name = "kb_feedback")
public class MessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 一条回答只能有一条反馈记录，重复提交走更新 */
    @Column(unique = true, nullable = false)
    private Long messageId;

    /** true=有用，false=无用 */
    @Column(nullable = false)
    private Boolean useful;

    /** 无用原因备注 */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** open / fixed */
    @Column(nullable = false, length = 16)
    private String status = "open";

    @CreationTimestamp
    private LocalDateTime createTime;
}
