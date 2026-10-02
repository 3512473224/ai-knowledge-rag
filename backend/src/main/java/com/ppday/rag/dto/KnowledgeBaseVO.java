package com.ppday.rag.dto;

import lombok.Data;

@Data
public class KnowledgeBaseVO {
    private Long id;
    private String name;
    private String description;
    private String visibility;
    private Integer docCount;
    private Integer questionCount;
    private String createTime;
}
