package com.crm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.system.entity.SysAuditLog;

/**
 * SysAuditLog Mapper（旁路写入，仅 insert + 查询）
 */
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
