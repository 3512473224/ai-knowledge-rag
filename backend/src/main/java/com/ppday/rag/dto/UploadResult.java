package com.ppday.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    /** 文档 ID，前端用它轮询解析进度 */
    private Long documentId;
    /** 是否秒传（已存在相同 sha256 的文档） */
    private boolean deduped;
}
