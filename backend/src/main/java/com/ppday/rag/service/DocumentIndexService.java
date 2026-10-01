package com.ppday.rag.service;

import com.ppday.rag.entity.DocChunk;
import com.ppday.rag.entity.Document;
import com.ppday.rag.rag.MarkdownSplitter;
import com.ppday.rag.repository.DocChunkRepository;
import com.ppday.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档索引任务：Tika 解析 -> 两级切分 -> 向量化入库 -> chunk 明文存关系表。
 *
 * 注意：必须独立成一个 Bean 才能让 @Async 生效。
 * 如果把 @Async 方法和调用方写在同一个类里，upload() 调 processAsync()
 * 走的是 this 直接调用，绕过了 Spring 的代理，异步会静默失效变成同步——
 * 这是 @Async 最经典的坑，面试常考。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexService {

    private final DocumentRepository documentRepository;
    private final DocChunkRepository docChunkRepository;
    private final MarkdownSplitter splitter;
    private final VectorStore vectorStore;
    private final Tika tika = new Tika();

    @Async("docProcessExecutor")
    public void indexAsync(Long documentId, byte[] bytes) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return;
        }
        try {
            // 1. Tika 解析纯文本
            String text = tika.parseToString(new ByteArrayInputStream(bytes));
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalStateException("文档内容为空或无法解析（可能是扫描版 PDF）");
            }
            // 2. 两级切分
            List<String> chunks = splitter.split(text);
            // 3. 批量向量化入库（Spring AI 内部批量调 embedding 接口）
            List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", i);
                metadata.put("fileName", doc.getFileName());
                aiDocs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
            }
            vectorStore.add(aiDocs);

            // 4. chunk 明文同步存关系表，供关键词检索（pg_trgm）用
            List<DocChunk> entities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocChunk entity = new DocChunk();
                entity.setDocumentId(documentId);
                entity.setChunkIndex(i);
                entity.setContent(chunks.get(i));
                entity.setFileName(doc.getFileName());
                entities.add(entity);
            }
            docChunkRepository.saveAll(entities);

            doc.setStatus("DONE");
            doc.setChunkCount(chunks.size());
            documentRepository.save(doc);
            log.info("文档 {} 向量化完成，共 {} 个 chunk", doc.getFileName(), chunks.size());
        } catch (Exception e) {
            log.error("文档 {} 处理失败", doc.getFileName(), e);
            doc.setStatus("FAILED");
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        }
    }
}
