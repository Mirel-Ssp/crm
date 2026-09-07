package com.crm.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色权限点分配请求（全量覆盖）
 */
@Data
public class PermissionIdsRequest {

    private List<Long> permissionIds;
}
