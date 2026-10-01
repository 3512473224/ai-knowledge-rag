package com.ppday.rag.repository;

import com.ppday.rag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findBySha256(String sha256);
}
