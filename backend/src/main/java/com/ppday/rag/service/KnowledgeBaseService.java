package com.ppday.rag.service;

import com.ppday.rag.common.BusinessException;
import com.ppday.rag.dto.KnowledgeBaseVO;
import com.ppday.rag.entity.ChatSession;
import com.ppday.rag.entity.KnowledgeBase;
import com.ppday.rag.repository.ChatMessageRepository;
import com.ppday.rag.repository.ChatSessionRepository;
import com.ppday.rag.repository.DocumentRepository;
import com.ppday.rag.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KnowledgeBaseRepository kbRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentService documentService;

    public List<KnowledgeBaseVO> list() {
        return kbRepository.findAll().stream()
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public KnowledgeBaseVO create(String name, String description, String visibility) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("知识库名称不能为空");
        }
        if (!"public".equals(visibility) && !"private".equals(visibility)) {
            throw new BusinessException("可见性只能是 public 或 private");
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name.trim());
        kb.setDescription(description);
        kb.setVisibility(visibility);
        return toVO(kbRepository.save(kb));
    }

    @Transactional
    public KnowledgeBaseVO update(Long id, String name, String description, String visibility) {
        KnowledgeBase kb = kbRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        if (name != null && !name.trim().isEmpty()) {
            kb.setName(name.trim());
        }
        if (description != null) {
            kb.setDescription(description);
        }
        if (visibility != null) {
            if (!"public".equals(visibility) && !"private".equals(visibility)) {
                throw new BusinessException("可见性只能是 public 或 private");
            }
            kb.setVisibility(visibility);
        }
        return toVO(kbRepository.save(kb));
    }

    /**
     * 删除知识库：级联删除名下所有文档（文档删除会同步清理向量与 chunk），
     * 再删除该库的问答会话。不可逆，调用方需二次确认。
     */
    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = kbRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        documentRepository.findByKbIdOrderByCreateTimeDesc(id)
                .forEach(doc -> documentService.delete(doc.getId()));
        sessionRepository.findByKbIdOrderByUpdateTimeDesc(id)
                .forEach(s -> {
                    messageRepository.findBySessionIdOrderByCreateTimeAsc(s.getId())
                            .forEach(messageRepository::delete);
                    sessionRepository.delete(s);
                });
        kbRepository.delete(kb);
    }

    public KnowledgeBase get(Long id) {
        return kbRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setVisibility(kb.getVisibility());
        vo.setDocCount((int) documentRepository.countByKbId(kb.getId()));
        List<ChatSession> sessions = sessionRepository.findByKbIdOrderByUpdateTimeDesc(kb.getId());
        List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();
        vo.setQuestionCount(sessionIds.isEmpty() ? 0
                : (int) messageRepository.countBySessionIdInAndRole(sessionIds, "user"));
        vo.setCreateTime(kb.getCreateTime() == null ? "" : kb.getCreateTime().format(FMT));
        return vo;
    }
}
