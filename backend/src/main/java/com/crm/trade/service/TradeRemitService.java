package com.crm.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.CustomerService;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import com.crm.trade.dto.RemitQuery;
import com.crm.trade.dto.RemitSaveRequest;
import com.crm.trade.dto.WriteOffRequest;
import com.crm.trade.entity.TradeOrder;
import com.crm.trade.entity.TradeOrderSettlement;
import com.crm.trade.entity.TradeRemittance;
import com.crm.trade.mapper.TradeOrderMapper;
import com.crm.trade.mapper.TradeOrderSettlementMapper;
import com.crm.trade.mapper.TradeRemittanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 汇款与核销服务（RM-1~4，金融级一致性）
 * - 登记 → 经理到账确认/驳回 → 多对多核销
 * - 核销原子性（REM-DV-02）：事务内 FOR UPDATE 锁订单 + 汇款余额条件更新，
 *   汇款余额 / 订单剩余应付双向校验，杜绝超核与"已确认未推进"中间态
 */
@Service
@RequiredArgsConstructor
public class TradeRemitService {

    private final TradeRemittanceMapper remitMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderSettlementMapper settlementMapper;
    private final CustomerService customerService;
    private final CrmCustomerMapper customerMapper;
    private final SysUserMapper sysUserMapper;
    private final ScopeService scopeService;
    private final NotifyService notifyService;
    private final TradeOrderService tradeOrderService;

    // ---------------- RM-3 查询 ----------------

    /** 汇款分页列表（三端数据范围 + 客户/登记人回填 + 核销余额） */
    public PageResult<Map<String, Object>> page(Long uid, RemitQuery query) {
        Page<TradeRemittance> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(),
                Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 200));

        LambdaQueryWrapper<TradeRemittance> wrapper = new LambdaQueryWrapper<TradeRemittance>()
                .like(StringUtils.hasText(query.getKeyword()), TradeRemittance::getRemitNo, query.getKeyword())
                .eq(StringUtils.hasText(query.getStatus()), TradeRemittance::getStatus, query.getStatus())
                .eq(query.getCustomerId() != null, TradeRemittance::getCustomerId, query.getCustomerId())
                .orderByDesc(TradeRemittance::getCreatedAt);
        List<Long> ownerIds = scopeService.visibleOwnerIds(uid);
        if (ownerIds != null) {
            wrapper.in(ownerIds.isEmpty(), TradeRemittance::getOwnerId, -1L)
                    .in(!ownerIds.isEmpty(), TradeRemittance::getOwnerId, ownerIds);
        }

        List<TradeRemittance> rows = remitMapper.selectPage(page, wrapper).getRecords();
        List<Map<String, Object>> list = new ArrayList<>();
        for (TradeRemittance r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            CrmCustomer c = customerMapper.selectById(r.getCustomerId());
            row.put("id", r.getId());
            row.put("remitNo", r.getRemitNo());
            row.put("customerId", r.getCustomerId());
            row.put("customerName", c == null ? "-" : c.getName());
            row.put("ownerId", r.getOwnerId());
            row.put("ownerName", userName(r.getOwnerId()));
            row.put("amount", r.getAmount());
            row.put("currency", r.getCurrency());
            row.put("remittedAt", r.getRemittedAt() == null ? "" : r.getRemittedAt().toString());
            row.put("voucherKey", r.getVoucherKey());
            row.put("status", r.getStatus());
            row.put("writtenOffAmount", r.getWrittenOffAmount());
            row.put("balance", r.getAmount().subtract(r.getWrittenOffAmount()));
            row.put("confirmedAt", r.getConfirmedAt() == null ? "" : r.getConfirmedAt().toString());
            row.put("rejectReason", r.getRejectReason());
            row.put("remark", r.getRemark());
            row.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
            list.add(row);
        }
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 汇款详情（含核销明细） */
    public Map<String, Object> detail(Long uid, Long id) {
        TradeRemittance r = requireVisible(uid, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", r.getId());
        data.put("remitNo", r.getRemitNo());
        data.put("customerId", r.getCustomerId());
        CrmCustomer c = customerMapper.selectById(r.getCustomerId());
        data.put("customerName", c == null ? "-" : c.getName());
        data.put("ownerId", r.getOwnerId());
        data.put("ownerName", userName(r.getOwnerId()));
        data.put("amount", r.getAmount());
        data.put("currency", r.getCurrency());
        data.put("remittedAt", r.getRemittedAt() == null ? "" : r.getRemittedAt().toString());
        data.put("voucherKey", r.getVoucherKey());
        data.put("status", r.getStatus());
        data.put("writtenOffAmount", r.getWrittenOffAmount());
        data.put("balance", r.getAmount().subtract(r.getWrittenOffAmount()));
        data.put("confirmerName", userName(r.getConfirmerId()));
        data.put("confirmedAt", r.getConfirmedAt() == null ? "" : r.getConfirmedAt().toString());
        data.put("rejectReason", r.getRejectReason());
        data.put("remark", r.getRemark());
        data.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());

        List<Map<String, Object>> settlements = new ArrayList<>();
        for (TradeOrderSettlement s : settlementMapper.selectList(new LambdaQueryWrapper<TradeOrderSettlement>()
                .eq(TradeOrderSettlement::getRemittanceId, id)
                .orderByDesc(TradeOrderSettlement::getCreatedAt))) {
            Map<String, Object> row = new LinkedHashMap<>();
            TradeOrder o = orderMapper.selectById(s.getOrderId());
            row.put("id", s.getId());
            row.put("orderId", s.getOrderId());
            row.put("orderNo", o == null ? "-" : o.getOrderNo());
            row.put("orderStatus", o == null ? "-" : o.getStatus());
            row.put("amount", s.getAmount());
            row.put("operatorName", userName(s.getOperatorId()));
            row.put("createdAt", s.getCreatedAt() == null ? "" : s.getCreatedAt().toString());
            settlements.add(row);
        }
        data.put("settlements", settlements);
        return data;
    }

    // ---------------- RM-1 登记 ----------------

    @Transactional
    public Long register(Long uid, RemitSaveRequest req) {
        customerService.requireVisible(uid, req.getCustomerId());
        TradeRemittance exists = remitMapper.selectOne(new LambdaQueryWrapper<TradeRemittance>()
                .eq(TradeRemittance::getRemitNo, req.getRemitNo()));
        if (exists != null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "汇款单号已存在");
        }
        TradeRemittance r = new TradeRemittance();
        r.setRemitNo(req.getRemitNo());
        r.setCustomerId(req.getCustomerId());
        r.setOwnerId(uid);
        r.setAmount(req.getAmount());
        r.setCurrency(StringUtils.hasText(req.getCurrency()) ? req.getCurrency() : "CNY");
        r.setRemittedAt(req.getRemittedAt());
        r.setVoucherKey(req.getVoucherKey());
        r.setStatus("PENDING_CONFIRM");
        r.setWrittenOffAmount(BigDecimal.ZERO);
        r.setRemark(req.getRemark());
        remitMapper.insert(r);
        notifyService.publishToManagers(uid, "新汇款待确认",
                "汇款 " + r.getRemitNo() + " 金额 " + r.getAmount() + " 待到账确认", "REMIT", r.getId());
        return r.getId();
    }

    // ---------------- RM-2 到账确认 / 驳回 ----------------

    @Transactional
    public void confirm(Long uid, Long id) {
        TradeRemittance r = requireVisible(uid, id);
        if (!"PENDING_CONFIRM".equals(r.getStatus())) {
            throw new BizException(ResultCode.REMIT_CONFIRMED_ALREADY);
        }
        r.setStatus("CONFIRMED");
        r.setConfirmerId(uid);
        r.setConfirmedAt(LocalDateTime.now());
        remitMapper.updateById(r);
        notifyService.publish(r.getOwnerId(), "ORDER_EVENT", "汇款已确认到账",
                "汇款 " + r.getRemitNo() + " 已确认到账，可发起核销", "REMIT", r.getId());
    }

    @Transactional
    public void reject(Long uid, Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "驳回原因不能为空");
        }
        TradeRemittance r = requireVisible(uid, id);
        if (!"PENDING_CONFIRM".equals(r.getStatus())) {
            throw new BizException(ResultCode.REMIT_CONFIRMED_ALREADY);
        }
        r.setStatus("REJECTED");
        r.setConfirmerId(uid);
        r.setRejectReason(reason);
        remitMapper.updateById(r);
        notifyService.publish(r.getOwnerId(), "ORDER_EVENT", "汇款被驳回",
                "汇款 " + r.getRemitNo() + " 被驳回：" + reason, "REMIT", r.getId());
    }

    // ---------------- RM-4 核销（原子推进 + 双向防超核） ----------------

    /**
     * 多对多核销：一笔汇款拆多张订单。
     * 流程（单事务）：余额预校验 → 逐单 FOR UPDATE 锁订单 → 订单剩余应付校验
     * → 写核销明细 → 订单状态原子推进 → 汇款余额条件更新（并发冲突返回 40900）。
     */
    @Transactional
    public void writeOff(Long uid, Long remittanceId, List<WriteOffRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "核销明细不能为空");
        }
        TradeRemittance r = requireVisible(uid, remittanceId);
        if (!"CONFIRMED".equals(r.getStatus()) && !"PARTIALLY_WRITTEN_OFF".equals(r.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "汇款待确认或已驳回，不可核销");
        }

        BigDecimal requestTotal = items.stream().map(WriteOffRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (r.getWrittenOffAmount().add(requestTotal).compareTo(r.getAmount()) > 0) {
            throw new BizException(ResultCode.REMIT_WRITE_OFF_OVER);
        }

        for (WriteOffRequest item : items) {
            TradeOrder locked = orderMapper.selectForUpdate(item.getOrderId());
            if (locked == null) {
                throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
            }
            if (!scopeService.canSee(uid, locked.getOwnerId())) {
                throw new BizException(ResultCode.FORBIDDEN);
            }
            if (!"CONFIRMED".equals(locked.getStatus()) && !"PARTIAL_DEALT".equals(locked.getStatus())) {
                throw new BizException(ResultCode.ORDER_STATE_ILLEGAL.getCode(),
                        "订单待审批或已完结，不可核销：" + locked.getOrderNo());
            }
            BigDecimal paid = tradeOrderService.paidAmount(locked.getId());
            BigDecimal after = paid.add(item.getAmount());
            if (after.compareTo(locked.getTotalAmount()) > 0) {
                throw new BizException(ResultCode.REMIT_WRITE_OFF_OVER.getCode(),
                        "核销金额超出订单剩余应付：" + locked.getOrderNo());
            }

            TradeOrderSettlement s = new TradeOrderSettlement();
            s.setRemittanceId(remittanceId);
            s.setOrderId(locked.getId());
            s.setAmount(item.getAmount());
            s.setOperatorId(uid);
            settlementMapper.insert(s);

            tradeOrderService.advanceOnSettlement(locked, after, uid);
        }

        String newStatus = r.getWrittenOffAmount().add(requestTotal).compareTo(r.getAmount()) == 0
                ? "WRITTEN_OFF" : "PARTIALLY_WRITTEN_OFF";
        int rows = remitMapper.update(null, new LambdaUpdateWrapper<TradeRemittance>()
                .eq(TradeRemittance::getId, remittanceId)
                .eq(TradeRemittance::getWrittenOffAmount, r.getWrittenOffAmount())
                .setSql("written_off_amount = written_off_amount + {0}", requestTotal)
                .set(TradeRemittance::getStatus, newStatus));
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT);
        }
        notifyService.publish(r.getOwnerId(), "ORDER_EVENT", "核销完成",
                "汇款 " + r.getRemitNo() + " 核销 " + requestTotal + "（"
                        + newStatus + "）", "REMIT", remittanceId);
    }

    // ---------------- internal ----------------

    private TradeRemittance requireVisible(Long uid, Long id) {
        TradeRemittance r = remitMapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "汇款记录不存在");
        }
        if (!scopeService.canSee(uid, r.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return r;
    }

    private String userName(Long id) {
        if (id == null) {
            return "-";
        }
        SysUser u = sysUserMapper.selectById(id);
        return u == null ? "-" : u.getRealName();
    }
}
