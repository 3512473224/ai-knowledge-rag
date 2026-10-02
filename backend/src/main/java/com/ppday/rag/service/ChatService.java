package com.ppday.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppday.rag.common.BusinessException;
import com.ppday.rag.config.RagProperties;
import com.ppday.rag.entity.ChatMessage;
import com.ppday.rag.entity.ChatSession;
import com.ppday.rag.rag.RetrievalService;
import com.ppday.rag.repository.ChatMessageRepository;
import com.ppday.rag.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * RAG 问答链路：选知识库 -> 混合检索（限定库内当前版本）-> 组装 prompt ->
 * 大模型流式生成 -> SSE 推送。
 *
 * 防幻觉三件套：相似度阈值过滤 / 检索为空直接拒答（记 refused=true 供看板统计）/
 * prompt 强制"只基于资料回答并标注来源"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            你是一个严谨的企业知识库问答助手。回答必须严格基于，
            遵守以下规则：
            1. 只能使用参考资料中的信息作答，禁止编造、禁止用常识脑补；
            2. 如果参考资料无法回答问题，直接说"根据现有资料无法回答该问题"，不要猜测；
            3. 回答中引用了某条资料时，在句末标注来源，如 [来源1]、[来源2]；
            4. 回答简洁准确，使用 Markdown 格式。
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final RetrievalService retrievalService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final KnowledgeBaseService kbService;
    private final RagProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Executor sseExecutor = Executors.newCachedThreadPool();

    /**
     * SSE 事件：token（文本增量）/ sources（引用来源 JSON）/
     * messageId（AI 回答落库后的 id，前端挂"有用/无用"按钮用）/ done（会话 id）。
     */
    public SseEmitter streamChat(Long sessionId, Long kbId, String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        if (question.length() > properties.getChat().getMaxQuestionChars()) {
            throw new BusinessException("问题过长，请控制在 "
                    + properties.getChat().getMaxQuestionChars() + " 字以内");
        }

        ChatSession session = resolveSession(sessionId, kbId, question);
        Long effectiveKbId = session.getKbId();
        saveMessage(session.getId(), "user", question, null, false);

        // 先检索，再决定 prompt：无命中直接拒答，不浪费大模型调用
        List<RetrievalService.Hit> hits = retrievalService.retrieve(question, effectiveKbId);

        SseEmitter emitter = new SseEmitter(properties.getChat().getSseTimeoutMs());
        sseExecutor.execute(() -> {
            StringBuilder answer = new StringBuilder();
            try {
                boolean refused = hits.isEmpty();
                if (refused) {
                    String refuse = "根据现有资料无法回答该问题，请换个问法或上传相关文档后再试。";
                    emitter.send(SseEmitter.event().name("token").data(refuse));
                    answer.append(refuse);
                } else {
                    String userPrompt = buildUserPrompt(question, hits);
                    chatClientBuilder.build()
                            .prompt()
                            .system(SYSTEM_PROMPT)
                            .user(userPrompt)
                            .stream()
                            .content()
                            .doOnNext(token -> {
                                answer.append(token);
                                try {
                                    emitter.send(SseEmitter.event().name("token").data(token));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .doOnError(emitter::completeWithError)
                            .doOnComplete(() -> {
                                try {
                                    String sourcesJson = objectMapper.writeValueAsString(toSources(hits));
                                    emitter.send(SseEmitter.event().name("sources").data(sourcesJson));
                                } catch (Exception ignored) {
                                }
                            })
                            .blockLast();
                }
                ChatMessage saved = saveMessage(session.getId(), "assistant",
                        answer.toString(), toSourcesJson(hits), refused);
                emitter.send(SseEmitter.event().name("messageId").data(saved.getId().toString()));
                emitter.send(SseEmitter.event().name("done").data(session.getId().toString()));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答异常", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private String buildUserPrompt(String question, List<RetrievalService.Hit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (int i = 0; i < hits.size(); i++) {
            RetrievalService.Hit hit = hits.get(i);
            sb.append("[来源").append(i + 1).append("]《")
                    .append(hit.getFileName()).append("》\n")
                    .append(hit.getContent()).append("\n\n");
        }
        sb.append("\n").append(question);
        return sb.toString();
    }

    private List<Map<String, Object>> toSources(List<RetrievalService.Hit> hits) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            RetrievalService.Hit hit = hits.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i + 1);
            m.put("documentId", hit.getDocumentId());
            m.put("fileName", hit.getFileName());
            m.put("score", hit.getScore());
            m.put("matchType", hit.getMatchType());
            m.put("preview", hit.getContent().length() > 120
                    ? hit.getContent().substring(0, 120) + "..."
                    : hit.getContent());
            sources.add(m);
        }
        return sources;
    }

    private String toSourcesJson(List<RetrievalService.Hit> hits) {
        try {
            return objectMapper.writeValueAsString(toSources(hits));
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 会话归属知识库：老会话没有 kbId 时，用本次传入的 kbId 回填；
     * 两边都有但不一致时，以会话创建时的为准（防止串库）。
     */
    @Transactional
    public ChatSession resolveSession(Long sessionId, Long kbId, String question) {
        if (sessionId != null) {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException("会话不存在"));
            if (session.getKbId() == null) {
                if (kbId == null) {
                    throw new BusinessException("请先选择知识库");
                }
                kbService.get(kbId);
                session.setKbId(kbId);
                sessionRepository.save(session);
            }
            return session;
        }
        if (kbId == null) {
            throw new BusinessException("请先选择知识库");
        }
        kbService.get(kbId);
        ChatSession session = new ChatSession();
        String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
        session.setTitle(title);
        session.setKbId(kbId);
        session.setUpdateTime(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public ChatMessage saveMessage(Long sessionId, String role, String content,
                                   String sources, boolean refused) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSources(sources);
        msg.setRefused(refused);
        ChatMessage saved = messageRepository.save(msg);
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setUpdateTime(LocalDateTime.now());
            sessionRepository.save(s);
        });
        return saved;
    }

    public List<ChatSession> listSessions(Long kbId, String keyword) {
        String kw = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return sessionRepository.search(kbId, kw);
    }

    public List<ChatMessage> listMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId)
                .forEach(messageRepository::delete);
        sessionRepository.deleteById(sessionId);
    }
}
