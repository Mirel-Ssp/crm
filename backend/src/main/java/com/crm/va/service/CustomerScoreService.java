package com.crm.va.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmFollowup;
import com.crm.customer.mapper.CrmFollowupMapper;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import com.crm.trade.service.NotifyService;
import com.crm.va.entity.CustomerScore;
import com.crm.va.entity.VaRule;
import com.crm.va.mapper.CustomerScoreMapper;
import com.crm.va.mapper.VaRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户价值评分服务（VA-1~VA-5）
 * - VA-1 五维加权评分：权重取 va_rule，每日重算（@Scheduled 02:00）+ 手动触发
 * - VA-2 分层自动打标 + 手动调整（manual=1 后自动重算跳过）
 * - VA-4 近 30/90 天趋势
 * - VA-5 沉默客户唤醒：超 SILENT_DAYS 无成交且无跟进 → 待唤醒列表 → 分配业务员生成跟进待办
 */
@Service
@RequiredArgsConstructor
public class CustomerScoreService {

    public static final String KEY_FREQ = "WEIGHT_FREQ";
    public static final String KEY_AMOUNT = "WEIGHT_AMOUNT";
    public static final String KEY_ACTIVE = "WEIGHT_ACTIVE";
    public static final String KEY_REMIT = "WEIGHT_REMIT";
    public static final String KEY_FOLLOWUP = "WEIGHT_FOLLOWUP";
    public static final String KEY_SILENT_DAYS = "SILENT_DAYS";
    private static final Set<String> WEIGHT_KEYS = Set.of(KEY_FREQ, KEY_AMOUNT, KEY_ACTIVE, KEY_REMIT, KEY_FOLLOWUP);
    /** SET-BASED 批量 upsert 分批大小 */
    private static final int UPSERT_BATCH_SIZE = 500;

    private final CustomerScoreMapper scoreMapper;
    private final VaRuleMapper ruleMapper;
    private final CrmFollowupMapper followupMapper;
    private final ScopeService scopeService;
    private final SysUserMapper sysUserMapper;
    private final NotifyService notifyService;

    // ---------------- VA-1 重算 ----------------

    /**
     * 全量重算（跳过 manual=1 客户），返回本次重算客户数
     * 批次6 SET-BASED 改造：聚合 1 SQL + manual 标记 1 SQL + 内存计算 + 批量 upsert（500 条/批），
     * 消除原逐客户 SELECT/INSERT/UPDATE 的 N+1（万级客户重算线性增长瓶颈）
     */
    @Transactional
    public int recalcAll() {
        ValueScoreCalculator.Weights w = loadWeights();
        LocalDate today = LocalDate.now();
        Map<Long, Integer> manualFlags = new HashMap<>();
        for (Map<String, Object> row : scoreMapper.selectLatestManualFlags()) {
            Long customerId = ((Number) row.get("customerId")).longValue();
            manualFlags.put(customerId, row.get("manual") == null ? 0 : ((Number) row.get("manual")).intValue());
        }
        List<CustomerScore> batch = new ArrayList<>();
        for (Map<String, Object> agg : scoreMapper.selectCustomerAggregates()) {
            Long customerId = ((Number) agg.get("customerId")).longValue();
            if (manualFlags.getOrDefault(customerId, 0) == 1) {
                continue;
            }
            ValueScoreCalculator.Result r = ValueScoreCalculator.compute(toMetrics(agg), w);
            batch.add(buildRow(customerId, r, today, 0));
        }
        for (int i = 0; i < batch.size(); i += UPSERT_BATCH_SIZE) {
            scoreMapper.upsertBatch(batch.subList(i, Math.min(i + UPSERT_BATCH_SIZE, batch.size())));
        }
        return batch.size();
    }

    /** 单客户重算（手动触发） */
    @Transactional
    public Long recalcCustomer(Long customerId) {
        ValueScoreCalculator.Weights w = loadWeights();
        for (Map<String, Object> agg : scoreMapper.selectCustomerAggregates()) {
            if (customerId.equals(((Number) agg.get("customerId")).longValue())) {
                CustomerScore latest = latestRow(customerId);
                int manual = latest != null && latest.getManual() != null ? latest.getManual() : 0;
                ValueScoreCalculator.Result r = ValueScoreCalculator.compute(toMetrics(agg), w);
                upsert(customerId, r, LocalDate.now(), manual, null);
                return customerId;
            }
        }
        throw new BizException(ResultCode.NOT_FOUND.getCode(), "客户不存在或已停用");
    }

    // ---------------- VA-2 分层与调整 ----------------

    /** 手动调整评分/分层（manual=1，重算跳过；留痕走审计 AOP） */
    @Transactional
    public void manualAdjust(Long uid, Long customerId, Double score, String reason) {
        if (score == null || score < 0 || score > 100) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "评分需在 0~100 之间");
        }
        CustomerScore latest = latestRow(customerId);
        ValueScoreCalculator.Result r = new ValueScoreCalculator.Result(
                score, ValueScoreCalculator.tierOf(score),
                latest == null || latest.getDimFreq() == null ? 0 : latest.getDimFreq().doubleValue(),
                latest == null || latest.getDimAmount() == null ? 0 : latest.getDimAmount().doubleValue(),
                latest == null || latest.getDimActive() == null ? 0 : latest.getDimActive().doubleValue(),
                latest == null || latest.getDimRemittance() == null ? 0 : latest.getDimRemittance().doubleValue(),
                latest == null || latest.getDimFollowup() == null ? 0 : latest.getDimFollowup().doubleValue());
        upsert(customerId, r, LocalDate.now(), 1, uid);
    }

    // ---------------- 查询（VA 列表/分布/趋势） ----------------

    public PageResult<Map<String, Object>> page(Long uid, Integer pageNum, Integer pageSize,
                                                String tier, String keyword) {
        Page<Map<String, Object>> page = new Page<>(pageNum == null ? 1 : pageNum,
                Math.min(pageSize == null ? 10 : pageSize, 100));
        var result = scoreMapper.selectLatestScores(page, tier, keyword, scopeService.visibleOwnerIds(uid));
        return new PageResult<>(new ArrayList<>(result.getRecords()), result.getTotal(),
                result.getCurrent(), result.getSize(), result.getPages());
    }

    public List<Map<String, Object>> distribution(Long uid) {
        List<Map<String, Object>> rows = scoreMapper.selectTierDistribution(scopeService.visibleOwnerIds(uid));
        Map<String, Long> dist = new LinkedHashMap<>();
        for (String tier : List.of("HIGH_VALUE", "POTENTIAL", "TO_ACTIVATE", "AT_RISK")) {
            dist.put(tier, 0L);
        }
        for (Map<String, Object> row : rows) {
            dist.put(String.valueOf(row.get("tier")), ((Number) row.get("cnt")).longValue());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        dist.forEach((tier, cnt) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tier", tier);
            m.put("count", cnt);
            out.add(m);
        });
        return out;
    }

    /** VA-4 评分趋势（近 N 天，按日期升序） */
    public List<Map<String, Object>> trend(Long customerId, Integer days) {
        int n = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDate from = LocalDate.now().minusDays(n);
        List<CustomerScore> rows = scoreMapper.selectList(new LambdaQueryWrapper<CustomerScore>()
                .eq(CustomerScore::getCustomerId, customerId)
                .ge(CustomerScore::getCalcDate, from)
                .orderByAsc(CustomerScore::getCalcDate));
        List<Map<String, Object>> out = new ArrayList<>();
        for (CustomerScore s : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("calcDate", s.getCalcDate() == null ? "" : s.getCalcDate().toString());
            m.put("score", s.getScore());
            m.put("tier", s.getTier());
            out.add(m);
        }
        return out;
    }

    // ---------------- VA-5 沉默唤醒 ----------------

    public List<Map<String, Object>> silentList(Long uid) {
        int silentDays = silentDays();
        LocalDateTime before = LocalDateTime.now().minusDays(silentDays);
        List<Map<String, Object>> rows = scoreMapper.selectSilentCustomers(before, scopeService.visibleOwnerIds(uid));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerId", ((Number) row.get("customerId")).longValue());
            m.put("customerName", row.get("customerName"));
            Long ownerId = row.get("ownerId") == null ? null : ((Number) row.get("ownerId")).longValue();
            m.put("ownerId", ownerId);
            m.put("ownerName", ownerName(ownerId));
            m.put("lastOrderAt", toStr(row.get("lastOrderAt")));
            m.put("lastFollowupAt", toStr(row.get("lastFollowupAt")));
            m.put("silentDays", silentDays);
            out.add(m);
        }
        return out;
    }

    /** 唤醒分配：给目标业务员生成跟进待办（复用 FUP 待办看板）并通知 */
    @Transactional
    public void wakeAssign(Long uid, Long customerId, Long assigneeId) {
        if (assigneeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "分配对象不能为空");
        }
        scopeService.requireActiveUser(assigneeId);
        CrmFollowup f = new CrmFollowup();
        f.setRelType("CUSTOMER");
        f.setRelId(customerId);
        f.setContent("沉默客户唤醒跟进（VA-5 分配）");
        f.setMethod("PHONE");
        f.setStatus("TODO");
        f.setNextFollowupAt(LocalDateTime.now().plusDays(3));
        f.setOwnerId(assigneeId);
        followupMapper.insert(f);
        notifyService.publish(assigneeId, "SYSTEM", "沉默客户唤醒",
                "客户 " + customerId + " 已超沉默期，已分配给您跟进", "CUSTOMER", customerId);
    }

    // ---------------- 权重配置 ----------------

    public Map<String, Object> weights() {
        ValueScoreCalculator.Weights w = loadWeights();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(KEY_FREQ, w.freq());
        out.put(KEY_AMOUNT, w.amount());
        out.put(KEY_ACTIVE, w.active());
        out.put(KEY_REMIT, w.remit());
        out.put(KEY_FOLLOWUP, w.followup());
        out.put(KEY_SILENT_DAYS, silentDays());
        return out;
    }

    @Transactional
    public void updateWeights(Long uid, Map<String, Double> req) {
        double sum = 0;
        for (String key : WEIGHT_KEYS) {
            Double v = req.get(key);
            if (v == null || v < 0 || v > 1) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "权重 " + key + " 需在 0~1 之间");
            }
            sum += v;
        }
        if (Math.abs(sum - 1) > 0.0001) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "五维权重之和必须等于 1");
        }
        for (String key : WEIGHT_KEYS) {
            VaRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<VaRule>().eq(VaRule::getRuleKey, key));
            if (rule != null) {
                rule.setRuleValue(BigDecimal.valueOf(req.get(key)).setScale(4, RoundingMode.HALF_UP));
                rule.setUpdatedBy(uid);
                rule.setUpdatedAt(LocalDateTime.now());
                ruleMapper.updateById(rule);
            }
        }
    }

    public int silentDays() {
        VaRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<VaRule>().eq(VaRule::getRuleKey, KEY_SILENT_DAYS));
        return rule == null ? 30 : rule.getRuleValue().intValue();
    }

    // ---------------- 内部方法 ----------------

    private ValueScoreCalculator.Weights loadWeights() {
        Map<String, Double> w = new HashMap<>();
        for (VaRule rule : ruleMapper.selectList(null)) {
            w.put(rule.getRuleKey(), rule.getRuleValue().doubleValue());
        }
        return new ValueScoreCalculator.Weights(
                w.getOrDefault(KEY_FREQ, 0.30),
                w.getOrDefault(KEY_AMOUNT, 0.30),
                w.getOrDefault(KEY_ACTIVE, 0.20),
                w.getOrDefault(KEY_REMIT, 0.10),
                w.getOrDefault(KEY_FOLLOWUP, 0.10));
    }

    private ValueScoreCalculator.RawMetrics toMetrics(Map<String, Object> agg) {
        LocalDateTime lastOrder = toTime(agg.get("lastOrderAt"));
        LocalDateTime lastFollowup = toTime(agg.get("lastFollowupAt"));
        Integer daysActivity = null;
        LocalDateTime last = lastOrder == null ? lastFollowup
                : (lastFollowup == null ? lastOrder : (lastFollowup.isAfter(lastOrder) ? lastFollowup : lastOrder));
        if (last != null) {
            long days = Duration.between(last.toLocalDate().atStartOfDay(), LocalDate.now().atStartOfDay()).toDays();
            daysActivity = (int) Math.max(days, 0);
        }
        return new ValueScoreCalculator.RawMetrics(
                ((Number) agg.getOrDefault("orderCount90d", 0)).longValue(),
                toDecimal(agg.get("totalAmount")),
                daysActivity,
                toDecimal(agg.get("remitAmount")),
                ((Number) agg.getOrDefault("followupCount90d", 0)).longValue());
    }

    private void upsert(Long customerId, ValueScoreCalculator.Result r, LocalDate calcDate, int manual, Long uid) {
        CustomerScore row = scoreMapper.selectOne(new LambdaQueryWrapper<CustomerScore>()
                .eq(CustomerScore::getCustomerId, customerId)
                .eq(CustomerScore::getCalcDate, calcDate));
        boolean create = row == null;
        if (create) {
            row = new CustomerScore();
            row.setCustomerId(customerId);
            row.setCalcDate(calcDate);
        }
        applyResult(row, r, manual);
        if (create) {
            scoreMapper.insert(row);
        } else {
            scoreMapper.updateById(row);
        }
    }

    /** 统一分数装配（SET-BASED 批量与单客户 upsert 共用），ID 用 MP 雪花分配 */
    private CustomerScore buildRow(Long customerId, ValueScoreCalculator.Result r, LocalDate calcDate, int manual) {
        CustomerScore row = new CustomerScore();
        row.setId(IdWorker.getId());
        row.setCustomerId(customerId);
        row.setCalcDate(calcDate);
        applyResult(row, r, manual);
        return row;
    }

    private static void applyResult(CustomerScore row, ValueScoreCalculator.Result r, int manual) {
        row.setScore(BigDecimal.valueOf(r.score()).setScale(2, RoundingMode.HALF_UP));
        row.setTier(r.tier());
        row.setDimFreq(BigDecimal.valueOf(r.dimFreq()).setScale(2, RoundingMode.HALF_UP));
        row.setDimAmount(BigDecimal.valueOf(r.dimAmount()).setScale(2, RoundingMode.HALF_UP));
        row.setDimActive(BigDecimal.valueOf(r.dimActive()).setScale(2, RoundingMode.HALF_UP));
        row.setDimRemittance(BigDecimal.valueOf(r.dimRemit()).setScale(2, RoundingMode.HALF_UP));
        row.setDimFollowup(BigDecimal.valueOf(r.dimFollowup()).setScale(2, RoundingMode.HALF_UP));
        row.setManual(manual);
    }

    private CustomerScore latestRow(Long customerId) {
        return scoreMapper.selectOne(new LambdaQueryWrapper<CustomerScore>()
                .eq(CustomerScore::getCustomerId, customerId)
                .orderByDesc(CustomerScore::getCalcDate)
                .last("LIMIT 1"));
    }

    private String ownerName(Long ownerId) {
        if (ownerId == null) {
            return "";
        }
        var u = sysUserMapper.selectById(ownerId);
        return u == null ? "" : u.getRealName();
    }

    private static LocalDateTime toTime(Object v) {
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static String toStr(Object v) {
        LocalDateTime t = toTime(v);
        return t == null ? "" : t.toString();
    }
}
