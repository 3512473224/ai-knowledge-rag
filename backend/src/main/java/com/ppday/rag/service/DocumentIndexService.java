package com.ppday.rag.service;

import com.ppday.rag.entity.DocChunk;
import com.ppday.rag.entity.Document;
import com.ppday.rag.entity.DocumentVersion;
import com.ppday.rag.rag.MarkdownSplitter;
import com.ppday.rag.repository.DocChunkRepository;
import com.ppday.rag.repository.DocumentRepository;
import com.ppday.rag.repository.DocumentVersionRepository;
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
 * 必须独立成一个 Bean 才能让 @Async 生效：@Async 靠 Spring 代理实现，
 * 同一个类内部 this.直接调用会绕过代理，异步静默失效变成同步。
 *
 * 版本语义：每次上传产生一个新版本。向量 metadata 里带 scope="{kbId}:current"，
 * 新版本入库前先把旧向量的 scope 翻成 archived，这样检索永远只命中当前版本，
 * 回滚也只是翻转 scope，不需要重新向量化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocChunkRepository docChunkRepository;
    private final MarkdownSplitter splitter;
    private final VectorStore vectorStore;
    private final VectorScopeService scopeService;
    private final Tika tika = new Tika();

    @Async("docProcessExecutor")
    public void indexAsync(Long documentId, Long versionId, Long kbId, byte[] bytes) {
        // upload() 是 @Transactional 的，外层事务提交前异步线程可能读不到版本行，
        // 这里重试几次而不是直接放弃，避免文档卡在 PROCESSING。
        DocumentVersion version = null;
        Document doc = null;
        for (int i = 0; i < 20 && (version == null || doc == null); i++) {
            if (version == null) {
                version = versionRepository.findById(versionId).orElse(null);
            }
            if (doc == null) {
                doc = documentRepository.findById(documentId).orElse(null);
            }
            if (version == null || doc == null) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (version == null || doc == null) {
            log.error("版本行不可见，放弃索引 documentId={} versionId={}", documentId, versionId);
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

            // 3. 先把旧版本向量标记为 archived，避免新旧混查
            scopeService.archiveAll(documentId, kbId);

            // 4. 批量向量化入库（Spring AI 内部批量调 embedding 接口）
            List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("versionId", versionId);
                metadata.put("kbId", kbId);
                metadata.put("scope", scopeService.scopeOf(kbId, true));
                metadata.put("chunkIndex", i);
                metadata.put("fileName", doc.getFileName());
                aiDocs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
            }
            vectorStore.add(aiDocs);

            // 5. chunk 明文同步存关系表，供关键词检索（pg_trgm）用
            List<DocChunk> entities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocChunk entity = new DocChunk();
                entity.setDocumentId(documentId);
                entity.setVersionId(versionId);
                entity.setChunkIndex(i);
                entity.setContent(chunks.get(i));
                entity.setFileName(doc.getFileName());
                entities.add(entity);
            }
            docChunkRepository.saveAll(entities);

            // 6. 版本切换为当前：一次遍历把所有版本的状态写对，
            //    避免 detached 实体 merge 回去覆盖 isCurrent
            List<DocumentVersion> allVersions =
                    versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId);
            for (DocumentVersion v : allVersions) {
                boolean current = v.getId().equals(versionId);
                v.setIsCurrent(current);
                if (current) {
                    v.setStatus("DONE");
                    v.setChunkCount(chunks.size());
                }
                versionRepository.save(v);
            }

            doc.setStatus("DONE");
            doc.setChunkCount(chunks.size());
            doc.setSha256(version.getSha256());
            doc.setFileSize(version.getFileSize());
            documentRepository.save(doc);
            log.info("文档 {} v{} 向量化完成，共 {} 个 chunk", doc.getFileName(),
                    version.getVersionNo(), chunks.size());
        } catch (Exception e) {
            log.error("文档 {} v{} 处理失败", doc.getFileName(), version.getVersionNo(), e);
            version.setStatus("FAILED");
            version.setErrorMsg(e.getMessage());
            versionRepository.save(version);
            doc.setStatus("FAILED");
            doc.setErrorMsg(e.getMessage());
            documentRepository.save(doc);
        }
    }
}
