package com.ppday.rag.rag;

import com.ppday.rag.config.RagProperties;
import com.ppday.rag.repository.DocChunkRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索 Hybrid Search（面试深挖点 No.1）：
 *
 *   向量检索（语义）              关键词检索（精确）
 *   topK=8, 阈值 0.70 过滤   +   pg_trgm 三字符相似度 topK
 *                    \              /
 *                     RRF 融合：score = Σ 1/(60 + rank)
 *                             ↓
 *                    去重 → 取 finalTopK → prompt
 *
 * 为什么融合：纯向量检索会被语义相近但关键信息错误的 chunk 骗过，
 * 经典 case——用户问"去年 Q3 财报"，向量可能召回"今年 Q3 财报"
 * （语义高度相似、时间却错了）；关键词检索对"去年/Q3"这类字面
 * 约束是精确匹配，两路融合后正确 chunk 排名显著上升。
 */
@Component
@RequiredArgsConstructor
public class RetrievalService {

    /** RRF 常数，业界常用 60 */
    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final DocChunkRepository docChunkRepository;
    private final RagProperties properties;

    public List<Hit> retrieve(String question) {
        RagProperties.Retrieval cfg = properties.getRetrieval();

        // ---- 第一路：向量检索（语义）----
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(cfg.getTopK())
                .similarityThreshold(cfg.getSimilarityThreshold())
                .build();
        List<Document> vecDocs = vectorStore.similaritySearch(request);

        // ---- 第二路：关键词检索（字面精确）----
        List<DocChunkRepository.ChunkSimRow> kwRows =
                docChunkRepository.keywordSearch(question, cfg.getTopK());

        // ---- RRF 融合 ----
        Map<String, FusedHit> fused = new LinkedHashMap<>();
        for (int i = 0; i < vecDocs.size(); i++) {
            Document doc = vecDocs.get(i);
            String key = keyOf(doc.getMetadata().get("documentId"), doc.getMetadata().get("chunkIndex"));
            FusedHit hit = fused.computeIfAbsent(key, k -> fromVectorDoc(doc));
            hit.rrfScore += 1.0 / (RRF_K + i + 1);
            hit.vectorRank = i + 1;
        }
        for (int j = 0; j < kwRows.size(); j++) {
            DocChunkRepository.ChunkSimRow row = kwRows.get(j);
            String key = keyOf(row.getDocumentId(), row.getChunkIndex());
            FusedHit hit = fused.computeIfAbsent(key, k -> fromKeywordRow(row));
            hit.rrfScore += 1.0 / (RRF_K + j + 1);
            hit.keywordRank = j + 1;
        }

        List<FusedHit> ranked = new ArrayList<>(fused.values());
        ranked.sort((a, b) -> Double.compare(b.rrfScore, a.rrfScore));
        return ranked.stream()
                .limit(cfg.getFinalTopK())
                .map(f -> new Hit(f.documentId, f.chunkIndex, f.fileName, f.content,
                        Math.round(f.rrfScore * 10000.0) / 10000.0, f.matchType()))
                .toList();
    }

    private String keyOf(Object documentId, Object chunkIndex) {
        return documentId + ":" + chunkIndex;
    }

    private FusedHit fromVectorDoc(Document doc) {
        FusedHit h = new FusedHit();
        h.documentId = Long.valueOf(doc.getMetadata().get("documentId").toString());
        h.chunkIndex = Integer.parseInt(doc.getMetadata().get("chunkIndex").toString());
        h.fileName = String.valueOf(doc.getMetadata().getOrDefault("fileName", "未知文档"));
        h.content = doc.getText();
        return h;
    }

    private FusedHit fromKeywordRow(DocChunkRepository.ChunkSimRow row) {
        FusedHit h = new FusedHit();
        h.documentId = row.getDocumentId();
        h.chunkIndex = row.getChunkIndex();
        h.fileName = row.getFileName();
        h.content = row.getContent();
        return h;
    }

    private static class FusedHit {
        Long documentId;
        int chunkIndex;
        String fileName;
        String content;
        double rrfScore = 0;
        int vectorRank = -1;
        int keywordRank = -1;

        String matchType() {
            if (vectorRank > 0 && keywordRank > 0) return "向量+关键词";
            return vectorRank > 0 ? "向量" : "关键词";
        }
    }

    @Data
    public static class Hit {
        private final Long documentId;
        private final int chunkIndex;
        private final String fileName;
        private final String content;
        /** RRF 融合分，前端展示用 */
        private final double score;
        /** 命中来源：向量 / 关键词 / 向量+关键词 */
        private final String matchType;
    }
}
