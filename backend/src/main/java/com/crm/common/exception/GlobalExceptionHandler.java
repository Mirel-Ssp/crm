package com.crm.common.exception;

import com.crm.common.api.Result;
import com.crm.common.api.ResultCode;


import com.crm.common.util.IpUtil;
import com.crm.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常收口（INF-DS-02 §2）
 * 预期内业务异常 WARN；未预期异常 ERROR 且不外泄堆栈
 * SYS-DV-04：方法级越权（@PreAuthorize 抛 AccessDeniedException 进 MVC）在此记录审计
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditService auditService;

    /** 业务异常：预期内，返回业务码 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("[BIZ] code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：取首个字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ResultCode.BAD_REQUEST.getMessage() : fe.getDefaultMessage();
        log.warn("[VALID] {}", msg);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** 越权访问（需求 §5 安全：拦截并审计，SYS-DV-04 落地） */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("[AUTHZ] {} {}", request.getMethod(), request.getRequestURI());
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        Long uid = a != null && a.getPrincipal() instanceof Long id ? id : null;
        auditService.record(uid, "access:denied", "URI", null,
                "{\"method\":\"" + request.getMethod() + "\",\"uri\":\"" + request.getRequestURI() + "\"}",
                IpUtil.clientIp(request));
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /** 未登录/令牌失效 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuth(AuthenticationException e) {
        log.warn("[AUTH] {}", e.getMessage());
        return Result.fail(ResultCode.UNAUTHORIZED);
    }

    /** 唯一索引冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("[DUP] {}", e.getMessage());
        return Result.fail(ResultCode.CONFLICT.getCode(), "数据重复");
    }

    /** 路径变量类型转换失败（如 /api/customers/abc）：返回 40000 而非 500 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[TYPE_MISMATCH] {} param={}", e.getMessage(), e.getName());
        return Result.fail(ResultCode.BAD_REQUEST);
    }

    /** 静态资源/接口路径不存在（如 /api/nonexistent）：返回 40400 而非 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("[NO_RESOURCE] {}", e.getMessage());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 请求体无法读取/解析（JSON 语法错误、字段类型不匹配、枚举非法、日期格式错误等）：
     * 属于客户端错误，返回 40000 而非 50000（HttpMessageNotReadableException 及其 Jackson 根因）。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("[BAD_BODY] {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求参数格式有误，请检查输入");
    }

    /** 未预期异常：堆栈仅入日志 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("[FATAL] unexpected error", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
