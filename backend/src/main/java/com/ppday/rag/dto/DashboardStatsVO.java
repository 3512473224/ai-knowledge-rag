package com.ppday.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashboardStatsVO {
    /** 用户提问总数 */
    private long questionCount;
    /** 无答案率 = 拒答数 / AI 回答数 */
    private double refusalRate;
    /** 热门问题 Top10 */
    private List<TopQuestion> topQuestions;
    /** 反馈好评率 = 有用 / 反馈总数（无反馈时为 null） */
    private Double feedbackRate;
    private long feedbackTotal;
    private long docCount;
    private long chunkCount;
    /** 最近一次评估结果（eval/last_result.json），没有则为 null */
    private Object evalResult;

    @Data
    public static class TopQuestion {
        private String question;
        private long count;

        public TopQuestion(String question, long count) {
            this.question = question;
            this.count = count;
        }
    }
}
