package com.ppday.rag.controller;

import com.ppday.rag.common.Result;
import com.ppday.rag.dto.DocumentDetailVO;
import com.ppday.rag.dto.DocumentVO;
import com.ppday.rag.dto.UploadResult;
import com.ppday.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档：kbId 必填。同名文件重复上传会自动追加新版本，
     * 内容完全一致则秒传。立即返回 documentId，向量化在后台异步执行。
     */
    @PostMapping("/upload")
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam("kbId") Long kbId) throws Exception {
        return Result.ok(documentService.upload(file, kbId));
    }

    @GetMapping
    public Result<List<DocumentVO>> list(@RequestParam(required = false) Long kbId) {
        return Result.ok(documentService.list(kbId));
    }

    @GetMapping("/{id}")
    public Result<DocumentDetailVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    /** 前端轮询解析进度 */
    @GetMapping("/{id}/progress")
    public Result<DocumentVO> progress(@PathVariable Long id) {
        return Result.ok(documentService.progress(id));
    }

    /** 版本回滚：把指定版本设为当前，瞬时生效 */
    @PostMapping("/{id}/versions/{versionId}/activate")
    public Result<?> activate(@PathVariable Long id, @PathVariable Long versionId) {
        documentService.activateVersion(id, versionId);
        return Result.ok();
    }

    /**
     * 原文件在线预览：pdf 走浏览器原生渲染，
     * docx 浏览器打不开，前端据此降级为下载按钮。
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        DocumentService.FileResource fr = documentService.fileForPreview(id);
        MediaType mediaType = switch (fr.fileType()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "md", "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        String encoded = URLEncoder.encode(fr.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .body(new ByteArrayResource(fr.bytes()));
    }

    /** txt/md 在线预览：当前版本全文（chunk 按序拼接） */
    @GetMapping(value = "/{id}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.textForPreview(id));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }
}
