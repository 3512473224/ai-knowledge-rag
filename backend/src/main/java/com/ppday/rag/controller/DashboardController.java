package com.ppday.rag.controller;

import com.ppday.rag.common.Result;
import com.ppday.rag.dto.DashboardStatsVO;
import com.ppday.rag.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 数据看板：kbId 为空时统计全部知识库 */
    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats(@RequestParam(required = false) Long kbId) {
        return Result.ok(dashboardService.stats(kbId));
    }

    /** 最近一次评估结果（eval/last_result.json），没有则返回 null */
    @GetMapping("/eval")
    public Result<Object> eval() {
        return Result.ok(dashboardService.readEvalResult());
    }
}
