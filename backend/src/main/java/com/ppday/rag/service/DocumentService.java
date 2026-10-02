package com.ppday.rag.service;

import com.ppday.rag.common.BusinessException;
import com.ppday.rag.dto.DocumentDetailVO;
import com.ppday.rag.dto.DocumentVO;
import com.ppday.rag.dto.DocumentVersionVO;
import com.ppday.rag.dto.UploadResult;
import com.ppday.rag.entity.DocChunk;
import com.ppday.rag.entity.Document;
import com.ppday.rag.entity.DocumentVersion;
import com.ppday.rag.repository.DocChunkRepository;
import com.ppday.rag.repository.DocumentRepository;
import com.ppday.rag.repository.DocumentVersionRepository;
import com.ppday.rag.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档服务：上传（自动版本管理）、版本回滚、删除、在线预览。
 *
 * 版本规则：同一知识库内同名文件重复上传 → 追加新版本并重新向量化，
 * 而不是新建文档行。检索永远只查 isCurrent=true 的版本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 上传原文件落盘目录：data/docs/{versionId}.bin，供 pdf 在线预览用 */
    private static final Path STORAGE_DIR = Paths.get("data", "docs");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocChunkRepository docChunkRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final VectorStore vectorStore;
    private final VectorScopeService scopeService;
    private final DocumentIndexService indexService;

    private static final Map<String, String> EXT_TO_TYPE = Map.of(
            "pdf", "pdf", "docx", "docx", "doc", "docx",
            "txt", "txt", "md", "md", "markdown", "md");

    @Transactional
    public UploadResult upload(MultipartFile file, Long kbId) throws Exception {
        if (kbId == null || !kbRepository.existsById(kbId)) {
            throw new BusinessException("请先选择知识库");
        }
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
        String sha256 = DigestUtils.md5DigestAsHex(bytes);

        Document doc = documentRepository.findByKbIdAndFileName(kbId, originalName).orElse(null);
        if (doc == null) {
            doc = new Document();
            doc.setKbId(kbId);
            doc.setFileName(originalName);
            doc.setFileType(fileType);
            doc.setStatus("PROCESSING");
            doc = documentRepository.save(doc);
            return startNewVersion(doc, bytes, sha256, fileType, 1);
        }

        // 已有同名文档：内容完全一致则秒传，否则追加新版本
        DocumentVersion current = versionRepository.findByDocumentIdAndIsCurrentTrue(doc.getId())
                .orElse(null);
        if (current != null && sha256.equals(current.getSha256())
                && "DONE".equals(current.getStatus())) {
            return new UploadResult(doc.getId(), true);
        }
        int nextNo = versionRepository.findByDocumentIdOrderByVersionNoDesc(doc.getId())
                .stream().mapToInt(DocumentVersion::getVersionNo).max().orElse(0) + 1;
        doc.setStatus("PROCESSING");
        documentRepository.save(doc);
        return startNewVersion(doc, bytes, sha256, fileType, nextNo);
    }

    private UploadResult startNewVersion(Document doc, byte[] bytes, String sha256,
                                         String fileType, int versionNo) throws IOException {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setVersionNo(versionNo);
        version.setFileName(doc.getFileName());
        version.setFileType(fileType);
        version.setFileSize((long) bytes.length);
        version.setSha256(sha256);
        version.setStatus("PROCESSING");
        version.setIsCurrent(false);
        version = versionRepository.save(version);

        // 原文件落盘：pdf 在线预览直接读这个文件
        Files.createDirectories(STORAGE_DIR);
        Files.write(storagePath(version.getId()), bytes);

        // 异步：解析 -> 切分 -> 向量化入库，接口立即返回
        // （走独立 Bean 的 @Async 方法，避免自调用导致异步失效）
        indexService.indexAsync(doc.getId(), version.getId(), doc.getKbId(), bytes);

        return new UploadResult(doc.getId(), false);
    }

    public List<DocumentVO> list(Long kbId) {
        List<Document> docs = kbId == null
                ? documentRepository.findAll()
                : documentRepository.findByKbIdOrderByCreateTimeDesc(kbId);
        Map<Long, Integer> currentNos = docs.stream().collect(Collectors.toMap(
                Document::getId,
                d -> versionRepository.findByDocumentIdAndIsCurrentTrue(d.getId())
                        .map(DocumentVersion::getVersionNo).orElse(null),
                (a, b) -> a));
        return docs.stream()
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .map(d -> toVO(d, currentNos.get(d.getId())))
                .toList();
    }

    public DocumentDetailVO detail(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        DocumentDetailVO vo = new DocumentDetailVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreateTime(doc.getCreateTime() == null ? "" : doc.getCreateTime().format(FMT));
        vo.setVersions(versionRepository.findByDocumentIdOrderByVersionNoDesc(id)
                .stream().map(this::toVersionVO).toList());
        return vo;
    }

    /**
     * 版本回滚：把旧版本设为当前。只翻转 DB 的 isCurrent 和向量的 scope，
     * 不需要重新向量化，所以是瞬时操作。
     */
    @Transactional
    public void activateVersion(Long documentId, Long versionId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        DocumentVersion target = versionRepository.findById(versionId)
                .filter(v -> v.getDocumentId().equals(documentId))
                .orElseThrow(() -> new BusinessException("版本不存在"));
        if (!"DONE".equals(target.getStatus())) {
            throw new BusinessException("只有解析成功的版本才能设为当前");
        }
        versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId)
                .forEach(v -> {
                    v.setIsCurrent(v.getId().equals(versionId));
                    versionRepository.save(v);
                });
        // 向量元数据同步翻转：检索只查 scope={kbId}:current
        scopeService.setCurrent(documentId, doc.getKbId(), versionId);

        doc.setStatus("DONE");
        doc.setChunkCount(target.getChunkCount());
        doc.setSha256(target.getSha256());
        doc.setFileSize(target.getFileSize());
        doc.setErrorMsg(null);
        documentRepository.save(doc);
        log.info("文档 {} 回滚到 v{}", doc.getFileName(), target.getVersionNo());
    }

    /**
     * 删除文档：同步清理该文档所有版本的向量、chunk 明文、版本行和落盘文件，
     * 避免脏数据被继续召回。
     */
    @Transactional
    public void delete(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        // 向量库：按 documentId 删除名下所有版本向量
        vectorStore.delete(new FilterExpressionBuilder().eq("documentId", id).build());
        // 关系表 chunk 明文
        docChunkRepository.deleteByDocumentId(id);
        // 版本行 + 落盘文件
        versionRepository.findByDocumentIdOrderByVersionNoDesc(id).forEach(v -> {
            try {
                Files.deleteIfExists(storagePath(v.getId()));
            } catch (IOException e) {
                log.warn("删除落盘文件失败 versionId={}", v.getId(), e);
            }
            versionRepository.delete(v);
        });
        documentRepository.delete(doc);
    }

    public DocumentVO progress(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        Integer no = versionRepository.findByDocumentIdAndIsCurrentTrue(id)
                .map(DocumentVersion::getVersionNo).orElse(null);
        return toVO(doc, no);
    }

    /** 在线预览：返回当前版本的原文件字节（pdf 用 <embed> 直接渲染） */
    public FileResource fileForPreview(Long documentId) {
        DocumentVersion current = versionRepository.findByDocumentIdAndIsCurrentTrue(documentId)
                .orElseThrow(() -> new BusinessException("文档尚未解析完成"));
        Path path = storagePath(current.getId());
        if (!Files.exists(path)) {
            throw new BusinessException("原文件不存在");
        }
        try {
            return new FileResource(current.getFileName(), current.getFileType(),
                    Files.readAllBytes(path));
        } catch (IOException e) {
            throw new BusinessException("读取文件失败");
        }
    }

    /** txt/md 在线预览：把当前版本的 chunk 按序拼回全文 */
    public String textForPreview(Long documentId) {
        DocumentVersion current = versionRepository.findByDocumentIdAndIsCurrentTrue(documentId)
                .orElseThrow(() -> new BusinessException("文档尚未解析完成"));
        List<DocChunk> chunks = docChunkRepository.findByVersionIdOrderByChunkIndexAsc(current.getId());
        if (chunks.isEmpty()) {
            throw new BusinessException("文档内容为空");
        }
        return chunks.stream().map(DocChunk::getContent)
                .collect(Collectors.joining("\n\n"));
    }

    private Path storagePath(Long versionId) {
        return STORAGE_DIR.resolve(versionId + ".bin");
    }

    private DocumentVO toVO(Document doc, Integer currentVersionNo) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setCurrentVersionNo(currentVersionNo);
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreateTime(doc.getCreateTime() == null ? "" : doc.getCreateTime().format(FMT));
        return vo;
    }

    private DocumentVersionVO toVersionVO(DocumentVersion v) {
        DocumentVersionVO vo = new DocumentVersionVO();
        vo.setId(v.getId());
        vo.setVersionNo(v.getVersionNo());
        vo.setFileName(v.getFileName());
        vo.setFileType(v.getFileType());
        vo.setFileSize(v.getFileSize());
        vo.setChunkCount(v.getChunkCount());
        vo.setStatus(v.getStatus());
        vo.setErrorMsg(v.getErrorMsg());
        vo.setIsCurrent(v.getIsCurrent());
        vo.setCreateTime(v.getCreateTime() == null ? "" : v.getCreateTime().format(FMT));
        return vo;
    }

    /** 预览文件载体 */
    public record FileResource(String fileName, String fileType, byte[] bytes) {
    }
}
