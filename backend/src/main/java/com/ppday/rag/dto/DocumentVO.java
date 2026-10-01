package com.ppday.rag.dto;

import lombok.Data;

@Data
public class DocumentVO {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private String createTime;
}
