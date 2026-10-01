package com.ppday.rag.controller;

import com.ppday.rag.common.Result;
import com.ppday.rag.dto.DocumentVO;
import com.ppday.rag.dto.UploadResult;
import com.ppday.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /** 上传文档：立即返回 documentId，向量化在后台异步执行 */
    @PostMapping("/upload")
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(documentService.upload(file));
    }

    @GetMapping
    public Result<List<DocumentVO>> list() {
        return Result.ok(documentService.list());
    }

    /** 前端轮询解析进度 */
    @GetMapping("/{id}/progress")
    public Result<DocumentVO> progress(@PathVariable Long id) {
        return Result.ok(documentService.progress(id));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }
}
