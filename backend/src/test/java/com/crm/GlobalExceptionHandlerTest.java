package com.crm;

import com.crm.common.api.Result;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.GlobalExceptionHandler;
import com.crm.system.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GlobalExceptionHandler 异常码收口单测（B6-P2 / 检测报告 P1 缺陷 #1 #2）
 * 验证非数字路径变量 → 40000、未知 API 路径 → 40400，不再落入 catch-all 返回 50000
 */
class GlobalExceptionHandlerTest {

    private final AuditService auditService = mock(AuditService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(auditService);

    @Test
    @DisplayName("非数字路径变量（MethodArgumentTypeMismatchException）返回 40000 而非 50000")
    void typeMismatchReturns40000() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, null);
        Result<Void> r = handler.handleTypeMismatch(ex);
        assertThat(r.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(r.getCode()).isEqualTo(40000);
    }

    @Test
    @DisplayName("未知 API 路径（NoResourceFoundException）返回 40400 而非 50000")
    void noResourceReturns40400() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/nonexistent");
        Result<Void> r = handler.handleNoResource(ex);
        assertThat(r.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(r.getCode()).isEqualTo(40400);
    }

    @Test
    @DisplayName("兜底异常仍返回 50000（回归保护）")
    void unknownReturns50000() {
        Result<Void> r = handler.handleUnknown(new RuntimeException("unexpected"));
        assertThat(r.getCode()).isEqualTo(ResultCode.SYSTEM_ERROR.getCode());
        assertThat(r.getCode()).isEqualTo(50000);
    }
}
