package com.ppday.rag.repository;

import com.ppday.rag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByKbIdOrderByCreateTimeDesc(Long kbId);

    Optional<Document> findByKbIdAndFileName(Long kbId, String fileName);

    long countByKbId(Long kbId);
}
