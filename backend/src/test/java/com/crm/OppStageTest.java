package com.crm;

import com.crm.opportunity.service.OppService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 商机状态机纯函数单测（OPP-TS-01）：
 * 仅 OPEN 可流转至 1~5 开放阶段；终态（WON/LOST）禁止；6/7 仅经 win/lose 专用接口。
 */
class OppStageTest {

    @Test
    @DisplayName("OPEN 状态允许流转至开放阶段 1~5")
    void openCanTransitWithinOpenStages() {
        for (int s = 1; s <= 5; s++) {
            Assertions.assertTrue(OppService.canTransit("OPEN", s), "OPEN → " + s + " 应允许");
        }
    }

    @Test
    @DisplayName("OPEN 状态不允许流转至终态 6/7 或非法阶段")
    void openCannotTransitToTerminalOrInvalid() {
        Assertions.assertFalse(OppService.canTransit("OPEN", 6));
        Assertions.assertFalse(OppService.canTransit("OPEN", 7));
        Assertions.assertFalse(OppService.canTransit("OPEN", 0));
        Assertions.assertFalse(OppService.canTransit("OPEN", 8));
    }

    @Test
    @DisplayName("终态（WON/LOST）禁止任何流转")
    void terminalCannotTransit() {
        for (int s = 1; s <= 7; s++) {
            Assertions.assertFalse(OppService.canTransit("WON", s), "WON → " + s + " 应禁止");
            Assertions.assertFalse(OppService.canTransit("LOST", s), "LOST → " + s + " 应禁止");
        }
        Assertions.assertFalse(OppService.canTransit("OTHER", 1));
    }

    @Test
    @DisplayName("阶段默认赢率与 PRD 口径一致，成交 100% / 丢单 0%")
    void defaultWinRatesMatchPrd() {
        Map<Integer, Integer> rates = Map.of(1, 10, 2, 30, 3, 50, 4, 70, 5, 90, 6, 100, 7, 0);
        Assertions.assertEquals(rates, OppService.DEFAULT_WIN_RATES);
    }
}
