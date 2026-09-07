package com.crm;

import com.crm.common.security.UserState;
import com.crm.system.service.ScopeService;
import com.crm.system.service.UserStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 单元测试：数据范围判定（SYS-DV-02 完整版）
 * ALL → null（不过滤）| TEAM → 可见成员集合 | SELF → 仅本人
 */
@ExtendWith(MockitoExtension.class)
class ScopeServiceTest {

    @Mock
    private UserStateService userStateService;

    @InjectMocks
    private ScopeService scopeService;

    private UserState state(String dataScope, List<Long> visibleIds) {
        UserState st = new UserState();
        st.setUid(9L);
        st.setStatus("ACTIVE");
        st.setDataScope(dataScope);
        st.setVisibleUserIds(visibleIds);
        return st;
    }

    @Test
    @DisplayName("ALL → 返回 null（调用方不加过滤条件）")
    void allReturnsNull() {
        when(userStateService.load(9L)).thenReturn(state("ALL", null));
        assertNull(scopeService.visibleOwnerIds(9L));
        assertTrue(scopeService.canSee(9L, 12345L), "ALL 恒可见");
    }

    @Test
    @DisplayName("TEAM → 返回缓存中的可见成员集合")
    void teamReturnsVisibleIds() {
        when(userStateService.load(9L)).thenReturn(state("TEAM", List.of(9L, 10L, 11L)));
        assertEquals(List.of(9L, 10L, 11L), scopeService.visibleOwnerIds(9L));
        assertTrue(scopeService.canSee(9L, 10L));
        assertFalse(scopeService.canSee(9L, 99L));
    }

    @Test
    @DisplayName("TEAM 但可见集合为空 → 兜底仅本人")
    void teamFallbackSelf() {
        when(userStateService.load(9L)).thenReturn(state("TEAM", null));
        assertEquals(List.of(9L), scopeService.visibleOwnerIds(9L));
    }

    @Test
    @DisplayName("SELF → 仅本人")
    void selfOnly() {
        when(userStateService.load(9L)).thenReturn(state("SELF", List.of(9L, 10L)));
        assertEquals(List.of(9L), scopeService.visibleOwnerIds(9L));
        assertFalse(scopeService.canSee(9L, 10L), "SELF 看不到他人");
    }

    @Test
    @DisplayName("用户态缺失（缓存与库均无）→ 兜底仅本人")
    void missingStateFallbackSelf() {
        when(userStateService.load(9L)).thenReturn(null);
        assertEquals(List.of(9L), scopeService.visibleOwnerIds(9L));
    }
}
