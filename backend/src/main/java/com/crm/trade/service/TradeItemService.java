package com.crm.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.trade.dto.ItemSaveRequest;
import com.crm.trade.dto.PriceChangeRequest;
import com.crm.trade.entity.TradeItem;
import com.crm.trade.entity.TradeItemPriceLog;
import com.crm.trade.entity.TradeRule;
import com.crm.trade.mapper.TradeItemMapper;
import com.crm.trade.mapper.TradeItemPriceLogMapper;
import com.crm.trade.mapper.TradeRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易标的服务（TB-1 档案 / TB-2 调价留痕 / TB-3 规则配置，管理员端）
 */
@Service
@RequiredArgsConstructor
public class TradeItemService {

    private final TradeItemMapper itemMapper;
    private final TradeItemPriceLogMapper priceLogMapper;
    private final TradeRuleMapper ruleMapper;

    /** 标的列表（全角色可见，供订单创建下拉） */
    public List<TradeItem> page(String keyword, String category, String status) {
        return itemMapper.selectList(new LambdaQueryWrapper<TradeItem>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(TradeItem::getName, keyword)
                        .or()
                        .like(TradeItem::getCode, keyword))
                .eq(StringUtils.hasText(category), TradeItem::getCategory, category)
                .eq(StringUtils.hasText(status), TradeItem::getStatus, status)
                .orderByDesc(TradeItem::getCreatedAt));
    }

    /** 建档（默认 DRAFT 草稿，上架走 /list） */
    @Transactional
    public Long create(Long uid, ItemSaveRequest req) {
        requireCodeFree(req.getCode(), null);
        TradeItem item = new TradeItem();
        apply(item, req);
        item.setStatus("DRAFT");
        itemMapper.insert(item);
        // 初始价留痕
        priceLogMapper.insert(log(item.getId(), null, item.getReferencePrice(), uid));
        return item.getId();
    }

    /** 编辑（不含价格，调价走 /price 留痕） */
    @Transactional
    public void update(Long uid, Long id, ItemSaveRequest req) {
        TradeItem item = requireItem(id);
        requireCodeFree(req.getCode(), id);
        apply(item, req);
        itemMapper.updateById(item);
    }

    /** 调价（TB-2：旧价 → 新价留痕） */
    @Transactional
    public void changePrice(Long uid, Long id, PriceChangeRequest req) {
        TradeItem item = requireItem(id);
        BigDecimal old = item.getReferencePrice();
        item.setReferencePrice(req.getNewPrice());
        itemMapper.updateById(item);
        priceLogMapper.insert(log(id, old, req.getNewPrice(), uid));
    }

    /** 上架（DRAFT/DELISTED → LISTED） */
    @Transactional
    public void listItem(Long uid, Long id) {
        TradeItem item = requireItem(id);
        if (!"DRAFT".equals(item.getStatus()) && !"DELISTED".equals(item.getStatus())) {
            throw new BizException(ResultCode.ITEM_OFF_SHELF);
        }
        item.setStatus("LISTED");
        item.setListedAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    /** 下架（LISTED → DELISTED；订单创建时校验） */
    @Transactional
    public void delistItem(Long uid, Long id) {
        TradeItem item = requireItem(id);
        if (!"LISTED".equals(item.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "仅上架状态的标的可下架");
        }
        item.setStatus("DELISTED");
        item.setDelistedAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    /** 调价留痕（TB-2：倒序） */
    public List<TradeItemPriceLog> priceLogs(Long itemId) {
        requireItem(itemId);
        return priceLogMapper.selectList(new LambdaQueryWrapper<TradeItemPriceLog>()
                .eq(TradeItemPriceLog::getItemId, itemId)
                .orderByDesc(TradeItemPriceLog::getCreatedAt));
    }

    /** 规则列表（TB-3） */
    public List<TradeRule> rules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<TradeRule>().orderByAsc(TradeRule::getId));
    }

    /** 规则更新（管理员） */
    @Transactional
    public void updateRule(Long uid, Long id, BigDecimal value) {
        TradeRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "规则不存在");
        }
        rule.setRuleValue(value);
        rule.setUpdatedBy(uid);
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
    }

    /** 规则取值（订单计价用；缺失视为不限制） */
    public BigDecimal ruleValue(String key) {
        TradeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<TradeRule>().eq(TradeRule::getRuleKey, key));
        return rule == null ? null : rule.getRuleValue();
    }

    /** 订单创建校验用：标的必须已上架 */
    public TradeItem requireListed(Long itemId) {
        TradeItem item = requireItem(itemId);
        if (!"LISTED".equals(item.getStatus())) {
            throw new BizException(ResultCode.ITEM_OFF_SHELF);
        }
        return item;
    }

    // ---------------- internal ----------------

    private TradeItem requireItem(Long id) {
        TradeItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "标的不存在");
        }
        return item;
    }

    private void requireCodeFree(String code, Long selfId) {
        TradeItem exists = itemMapper.selectOne(new LambdaQueryWrapper<TradeItem>().eq(TradeItem::getCode, code));
        if (exists != null && !exists.getId().equals(selfId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "标的代码已存在");
        }
    }

    private void apply(TradeItem item, ItemSaveRequest req) {
        item.setCode(req.getCode());
        item.setName(req.getName());
        item.setCategory(req.getCategory());
        item.setMarket(req.getMarket());
        item.setReferencePrice(req.getReferencePrice());
        item.setRiskLevel(StringUtils.hasText(req.getRiskLevel()) ? req.getRiskLevel() : "MEDIUM");
        item.setFeeRate(req.getFeeRate() == null ? BigDecimal.ZERO : req.getFeeRate());
        item.setMinQuantity(req.getMinQuantity() == null ? BigDecimal.ONE : req.getMinQuantity());
        item.setRemark(req.getRemark());
        item.setAttributes(req.getAttributes());
    }

    private TradeItemPriceLog log(Long itemId, BigDecimal old, BigDecimal newPrice, Long uid) {
        TradeItemPriceLog l = new TradeItemPriceLog();
        l.setItemId(itemId);
        l.setOldPrice(old);
        l.setNewPrice(newPrice);
        l.setOperatorId(uid);
        return l;
    }
}
