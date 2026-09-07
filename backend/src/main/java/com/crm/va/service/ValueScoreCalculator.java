package com.crm.va.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 客户价值五维评分计算器（VA-1，纯函数可单测）
 * 五维加权(0-100)：交易频率30% + 累计交易额30% + 最近活跃20% + 汇款及时性10% + 跟进响应度10%
 * 分层：高价值(≥80) / 潜力(60-79) / 待激活(40-59) / 流失风险(<40)
 * 权重来自 va_rule 表可配置；维度归一化口径见各方法注释
 */
public final class ValueScoreCalculator {

    /** 权重（va_rule 可配） */
    public record Weights(double freq, double amount, double active, double remit, double followup) {
    }

    /** 原始指标（聚合 SQL 产出） */
    public record RawMetrics(long orderCount90d, BigDecimal totalAmount, Integer daysSinceLastActivity,
                             BigDecimal confirmedRemit, long followupCount90d) {
    }

    /** 计算结果：总分 + 五维分项 + 分层 */
    public record Result(double score, String tier, double dimFreq, double dimAmount,
                         double dimActive, double dimRemit, double dimFollowup) {
    }

    private ValueScoreCalculator() {
    }

    public static Result compute(RawMetrics m, Weights w) {
        double amountVal = m.totalAmount() == null ? 0 : m.totalAmount().doubleValue();
        double remitVal = m.confirmedRemit() == null ? 0 : m.confirmedRemit().doubleValue();

        // 交易频率：近90天订单数，10 单封顶
        double dimFreq = cap(m.orderCount90d() / 10.0) * 100;
        // 累计交易额：50 万封顶
        double dimAmount = cap(amountVal / 500000.0) * 100;
        // 最近活跃：距最近一次交易/跟进天数，60 天线性衰减
        double dimActive = m.daysSinceLastActivity() == null ? 0
                : Math.max(0, 100 - m.daysSinceLastActivity() * 100.0 / 60);
        // 汇款及时性：已确认到账金额 / 累计订单额
        double dimRemit = amountVal <= 0 ? 0 : cap(remitVal / amountVal) * 100;
        // 跟进响应度：近90天跟进次数，6 次封顶
        double dimFollowup = cap(m.followupCount90d() / 6.0) * 100;

        double score = w.freq() * dimFreq + w.amount() * dimAmount + w.active() * dimActive
                + w.remit() * dimRemit + w.followup() * dimFollowup;
        score = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new Result(score, tierOf(score), round2(dimFreq), round2(dimAmount),
                round2(dimActive), round2(dimRemit), round2(dimFollowup));
    }

    /** VA-2 分层：≥80 高价值 / ≥60 潜力 / ≥40 待激活 / 其余流失风险 */
    public static String tierOf(double score) {
        if (score >= 80) {
            return "HIGH_VALUE";
        }
        if (score >= 60) {
            return "POTENTIAL";
        }
        if (score >= 40) {
            return "TO_ACTIVATE";
        }
        return "AT_RISK";
    }

    private static double cap(double v) {
        return Math.min(Math.max(v, 0), 1);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
