package com.ppday.rag.controller;

import com.ppday.rag.common.Result;
import com.ppday.rag.dto.KnowledgeBaseVO;
import com.ppday.rag.service.KnowledgeBaseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @GetMapping
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.ok(kbService.list());
    }

    @PostMapping
    public Result<KnowledgeBaseVO> create(@RequestBody KbRequest req) {
        return Result.ok(kbService.create(req.getName(), req.getDescription(), req.getVisibility()));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseVO> update(@PathVariable Long id, @RequestBody KbRequest req) {
        return Result.ok(kbService.update(id, req.getName(), req.getDescription(), req.getVisibility()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Result.ok();
    }

    @Data
    public static class KbRequest {
        private String name;
        private String description;
        private String visibility;
    }
}
