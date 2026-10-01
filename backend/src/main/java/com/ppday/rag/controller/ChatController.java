package com.ppday.rag.controller;

import com.ppday.rag.common.Result;
import com.ppday.rag.entity.ChatMessage;
import com.ppday.rag.entity.ChatSession;
import com.ppday.rag.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式问答（SSE）。sessionId 为空时自动创建新会话。
     * 事件：token（文本增量）/ sources（引用来源 JSON）/ done（会话 id）
     */
    @PostMapping("/stream")
    public SseEmitter stream(@RequestParam(required = false) Long sessionId,
                             @RequestParam String question) {
        return chatService.streamChat(sessionId, question);
    }

    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions() {
        return Result.ok(chatService.listSessions());
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        return Result.ok(chatService.listMessages(id));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<?> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id);
        return Result.ok();
    }
}
