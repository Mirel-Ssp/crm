package com.crm.trade.service;

import java.util.Map;
import java.util.Set;

/**
 * 订单六态状态机（V1 迁移契约为准，ORD-DV-02）
 * PENDING_CONFIRM → CONFIRMED → PARTIAL_DEALT → FULL_DEALT
 * PENDING_CONFIRM/CONFIRMED/PARTIAL_DEALT → CANCELLED（整单撤销）
 * PARTIAL_DEALT → PARTIAL_CANCELLED → CANCELLED
 * 终态：FULL_DEALT / CANCELLED
 */
public final class OrderStateMachine {

    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PARTIAL_DEALT = "PARTIAL_DEALT";
    public static final String FULL_DEALT = "FULL_DEALT";
    public static final String PARTIAL_CANCELLED = "PARTIAL_CANCELLED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            PENDING_CONFIRM, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PARTIAL_DEALT, FULL_DEALT, CANCELLED),
            PARTIAL_DEALT, Set.of(FULL_DEALT, PARTIAL_CANCELLED, CANCELLED),
            PARTIAL_CANCELLED, Set.of(CANCELLED),
            FULL_DEALT, Set.of(),
            CANCELLED, Set.of()
    );

    private OrderStateMachine() {
    }

    /** 是否允许从 from 流转到 to（终态不可流出） */
    public static boolean canTransit(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 非终态判断（可继续流转/核销） */
    public static boolean isOpen(String status) {
        return !FULL_DEALT.equals(status) && !CANCELLED.equals(status);
    }

    /** 校验并抛出业务异常（服务层统一入口） */
    public static void requireTransit(String from, String to) {
        if (!canTransit(from, to)) {
            throw new com.crm.common.exception.BizException(
                    com.crm.common.api.ResultCode.BAD_REQUEST.getCode(),
                    "订单状态不允许从 " + from + " 变更为 " + to);
        }
    }
}
