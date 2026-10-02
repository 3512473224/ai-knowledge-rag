package com.ppday.rag.dto;

import lombok.Data;

@Data
public class DocumentVersionVO {
    private Long id;
    private Integer versionNo;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private String status;
    private String errorMsg;
    private Boolean isCurrent;
    private String createTime;
}
