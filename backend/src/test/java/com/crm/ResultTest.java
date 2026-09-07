package com.crm;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.api.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应体单元测试（INF-DS-02 §1）
 */
class ResultTest {

    @Test
    @DisplayName("成功响应：code=0 且携带数据")
    void ok() {
        Result<String> r = Result.ok("hello");
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("失败响应：业务码与消息透传")
    void fail() {
        Result<Void> r = Result.fail(ResultCode.UNAUTHORIZED);
        assertThat(r.getCode()).isEqualTo(40100);
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("PageResult 字段完整")
    void pageResult() {
        PageResult<String> p = new PageResult<>(java.util.List.of("a"), 11, 2, 10, 2);
        assertThat(p.getTotal()).isEqualTo(11);
        assertThat(p.getPages()).isEqualTo(2);
        assertThat(p.getList()).hasSize(1);
    }
}
