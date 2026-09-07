package com.crm.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.mapper.CrmFollowupMapper;
import com.crm.lead.entity.CrmLead;
import com.crm.lead.mapper.CrmLeadMapper;
import com.crm.opportunity.service.OppService;
import com.crm.system.service.ScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表服务（RPT-DV-01/02）：客户（等级/生命周期）+ 线索（状态/转化率）+ 商机（漏斗/丢单）
 * 导出 CSV：UTF-8 带 BOM（Excel 兼容）；数据范围与列表口径一致（可对账）
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 与 V4 字典 opportunity_stage 固化命名一致 */
    private static final Map<Integer, String> STAGE_NAMES = Map.of(
            1, "初步接触", 2, "需求确认", 3, "方案报价", 4, "商务谈判", 5, "成交准备", 6, "已成交", 7, "已丢单");
    private static final Map<String, String> LEVEL_NAMES =
            Map.of("VIP", "VIP客户", "IMPORTANT", "重要客户", "NORMAL", "普通客户");
    private static final Map<String, String> LIFECYCLE_NAMES = Map.of(
            "POTENTIAL", "潜在客户", "FOLLOWING", "跟进中", "WON", "已成交", "LOST", "已流失", "DORMANT", "休眠");
    private static final Map<String, String> LEAD_STATUS_NAMES = Map.of(
            "PENDING", "待处理", "CLAIMED", "已领取", "ASSIGNED", "已分配", "CONVERTED", "已转化", "INVALID", "无效");

    private final CrmCustomerMapper customerMapper;
    private final CrmFollowupMapper followupMapper;
    private final CrmLeadMapper leadMapper;
    private final OppService oppService;
    private final ScopeService scopeService;

    /** 客户报表：等级分布 + 生命周期分布 */
    public Map<String, Object> customerReport(Long uid, String from, String to) {
        List<CrmCustomer> all = customerMapper.selectList(customerWrapper(uid, from, to));
        Map<String, Long> byLevel = all.stream().collect(Collectors.groupingBy(
                c -> c.getLevel() == null ? "NORMAL" : c.getLevel(), Collectors.counting()));
        Map<String, Long> byLifecycle = all.stream().collect(Collectors.groupingBy(
                c -> c.getLifecycleStatus() == null ? "POTENTIAL" : c.getLifecycleStatus(), Collectors.counting()));
        return Map.of(
                "total", all.size(),
                "byLevel", toRows(LEVEL_NAMES, byLevel),
                "byLifecycle", toRows(LIFECYCLE_NAMES, byLifecycle));
    }

    /** 线索报表：状态分布 + 线索转化率（已转化/总量） */
    public Map<String, Object> leadReport(Long uid, String from, String to) {
        List<CrmLead> all = leadMapper.selectList(leadWrapper(uid, from, to));
        Map<String, Long> byStatus = all.stream().collect(Collectors.groupingBy(
                l -> l.getStatus() == null ? "PENDING" : l.getStatus(), Collectors.counting()));
        long converted = byStatus.getOrDefault("CONVERTED", 0L);
        double rate = all.isEmpty() ? 0.0 : Math.round(converted * 1000.0 / all.size()) / 10.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("byStatus", toRows(LEAD_STATUS_NAMES, byStatus));
        result.put("conversionRate", rate);
        return result;
    }

    /** 商机报表：漏斗 + 丢单原因（口径复用 OppService，与商机列表可对账） */
    public Map<String, Object> opportunityReport(Long uid, String from, String to) {
        return Map.of(
                "funnel", oppService.funnel(uid, from, to),
                "lossStats", oppService.lossStats(uid, from, to));
    }

    /**
     * 跟进及时率监控（CRM-F3）：活跃客户最近一次跟进超 N 天（默认 7）的占比 + 超时清单 + 超期待办
     * 数据范围与列表口径一致（visibleOwnerIds）
     */
    public Map<String, Object> followupTimeliness(Long uid, Integer days) {
        int threshold = (days == null || days <= 0) ? 7 : Math.min(days, 365);
        List<Long> visible = visibleIds(uid);
        List<Long> ownerIds = visible == null ? null : (visible.isEmpty() ? List.of(-1L) : visible);

        LambdaQueryWrapper<CrmCustomer> cw = new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getStatus, "ACTIVE");
        applyScope(ownerIds, v -> cw.in(CrmCustomer::getOwnerId, v),
                () -> cw.eq(CrmCustomer::getOwnerId, -1));
        long totalActive = customerMapper.selectCount(cw);

        long overdueCount = followupMapper.countOverdueCustomers(ownerIds, threshold);
        List<Map<String, Object>> overdueList = followupMapper.selectOverdueCustomers(ownerIds, threshold, 50);
        List<Map<String, Object>> overdueTodos = followupMapper.selectOverdueTodos(ownerIds);

        double timelyRate = totalActive == 0 ? 100.0
                : Math.round((totalActive - overdueCount) * 1000.0 / totalActive) / 10.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("thresholdDays", threshold);
        result.put("totalActive", totalActive);
        result.put("overdueCount", overdueCount);
        result.put("timelyRate", timelyRate);
        result.put("overdueTodoCount", overdueTodos.size());
        result.put("overdueList", overdueList);
        result.put("overdueTodos", overdueTodos);
        return result;
    }

    /** CSV 导出（UTF-8 BOM）：type = customer / lead / opportunity / followup-timeliness */
    public byte[] exportCsv(Long uid, String type, String from, String to) {
        List<String> lines = new ArrayList<>();
        switch (type) {
            case "customer" -> {
                Map<String, Object> r = customerReport(uid, from, to);
                lines.add("维度,项目,数量");
                for (Map<String, Object> row : asRows(r.get("byLevel"))) {
                    lines.add("客户等级," + csv(row.get("name")) + "," + row.get("count"));
                }
                for (Map<String, Object> row : asRows(r.get("byLifecycle"))) {
                    lines.add("生命周期," + csv(row.get("name")) + "," + row.get("count"));
                }
            }
            case "lead" -> {
                Map<String, Object> r = leadReport(uid, from, to);
                lines.add("状态,数量,占比(%)");
                for (Map<String, Object> row : asRows(r.get("byStatus"))) {
                    long count = ((Number) row.get("count")).longValue();
                    long total = ((Number) r.get("total")).longValue();
                    String pct = total == 0 ? "0.0"
                            : String.valueOf(Math.round(count * 1000.0 / total) / 10.0);
                    lines.add(csv(row.get("name")) + "," + count + "," + pct);
                }
                lines.add("合计," + r.get("total") + ",100.0");
                lines.add("线索转化率(%)," + r.get("conversionRate") + ",");
            }
            case "opportunity" -> {
                Map<String, Object> r = opportunityReport(uid, from, to);
                lines.add("==商机漏斗==");
                lines.add("阶段,数量,金额,默认赢率(%),加权预测金额");
                for (Map<String, Object> row : asRows(r.get("funnel"))) {
                    lines.add(csv(STAGE_NAMES.getOrDefault(((Number) row.get("stage")).intValue(), "-"))
                            + "," + row.get("count") + "," + row.get("amount")
                            + "," + row.get("winRate") + "," + row.get("weightedAmount"));
                }
                lines.add("");
                lines.add("==丢单原因==");
                lines.add("原因,数量,金额");
                for (Map<String, Object> row : asRows(r.get("lossStats"))) {
                    lines.add(csv(row.get("reason")) + "," + row.get("count") + "," + row.get("amount"));
                }
            }
            case "followup-timeliness" -> {
                Map<String, Object> r = followupTimeliness(uid, null);
                lines.add("==跟进及时率监控（阈值 " + r.get("thresholdDays") + " 天）==");
                lines.add("活跃客户数,超时未跟进数,跟进及时率(%)");
                lines.add(r.get("totalActive") + "," + r.get("overdueCount") + "," + r.get("timelyRate"));
                lines.add("");
                lines.add("==超时未跟进客户清单（最多50条）==");
                lines.add("客户,负责人,等级,最近跟进时间,超时天数,累计跟进次数");
                for (Map<String, Object> row : asRows(r.get("overdueList"))) {
                    lines.add(csv(row.get("name")) + "," + csv(row.get("ownerName")) + ","
                            + csv(LEVEL_NAMES.getOrDefault(String.valueOf(row.get("level")), "-")) + ","
                            + row.get("lastFollowupAt") + "," + row.get("overdueDays") + "," + row.get("followupCount"));
                }
                lines.add("");
                lines.add("==超期待办任务（最多50条）==");
                lines.add("负责人,类型,关联ID,内容,计划跟进时间,超时天数");
                for (Map<String, Object> row : asRows(r.get("overdueTodos"))) {
                    lines.add(csv(row.get("ownerName")) + "," + row.get("relType") + "," + row.get("relId") + ","
                            + csv(row.get("content")) + "," + row.get("nextFollowupAt") + "," + row.get("overdueDays"));
                }
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST.getCode(), "未知报表类型: " + type);
        }
        StringBuilder sb = new StringBuilder("\uFEFF");
        lines.forEach(l -> sb.append(l).append("\r\n"));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------------- 内部工具 ----------------

    private LambdaQueryWrapper<CrmCustomer> customerWrapper(Long uid, String from, String to) {
        LambdaQueryWrapper<CrmCustomer> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(from)) {
            w.ge(CrmCustomer::getCreatedAt, LocalDateTime.parse(from + "T00:00:00"));
        }
        if (StringUtils.hasText(to)) {
            w.lt(CrmCustomer::getCreatedAt, LocalDateTime.parse(to + "T00:00:00").plusDays(1));
        }
        applyScope(visibleIds(uid), v -> w.in(CrmCustomer::getOwnerId, v),
                () -> w.eq(CrmCustomer::getOwnerId, -1));
        return w;
    }

    private LambdaQueryWrapper<CrmLead> leadWrapper(Long uid, String from, String to) {
        LambdaQueryWrapper<CrmLead> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(from)) {
            w.ge(CrmLead::getCreatedAt, LocalDateTime.parse(from + "T00:00:00"));
        }
        if (StringUtils.hasText(to)) {
            w.lt(CrmLead::getCreatedAt, LocalDateTime.parse(to + "T00:00:00").plusDays(1));
        }
        applyScope(visibleIds(uid), v -> w.in(CrmLead::getOwnerId, v),
                () -> w.eq(CrmLead::getOwnerId, -1));
        return w;
    }

    private List<Long> visibleIds(Long uid) {
        return scopeService.visibleOwnerIds(uid);
    }

    private void applyScope(List<Long> visibleIds,
                            java.util.function.Consumer<List<Long>> inClause,
                            Runnable emptyClause) {
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                emptyClause.run();
            } else {
                inClause.accept(visibleIds);
            }
        }
    }

    /** 计数 → [{code, name, count}]，按字典顺序稳定输出 */
    private List<Map<String, Object>> toRows(Map<String, String> names, Map<String, Long> counts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        names.forEach((code, name) -> rows.add(Map.of(
                "code", code,
                "name", name,
                "count", counts.getOrDefault(code, 0L))));
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asRows(Object o) {
        return (List<Map<String, Object>>) o;
    }

    /** CSV 字段转义：含逗号/引号/换行时加引号 */
    private String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
