package com.crm;

import com.crm.common.exception.BizException;
import com.crm.trade.service.OrderStateMachine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 订单六态状态机纯函数单测（ORD-DV-02）：
 * PENDING_CONFIRM → CONFIRMED → PARTIAL_DEALT → FULL_DEALT；
 * 非终态可取消；PARTIAL_DEALT → PARTIAL_CANCELLED → CANCELLED；终态禁出。
 */
class OrderStateMachineTest {

    @Test
    @DisplayName("主线流转允许：待审批→已确认→部分成交→全部成交")
    void happyPathAllowed() {
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.PENDING_CONFIRM, OrderStateMachine.CONFIRMED));
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.CONFIRMED, OrderStateMachine.PARTIAL_DEALT));
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.PARTIAL_DEALT, OrderStateMachine.FULL_DEALT));
        // B5 修正：一次性全额核销允许 CONFIRMED 直达 FULL_DEALT（advanceOnSettlement 语义，REM-DV-02）
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.CONFIRMED, OrderStateMachine.FULL_DEALT));
    }

    @Test
    @DisplayName("取消路径允许：非终态可取消，部分成交需经 PARTIAL_CANCELLED")
    void cancelPathAllowed() {
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.PENDING_CONFIRM, OrderStateMachine.CANCELLED));
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.CONFIRMED, OrderStateMachine.CANCELLED));
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.PARTIAL_DEALT, OrderStateMachine.PARTIAL_CANCELLED));
        Assertions.assertTrue(OrderStateMachine.canTransit(OrderStateMachine.PARTIAL_CANCELLED, OrderStateMachine.CANCELLED));
    }

    @Test
    @DisplayName("非法流转禁止：跳阶段/回退/自环")
    void illegalTransitForbidden() {
        Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.PENDING_CONFIRM, OrderStateMachine.PARTIAL_DEALT));
        Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.PENDING_CONFIRM, OrderStateMachine.FULL_DEALT));
        Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.PARTIAL_DEALT, OrderStateMachine.CONFIRMED));
        Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.PARTIAL_CANCELLED, OrderStateMachine.PARTIAL_DEALT));
        Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.CONFIRMED, OrderStateMachine.CONFIRMED));
    }

    @Test
    @DisplayName("终态（FULL_DEALT/CANCELLED）禁止任何流转")
    void terminalCannotTransit() {
        for (String to : new String[]{OrderStateMachine.PENDING_CONFIRM, OrderStateMachine.CONFIRMED,
                OrderStateMachine.PARTIAL_DEALT, OrderStateMachine.FULL_DEALT,
                OrderStateMachine.PARTIAL_CANCELLED, OrderStateMachine.CANCELLED}) {
            Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.FULL_DEALT, to), "FULL_DEALT → " + to + " 应禁止");
            Assertions.assertFalse(OrderStateMachine.canTransit(OrderStateMachine.CANCELLED, to), "CANCELLED → " + to + " 应禁止");
        }
    }

    @Test
    @DisplayName("isOpen：终态为 false，其余为 true")
    void isOpenSemantics() {
        Assertions.assertFalse(OrderStateMachine.isOpen(OrderStateMachine.FULL_DEALT));
        Assertions.assertFalse(OrderStateMachine.isOpen(OrderStateMachine.CANCELLED));
        Assertions.assertTrue(OrderStateMachine.isOpen(OrderStateMachine.PENDING_CONFIRM));
        Assertions.assertTrue(OrderStateMachine.isOpen(OrderStateMachine.CONFIRMED));
        Assertions.assertTrue(OrderStateMachine.isOpen(OrderStateMachine.PARTIAL_DEALT));
        Assertions.assertTrue(OrderStateMachine.isOpen(OrderStateMachine.PARTIAL_CANCELLED));
    }

    @Test
    @DisplayName("requireTransit：非法流转抛业务异常")
    void requireTransitThrows() {
        Assertions.assertThrows(BizException.class,
                () -> OrderStateMachine.requireTransit(OrderStateMachine.FULL_DEALT, OrderStateMachine.CANCELLED));
        Assertions.assertDoesNotThrow(
                () -> OrderStateMachine.requireTransit(OrderStateMachine.PENDING_CONFIRM, OrderStateMachine.CONFIRMED));
    }
}
