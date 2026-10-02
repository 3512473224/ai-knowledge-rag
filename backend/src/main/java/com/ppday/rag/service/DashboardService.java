package com.ppday.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppday.rag.dto.DashboardStatsVO;
import com.ppday.rag.entity.ChatSession;
import com.ppday.rag.entity.MessageFeedback;
import com.ppday.rag.repository.ChatMessageRepository;
import com.ppday.rag.repository.ChatSessionRepository;
import com.ppday.rag.repository.DocChunkRepository;
import com.ppday.rag.repository.DocumentRepository;
import com.ppday.rag.repository.MessageFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据看板：回答运营者"知识库用得怎么样"——
 * 问了多少、无答案率多高、哪些问题被反复问、用户满不满意。
 * 评估结果（eval/last_result.json）也接进来，技术指标和业务指标同屏看。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** eval.py 跑完把结果写到这里，看板直接读 */
    private static final Path EVAL_RESULT = Paths.get("eval", "last_result.json");

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashboardStatsVO stats(Long kbId) {
        List<ChatSession> sessions = kbId == null
                ? sessionRepository.findAll()
                : sessionRepository.findByKbIdOrderByUpdateTimeDesc(kbId);
        List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();

        DashboardStatsVO vo = new DashboardStatsVO();
        if (sessionIds.isEmpty()) {
            vo.setQuestionCount(0);
            vo.setRefusalRate(0);
            vo.setTopQuestions(List.of());
            vo.setFeedbackTotal(0);
        } else {
            long questions = messageRepository.countBySessionIdInAndRole(sessionIds, "user");
            long answers = messageRepository.countBySessionIdInAndRole(sessionIds, "assistant");
            long refused = messageRepository.countBySessionIdInAndRoleAndRefusedTrue(sessionIds, "assistant");
            vo.setQuestionCount(questions);
            vo.setRefusalRate(answers == 0 ? 0
                    : Math.round((double) refused / answers * 1000.0) / 10.0);

            List<DashboardStatsVO.TopQuestion> top = new ArrayList<>();
            for (Object[] row : messageRepository.topQuestions(sessionIds, PageRequest.of(0, 10))) {
                String q = String.valueOf(row[0]);
                top.add(new DashboardStatsVO.TopQuestion(
                        q.length() > 60 ? q.substring(0, 60) + "..." : q,
                        ((Number) row[1]).longValue()));
            }
            vo.setTopQuestions(top);

            // 反馈好评率：只统计本知识库会话里的反馈
            Set<Long> idSet = Set.copyOf(sessionIds);
            List<MessageFeedback> fbs = feedbackRepository.findAll().stream()
                    .filter(fb -> messageRepository.findById(fb.getMessageId())
                            .map(m -> idSet.contains(m.getSessionId())).orElse(false))
                    .toList();
            vo.setFeedbackTotal(fbs.size());
            if (!fbs.isEmpty()) {
                long useful = fbs.stream().filter(f -> Boolean.TRUE.equals(f.getUseful())).count();
                vo.setFeedbackRate(Math.round((double) useful / fbs.size() * 1000.0) / 10.0);
            }
        }

        vo.setDocCount(kbId == null ? documentRepository.count() : documentRepository.countByKbId(kbId));
        vo.setChunkCount(chunkRepository.count());
        vo.setEvalResult(readEvalResult());
        return vo;
    }

    /**
     * 最近一次评估结果。文件由 eval.py 生成，不存在时返回 null，
     * 前端据此展示"暂无评估数据，去跑一轮"的引导态。
     */
    public Object readEvalResult() {
        try {
            if (!Files.exists(EVAL_RESULT)) {
                return null;
            }
            return objectMapper.readValue(Files.readString(EVAL_RESULT), Map.class);
        } catch (Exception e) {
            log.warn("读取评估结果失败", e);
            return null;
        }
    }
}
