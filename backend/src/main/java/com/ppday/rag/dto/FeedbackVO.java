package com.ppday.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class FeedbackVO {
    private Long id;
    private Long messageId;
    private Long sessionId;
    /** 提问原文 */
    private String question;
    /** 回答摘要（前 200 字） */
    private String answerPreview;
    private Boolean useful;
    /** 无用原因备注 */
    private String note;
    /** open / fixed */
    private String status;
    /** 回答引用的文档名 */
    private List<String> fileNames;
    private String createTime;
}
