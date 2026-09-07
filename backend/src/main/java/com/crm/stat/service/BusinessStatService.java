package com.crm.stat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.stat.entity.BusinessStat;
import com.crm.stat.mapper.BusinessStatMapper;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import com.crm.trade.entity.TradeItem;
import com.crm.trade.mapper.TradeItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 多维统计服务（ST-1 总量/同环比、ST-2 趋势、ST-3 明细导出、ST-4 排名、ST-5 标的分析）
 * 聚合表 business_stat 预计算（每日 02:30 增量重建 + 手动全量触发），读聚合表满足 <300ms
 */
@Service
@RequiredArgsConstructor
public class BusinessStatService {

    private static final Set<String> DIMS = Set.of("DAY", "MONTH", "QUARTER", "YEAR");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BusinessStatMapper statMapper;
    private final TradeItemMapper itemMapper;
    private final ScopeService scopeService;
    private final SysUserMapper sysUserMapper;

    // ---------------- 预计算（幂等：按维度先删后插） ----------------

    /** 重建单个维度聚合（dim=DAY/MONTH/QUARTER/YEAR，覆盖全部历史；手动触发用） */
    @Transactional
    public int rebuild(String dim) {
        String d = requireDim(dim);
        statMapper.delete(new LambdaQueryWrapper<BusinessStat>().eq(BusinessStat::getStatDim, d));
        return aggregateAndStore(d, d.toLowerCase(), LocalDateTime.of(2000, 1, 1, 0, 0), null);
    }

    /**
     * 增量重建单个维度（批次6）：仅重建 since 之后有变动（订单创建/更新/取消、到账确认/更新）的周期桶，
     * 消除原全维先删后插的重建空窗与万级数据下的全量耗时
     */
    @Transactional
    public int rebuildIncremental(String dim, LocalDateTime since) {
        String d = requireDim(dim);
        String unit = d.toLowerCase();
        Set<LocalDate> buckets = new TreeSet<>();
        for (Map<String, Object> row : statMapper.affectedOrderBuckets(unit, since)) {
            buckets.add(toTime(row.get("bucket")).toLocalDate());
        }
        for (Map<String, Object> row : statMapper.affectedRemitBuckets(unit, since)) {
            buckets.add(toTime(row.get("bucket")).toLocalDate());
        }
        if (buckets.isEmpty()) {
            return 0;
        }
        statMapper.delete(new LambdaQueryWrapper<BusinessStat>()
                .eq(BusinessStat::getStatDim, d)
                .in(BusinessStat::getStatDate, buckets));
        return aggregateAndStore(d, unit, LocalDateTime.of(2000, 1, 1, 0, 0), new ArrayList<>(buckets));
    }

    /** 增量重建全部维度（定时任务用） */
    @Transactional
    public int rebuildAllIncremental(LocalDateTime since) {
        int n = 0;
        for (String dim : DIMS) {
            n += rebuildIncremental(dim, since);
        }
        return n;
    }

    /** 聚合指定范围并落库（buckets=null 全历史；否则仅聚合命中桶，行族/到账归集口径与全量一致） */
    private int aggregateAndStore(String d, String unit, LocalDateTime from, List<LocalDate> buckets) {
        Map<String, BusinessStat> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : statMapper.rawOrderRows(unit, from, buckets)) {
            LocalDate statDate = toTime(row.get("statDate")).toLocalDate();
            Long ownerId = ((Number) row.get("ownerId")).longValue();
            Long itemId = ((Number) row.get("itemId")).longValue();
            BusinessStat s = merged.computeIfAbsent(key(d, statDate, ownerId, itemId), k -> blank(d, statDate, ownerId, itemId));
            s.setOrderCount(((Number) row.get("orderCount")).longValue());
            s.setOrderAmount(toDecimal(row.get("orderAmount")));
            s.setCustomerCount(((Number) row.get("customerCount")).longValue());
        }
        for (Map<String, Object> row : statMapper.rawRemitRows(unit, from, buckets)) {
            LocalDate statDate = toTime(row.get("statDate")).toLocalDate();
            Long ownerId = ((Number) row.get("ownerId")).longValue();
            BigDecimal remit = toDecimal(row.get("remitAmount"));
            // owner 行
            BusinessStat owner = merged.get(key(d, statDate, ownerId, 0L));
            if (owner != null) {
                owner.setRemitAmount(remit);
            }
            // ALL 行（owner_id=0）：累加各 owner 到账
            BusinessStat all = merged.get(key(d, statDate, 0L, 0L));
            if (all != null) {
                all.setRemitAmount(all.getRemitAmount().add(remit));
            }
        }
        for (BusinessStat s : merged.values()) {
            BigDecimal order = s.getOrderAmount() == null ? BigDecimal.ZERO : s.getOrderAmount();
            BigDecimal remit = s.getRemitAmount() == null ? BigDecimal.ZERO : s.getRemitAmount();
            s.setArriveRate(order.signum() > 0
                    ? remit.divide(order, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            statMapper.insert(s);
        }
        return merged.size();
    }

    /** 全维度重建 */
    @Transactional
    public int rebuildAll() {
        int n = 0;
        for (String dim : DIMS) {
            n += rebuild(dim);
        }
        return n;
    }

    // ---------------- ST-1/ST-2 总量统计 + 趋势（读聚合表） ----------------

    /**
     * 区间内指定维度行（ALL 行族），每行附上一周期值（环比基数）
     * dim=MONTH 时 stat_date 为月初；查询区间按 stat_date 过滤
     */
    public Map<String, Object> summary(Long uid, String dim, String from, String to) {
        String d = requireDim(dim);
        List<BusinessStat> rows = statMapper.selectList(new LambdaQueryWrapper<BusinessStat>()
                .eq(BusinessStat::getStatDim, d)
                .eq(BusinessStat::getOwnerId, 0L)
                .eq(BusinessStat::getItemId, 0L)
                .ge(from != null, BusinessStat::getStatDate, from == null ? null : LocalDate.parse(from))
                .le(to != null, BusinessStat::getStatDate, to == null ? null : LocalDate.parse(to))
                .orderByAsc(BusinessStat::getStatDate));
        // 上一周期值（环比基数）：stat_date 前移一个周期
        long step = switch (d) {
            case "DAY" -> 1;
            case "MONTH" -> 1;
            case "QUARTER" -> 3;
            default -> 12;
        };
        List<Map<String, Object>> list = new ArrayList<>();
        for (BusinessStat s : rows) {
            BusinessStat prev = statMapper.selectOne(new LambdaQueryWrapper<BusinessStat>()
                    .eq(BusinessStat::getStatDim, d)
                    .eq(BusinessStat::getOwnerId, 0L)
                    .eq(BusinessStat::getItemId, 0L)
                    .eq(BusinessStat::getStatDate, s.getStatDate().minusMonths(step)));
            Map<String, Object> m = toRow(s);
            m.put("prevAmount", prev == null ? 0 : prev.getOrderAmount());
            m.put("prevCount", prev == null ? 0 : prev.getOrderCount());
            list.add(m);
        }
        // 区间合计 + 环比（与前一等长区间合计比较）
        BigDecimal sumAmount = rows.stream().map(BusinessStat::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long sumCount = rows.stream().mapToLong(BusinessStat::getOrderCount).sum();
        BigDecimal prevSumAmount = BigDecimal.ZERO;
        long prevSumCount = 0;
        if (!rows.isEmpty()) {
            LocalDate first = rows.get(0).getStatDate();
            LocalDate last = rows.get(rows.size() - 1).getStatDate();
            List<BusinessStat> prevRows = statMapper.selectList(new LambdaQueryWrapper<BusinessStat>()
                    .eq(BusinessStat::getStatDim, d)
                    .eq(BusinessStat::getOwnerId, 0L)
                    .eq(BusinessStat::getItemId, 0L)
                    .gt(BusinessStat::getStatDate, first.minusMonths(step))
                    .lt(BusinessStat::getStatDate, first));
            prevSumAmount = prevRows.stream().map(BusinessStat::getOrderAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            prevSumCount = prevRows.stream().mapToLong(BusinessStat::getOrderCount).sum();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", list);
        out.put("totalAmount", sumAmount);
        out.put("totalCount", sumCount);
        out.put("amountChainRatio", ratio(sumAmount, prevSumAmount));
        out.put("countChainRatio", ratio(BigDecimal.valueOf(sumCount), BigDecimal.valueOf(prevSumCount)));
        return out;
    }

    // ---------------- ST-4 业绩排名 / ST-5 标的分析 ----------------

    public List<Map<String, Object>> ownerRank(Long uid, String dim, String from, String to) {
        requireDim(dim);
        List<BusinessStat> rows = statMapper.selectList(new LambdaQueryWrapper<BusinessStat>()
                .eq(BusinessStat::getStatDim, requireDim(dim))
                .gt(BusinessStat::getOwnerId, 0L)
                .eq(BusinessStat::getItemId, 0L)
                .ge(from != null, BusinessStat::getStatDate, from == null ? null : LocalDate.parse(from))
                .le(to != null, BusinessStat::getStatDate, to == null ? null : LocalDate.parse(to))
                .orderByDesc(BusinessStat::getOrderAmount));
        List<Long> visible = scopeService.visibleOwnerIds(uid);
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 0;
        for (BusinessStat s : rows) {
            if (visible != null && !visible.contains(s.getOwnerId())) {
                continue;
            }
            rank++;
            Map<String, Object> m = toRow(s);
            m.put("rank", rank);
            m.put("ownerName", ownerName(s.getOwnerId()));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> itemRank(Long uid, String dim, String from, String to) {
        List<BusinessStat> rows = statMapper.selectList(new LambdaQueryWrapper<BusinessStat>()
                .eq(BusinessStat::getStatDim, requireDim(dim))
                .eq(BusinessStat::getOwnerId, 0L)
                .gt(BusinessStat::getItemId, 0L)
                .ge(from != null, BusinessStat::getStatDate, from == null ? null : LocalDate.parse(from))
                .le(to != null, BusinessStat::getStatDate, to == null ? null : LocalDate.parse(to))
                .orderByDesc(BusinessStat::getOrderAmount));
        // ST-5：区间内按标的跨周期聚合，并回填标的编码/名称（前端占比分析直接可用）
        Map<Long, BusinessStat> merged = new LinkedHashMap<>();
        for (BusinessStat s : rows) {
            BusinessStat acc = merged.computeIfAbsent(s.getItemId(), BusinessStatService::blankItem);
            acc.setOrderCount(acc.getOrderCount() + s.getOrderCount());
            acc.setOrderAmount(acc.getOrderAmount().add(s.getOrderAmount() == null ? BigDecimal.ZERO : s.getOrderAmount()));
            acc.setCustomerCount(acc.getCustomerCount() + s.getCustomerCount());
            acc.setRemitAmount(acc.getRemitAmount().add(s.getRemitAmount() == null ? BigDecimal.ZERO : s.getRemitAmount()));
        }
        List<BusinessStat> list = new ArrayList<>(merged.values());
        list.sort((a, b) -> b.getOrderAmount().compareTo(a.getOrderAmount()));
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 0;
        for (BusinessStat s : list) {
            rank++;
            Map<String, Object> m = toRow(s);
            BigDecimal order = s.getOrderAmount() == null ? BigDecimal.ZERO : s.getOrderAmount();
            BigDecimal remit = s.getRemitAmount() == null ? BigDecimal.ZERO : s.getRemitAmount();
            m.put("arriveRate", order.signum() > 0
                    ? remit.divide(order, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            m.put("rank", rank);
            m.put("itemId", s.getItemId());
            var item = itemMapper.selectById(s.getItemId());
            m.put("itemCode", item == null ? "" : item.getCode());
            m.put("itemName", item == null ? "标的#" + s.getItemId() : item.getName());
            out.add(m);
        }
        return out;
    }

    // ---------------- ST-3 明细 + CSV ----------------

    public PageResult<Map<String, Object>> detail(Long uid, Integer pageNum, Integer pageSize,
                                                  Long customerId, Long itemId, String status, String direction,
                                                  String from, String to) {
        Page<Map<String, Object>> page = new Page<>(pageNum == null ? 1 : pageNum,
                Math.min(pageSize == null ? 10 : pageSize, 100));
        var result = statMapper.selectDetail(page, customerId, itemId, status, direction,
                parseFrom(from), parseTo(to), scopeService.visibleOwnerIds(uid));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : result.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>(row);
            m.put("createdAt", toTime(row.get("createdAt")) == null ? "" : toTime(row.get("createdAt")).toString());
            m.put("remitStatus", remitStatus(row));
            list.add(m);
        }
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 明细 CSV 导出（UTF-8 BOM，口径与列表一致） */
    public byte[] exportDetailCsv(Long uid, Long customerId, Long itemId, String status, String direction,
                                  String from, String to) {
        var all = statMapper.selectDetail(new Page<Map<String, Object>>(1, 50000), customerId, itemId, status, direction,
                parseFrom(from), parseTo(to), scopeService.visibleOwnerIds(uid));
        List<String> lines = new ArrayList<>();
        lines.add("订单号,客户,标的,方向,数量,单价,金额,手续费,总额,订单状态,核销状态,业务员,创建时间");
        for (Map<String, Object> row : all.getRecords()) {
            lines.add(csv(row.get("orderNo")) + "," + csv(row.get("customerName")) + "," + csv(row.get("itemName"))
                    + "," + csv(row.get("direction")) + "," + row.get("quantity") + "," + row.get("price")
                    + "," + row.get("amount") + "," + row.get("feeAmount") + "," + row.get("totalAmount")
                    + "," + csv(row.get("status")) + "," + csv(remitStatus(row)) + ","
                    + csv(row.get("ownerName")) + "," + toTime(row.get("createdAt")));
        }
        StringBuilder sb = new StringBuilder("\uFEFF");
        lines.forEach(l -> sb.append(l).append("\r\n"));
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---------------- 内部方法 ----------------

    private static String remitStatus(Map<String, Object> row) {
        BigDecimal total = toDecimal(row.get("totalAmount"));
        BigDecimal settled = toDecimal(row.get("settledAmount"));
        if (settled.signum() <= 0) {
            return "UNSETTLED";
        }
        return settled.compareTo(total) >= 0 ? "WRITTEN_OFF" : "PARTIAL_WRITTEN_OFF";
    }

    private String requireDim(String dim) {
        if (dim == null || !DIMS.contains(dim)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "统计维度仅支持 DAY/MONTH/QUARTER/YEAR");
        }
        return dim;
    }

    private static String key(String dim, LocalDate date, Long owner, Long item) {
        return dim + "|" + date + "|" + owner + "|" + item;
    }

    private static BusinessStat blank(String dim, LocalDate date, Long owner, Long item) {
        BusinessStat s = new BusinessStat();
        s.setStatDim(dim);
        s.setStatDate(date);
        s.setOwnerId(owner);
        s.setItemId(item);
        s.setOrderCount(0L);
        s.setOrderAmount(BigDecimal.ZERO);
        s.setCustomerCount(0L);
        s.setRemitAmount(BigDecimal.ZERO);
        s.setArriveRate(BigDecimal.ZERO);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    /** ST-5 标的聚合累加器（跨周期合并用） */
    private static BusinessStat blankItem(Long itemId) {
        BusinessStat s = new BusinessStat();
        s.setItemId(itemId);
        s.setOrderCount(0L);
        s.setOrderAmount(BigDecimal.ZERO);
        s.setCustomerCount(0L);
        s.setRemitAmount(BigDecimal.ZERO);
        s.setArriveRate(BigDecimal.ZERO);
        return s;
    }

    /** 环比：本期/上期 - 1（百分数，保留 2 位；上期为 0 时返回 null） */
    private static Double ratio(BigDecimal cur, BigDecimal prev) {
        if (prev.signum() <= 0) {
            return null;
        }
        return cur.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Map<String, Object> toRow(BusinessStat s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statDate", s.getStatDate() == null ? "" : s.getStatDate().format(DATE_FMT));
        m.put("orderCount", s.getOrderCount());
        m.put("orderAmount", s.getOrderAmount());
        m.put("customerCount", s.getCustomerCount());
        m.put("remitAmount", s.getRemitAmount());
        m.put("arriveRate", s.getArriveRate());
        return m;
    }

    private String ownerName(Long ownerId) {
        if (ownerId == null) {
            return "";
        }
        var u = sysUserMapper.selectById(ownerId);
        return u == null ? "" : u.getRealName();
    }

    private static LocalDateTime parseFrom(String from) {
        return from == null || from.isBlank() ? null : LocalDateTime.parse(from + "T00:00:00");
    }

    private static LocalDateTime parseTo(String to) {
        return to == null || to.isBlank() ? null : LocalDateTime.parse(to + "T00:00:00").plusDays(1);
    }

    private static LocalDateTime toTime(Object v) {
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof LocalDate ld) {
            return ld.atStartOfDay();
        }
        if (v instanceof java.sql.Date d) {  // PG date 聚合列 getObject 返回 java.sql.Date
            return d.toLocalDate().atStartOfDay();
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

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
