package com.ppday.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocumentDetailVO {
    private Long id;
    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private String createTime;
    private List<DocumentVersionVO> versions;
}
