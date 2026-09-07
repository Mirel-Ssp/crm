package com.crm.common.audit;

import com.crm.common.util.IpUtil;
import com.crm.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 审计切面（SYS-DV-04）：@AuditLog 方法成功返回后同步旁路写 sys_audit_log
 * - 同步写（当前量级单条 insert 开销可忽略，避免 @Async 的 SecurityContext 传递复杂度）
 * - 整体 try-catch：审计失败不影响业务
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final AuditService auditService;

    @AfterReturning("@annotation(auditLog)")
    public void afterReturning(JoinPoint jp, AuditLog auditLog) {
        try {
            Long targetId = resolveTargetId(jp, auditLog.targetId());
            Long uid = currentUid();
            auditService.record(uid, auditLog.action(), auditLog.targetType(), targetId, null, currentIp());
        } catch (Exception e) {
            log.warn("[AUDIT] 切面处理失败（不影响业务）action={}: {}", auditLog.action(), e.getMessage());
        }
    }

    /** SpEL 求值 targetId（如 "#id"），失败返回 null */
    private Long resolveTargetId(JoinPoint jp, String spel) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            Method method = ((org.aspectj.lang.reflect.MethodSignature) jp.getSignature()).getMethod();
            MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(
                    null, method, jp.getArgs(), NAME_DISCOVERER);
            Object val = PARSER.parseExpression(spel).getValue(ctx);
            if (val instanceof Number n) {
                return n.longValue();
            }
            return val == null ? null : Long.parseLong(val.toString());
        } catch (Exception e) {
            log.warn("[AUDIT] targetId SpEL 求值失败: {} ({})", spel, e.getMessage());
            return null;
        }
    }

    private Long currentUid() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Long uid) {
            return uid;
        }
        return null;
    }

    private String currentIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : IpUtil.clientIp(attrs.getRequest());
    }
}
