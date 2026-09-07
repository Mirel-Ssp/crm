package com.crm.system.service;

import com.crm.system.entity.SysAuditLog;
import com.crm.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计写入服务（SYS-DV-04）：旁路写库，任何失败仅 WARN 不阻断业务（INF-DS-02 §4.3）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final SysAuditLogMapper auditLogMapper;

    /**
     * 记录审计日志
     * @param detailJson JSON 字符串或纯文本，可为 null
     */
    public void record(Long uid, String action, String targetType, Long targetId, String detailJson, String ip) {
        try {
            SysAuditLog row = new SysAuditLog();
            row.setUserId(uid == null ? 0L : uid);
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetId(targetId);
            row.setDetail(detailJson);
            row.setIp(ip);
            auditLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("[AUDIT] 审计写入失败（不影响业务）action={} user={}: {}", action, uid, e.getMessage());
        }
    }
}
