package com.ppday.rag.repository;

import com.ppday.rag.entity.MessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, Long> {

    Optional<MessageFeedback> findByMessageId(Long messageId);

    List<MessageFeedback> findByStatusOrderByCreateTimeDesc(String status);
}
