package com.ppday.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();
    private Chat chat = new Chat();

    @Data
    public static class Chunk {
        private int maxChars = 800;
        private int overlapChars = 120;
    }

    @Data
    public static class Retrieval {
        private int topK = 8;
        private double similarityThreshold = 0.70;
        private int finalTopK = 5;
    }

    @Data
    public static class Chat {
        private long sseTimeoutMs = 600000;
        private int maxQuestionChars = 2000;
    }
}
