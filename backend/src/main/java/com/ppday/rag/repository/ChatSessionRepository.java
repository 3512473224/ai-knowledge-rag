package com.ppday.rag.repository;

import com.ppday.rag.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByKbIdOrderByUpdateTimeDesc(Long kbId);

    /**
     * 历史会话筛选：按知识库 + 标题关键词。
     */
    @Query("SELECT s FROM ChatSession s WHERE (:kbId IS NULL OR s.kbId = :kbId)"
            + " AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%'))"
            + " ORDER BY s.updateTime DESC")
    List<ChatSession> search(@Param("kbId") Long kbId, @Param("keyword") String keyword);
}
