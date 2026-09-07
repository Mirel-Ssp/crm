package com.crm.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户角色分配请求（全量覆盖）
 */
@Data
public class RoleIdsRequest {

    private List<Long> roleIds;
}
