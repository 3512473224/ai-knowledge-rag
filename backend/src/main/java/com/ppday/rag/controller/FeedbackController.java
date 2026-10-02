package com.ppday.rag.controller;

import com.ppday.rag.common.Result;
import com.ppday.rag.dto.FeedbackVO;
import com.ppday.rag.service.FeedbackService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** 对某条 AI 回答提交有用/无用反馈 */
    @PostMapping("/messages/{messageId}")
    public Result<FeedbackVO> submit(@PathVariable Long messageId,
                                     @RequestBody FeedbackRequest req) {
        return Result.ok(feedbackService.submit(messageId, req.getUseful(), req.getNote()));
    }

    /** 待优化看板：status=open 待处理，fixed 已处理 */
    @GetMapping
    public Result<List<FeedbackVO>> list(@RequestParam(defaultValue = "open") String status) {
        return Result.ok(feedbackService.list(status));
    }

    /** 标记反馈为已处理 / 重新打开 */
    @PostMapping("/{id}/resolve")
    public Result<FeedbackVO> resolve(@PathVariable Long id, @RequestBody ResolveRequest req) {
        return Result.ok(feedbackService.resolve(id, req.getStatus()));
    }

    @Data
    public static class FeedbackRequest {
        private Boolean useful;
        private String note;
    }

    @Data
    public static class ResolveRequest {
        private String status;
    }
}
