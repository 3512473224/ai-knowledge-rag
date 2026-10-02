package com.ppday.rag.repository;

import com.ppday.rag.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreateTimeAsc(Long sessionId);

    long countBySessionIdInAndRole(Collection<Long> sessionIds, String role);

    long countBySessionIdInAndRoleAndRefusedTrue(Collection<Long> sessionIds, String role);

    /**
     * 热门问题 TopN：按用户提问原文精确分组计数。
     * 简单但真实——同一个问题被反复问，说明知识库缺这块内容。
     */
    @Query("SELECT m.content, COUNT(m) FROM ChatMessage m"
            + " WHERE m.sessionId IN :sessionIds AND m.role = 'user'"
            + " GROUP BY m.content ORDER BY COUNT(m) DESC")
    List<Object[]> topQuestions(@Param("sessionIds") Collection<Long> sessionIds, Pageable pageable);
}
