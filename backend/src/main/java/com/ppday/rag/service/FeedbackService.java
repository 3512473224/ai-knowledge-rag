package com.ppday.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppday.rag.common.BusinessException;
import com.ppday.rag.dto.FeedbackVO;
import com.ppday.rag.entity.ChatMessage;
import com.ppday.rag.entity.MessageFeedback;
import com.ppday.rag.repository.ChatMessageRepository;
import com.ppday.rag.repository.MessageFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 反馈闭环：用户对 AI 回答点"有用/无用"，无用可备注原因，
 * 进入待优化看板。知识库运营者在这里看到"哪些问题答得不好"，
 * 针对性补充文档——这就是知识库持续运营的抓手。
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<Map<String, Object>>> SOURCE_LIST =
            new TypeReference<>() {
            };

    private final MessageFeedbackRepository feedbackRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交反馈：一条回答只保留一条反馈记录，重复提交走更新；
     * 重新提交时状态重置为 open（之前标 fixed 的结论作废）。
     */
    @Transactional
    public FeedbackVO submit(Long messageId, Boolean useful, String note) {
        if (useful == null) {
            throw new BusinessException("请给出有用/无用评价");
        }
        ChatMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("消息不存在"));
        if (!"assistant".equals(msg.getRole())) {
            throw new BusinessException("只能对 AI 回答进行评价");
        }
        MessageFeedback fb = feedbackRepository.findByMessageId(messageId)
                .orElseGet(MessageFeedback::new);
        fb.setMessageId(messageId);
        fb.setUseful(useful);
        fb.setNote(note);
        fb.setStatus("open");
        return toVO(feedbackRepository.save(fb));
    }

    public List<FeedbackVO> list(String status) {
        String s = (status == null || status.isEmpty()) ? "open" : status;
        if (!"open".equals(s) && !"fixed".equals(s)) {
            throw new BusinessException("状态只能是 open 或 fixed");
        }
        return feedbackRepository.findByStatusOrderByCreateTimeDesc(s)
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public FeedbackVO resolve(Long id, String status) {
        if (!"open".equals(status) && !"fixed".equals(status)) {
            throw new BusinessException("状态只能是 open 或 fixed");
        }
        MessageFeedback fb = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException("反馈不存在"));
        fb.setStatus(status);
        return toVO(feedbackRepository.save(fb));
    }

    private FeedbackVO toVO(MessageFeedback fb) {
        FeedbackVO vo = new FeedbackVO();
        vo.setId(fb.getId());
        vo.setMessageId(fb.getMessageId());
        vo.setUseful(fb.getUseful());
        vo.setNote(fb.getNote());
        vo.setStatus(fb.getStatus());
        vo.setCreateTime(fb.getCreateTime() == null ? "" : fb.getCreateTime().format(FMT));

        ChatMessage answer = messageRepository.findById(fb.getMessageId()).orElse(null);
        if (answer != null) {
            vo.setSessionId(answer.getSessionId());
            String content = answer.getContent() == null ? "" : answer.getContent();
            vo.setAnswerPreview(content.length() > 200 ? content.substring(0, 200) + "..." : content);
            vo.setQuestion(findQuestion(answer));
            vo.setFileNames(extractFileNames(answer.getSources()));
        }
        return vo;
    }

    /** 找到同一会话里这条回答之前最近的一条用户提问 */
    private String findQuestion(ChatMessage answer) {
        String q = "";
        for (ChatMessage m : messageRepository.findBySessionIdOrderByCreateTimeAsc(answer.getSessionId())) {
            if (m.getId().equals(answer.getId())) {
                break;
            }
            if ("user".equals(m.getRole())) {
                q = m.getContent();
            }
        }
        return q;
    }

    private List<String> extractFileNames(String sourcesJson) {
        List<String> names = new ArrayList<>();
        if (sourcesJson == null || sourcesJson.isEmpty()) {
            return names;
        }
        try {
            for (Map<String, Object> s : objectMapper.readValue(sourcesJson, SOURCE_LIST)) {
                Object name = s.get("fileName");
                if (name != null) {
                    names.add(String.valueOf(name));
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }
}
