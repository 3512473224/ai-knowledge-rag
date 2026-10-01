package com.ppday.rag.service;

import com.ppday.rag.common.BusinessException;
import com.ppday.rag.dto.DocumentVO;
import com.ppday.rag.dto.UploadResult;
import com.ppday.rag.entity.Document;
import com.ppday.rag.repository.DocChunkRepository;
import com.ppday.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocumentRepository documentRepository;
    private final DocChunkRepository docChunkRepository;
    private final VectorStore vectorStore;
    private final DocumentIndexService indexService;

    private static final Map<String, String> EXT_TO_TYPE = Map.of(
            "pdf", "pdf", "docx", "docx", "doc", "docx",
            "txt", "txt", "md", "md", "markdown", "md");

    @Transactional
    public UploadResult upload(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String fileType = EXT_TO_TYPE.get(ext);
        if (fileType == null) {
            throw new BusinessException("仅支持 pdf / docx / txt / md 格式");
        }

        byte[] bytes = file.getBytes();
        String sha256 = DigestUtils.md5DigestAsHex(bytes); // 演示用 md5 指纹，生产换 sha256

        // 秒传：相同内容直接复用
        var existed = documentRepository.findBySha256(sha256);
        if (existed.isPresent() && "DONE".equals(existed.get().getStatus())) {
            return new UploadResult(existed.get().getId(), true);
        }

        Document doc = new Document();
        doc.setFileName(originalName);
        doc.setFileType(fileType);
        doc.setFileSize((long) bytes.length);
        doc.setSha256(sha256);
        doc.setStatus("PROCESSING");
        doc = documentRepository.save(doc);

        // 异步：解析 -> 切分 -> 向量化入库，接口立即返回
        // （走独立 Bean 的 @Async 方法，避免自调用导致异步失效）
        indexService.indexAsync(doc.getId(), bytes);

        return new UploadResult(doc.getId(), false);
    }

    public List<DocumentVO> list() {
        return documentRepository.findAll().stream()
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        // 同步删除向量库中的 chunk，避免脏数据被继续召回
        vectorStore.delete(new FilterExpressionBuilder().eq("documentId", id).build());
        // 同步删除关系表中的 chunk 明文（关键词检索用）
        docChunkRepository.deleteByDocumentId(id);
        documentRepository.delete(doc);
    }

    public DocumentVO progress(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        return toVO(doc);
    }

    private DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreateTime(doc.getCreateTime() == null ? "" : doc.getCreateTime().format(FMT));
        return vo;
    }
}
