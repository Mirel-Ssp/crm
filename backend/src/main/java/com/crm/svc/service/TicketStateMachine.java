package com.crm.svc.service;

import java.util.Map;
import java.util.Set;

/**
 * 工单四态状态机（CRM-S1 验收标准：状态机完整）
 * OPEN(待处理) → PROCESSING(处理中) → RESOLVED(已解决) → CLOSED(已关闭)
 * OPEN → RESOLVED 允许（快速办结）；CLOSED 为终态
 */
public final class TicketStateMachine {

    public static final String OPEN = "OPEN";
    public static final String PROCESSING = "PROCESSING";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            OPEN, Set.of(PROCESSING, RESOLVED),
            PROCESSING, Set.of(RESOLVED),
            RESOLVED, Set.of(CLOSED),
            CLOSED, Set.of()
    );

    private TicketStateMachine() {
    }

    /** 是否允许从 from 流转到 to（终态不可流出） */
    public static boolean canTransit(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 终态判断（已关闭不可再操作） */
    public static boolean isTerminal(String status) {
        return CLOSED.equals(status);
    }

    /** 是否允许回访评分（RESOLVED/CLOSED） */
    public static boolean canRate(String status) {
        return RESOLVED.equals(status) || CLOSED.equals(status);
    }
}
