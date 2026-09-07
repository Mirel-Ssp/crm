package com.crm.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应码（INF-DS-02 §1.2 错误码段规划）
 * A0xxx 通用/系统 | C0xxx 基础CRM | B0xxx 交易业务 | S0xxx 系统管理 | D0xxx 报表
 * 新增业务错误码必须落在对应码段内，禁止跳段
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),

    // ===== A0 通用/系统 =====
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限执行该操作"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "数据冲突，请刷新后重试"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试"),

    // ===== B0 交易业务（占位，交易模块实现时扩充） =====
    ITEM_OFF_SHELF(10001, "交易标的已下架"),
    ORDER_STATE_ILLEGAL(20001, "订单当前状态不允许该操作"),
    ORDER_AMOUNT_OVER_LIMIT(20002, "金额超出单笔限额"),
    REMIT_WRITE_OFF_OVER(30001, "汇款核销金额超出剩余可核金额"),
    REMIT_CONFIRMED_ALREADY(30002, "该汇款已确认到账，请勿重复操作"),

    // ===== S0 系统管理（映射规则：S0xyz -> 6xyz，60000-69999 段；变更跟踪记录已登记） =====
    ORG_NOT_FOUND(60001, "组织不存在"),
    ORG_HAS_CHILDREN_OR_MEMBERS(60002, "组织下存在子节点或成员，不可删除"),
    ROLE_NOT_FOUND(60101, "角色不存在"),
    PERMISSION_NOT_FOUND(60102, "权限点不存在或冲突"),
    ROLE_IN_USE(60103, "角色仍被用户引用，不可删除"),
    USER_NOT_FOUND(60201, "用户不存在或已停用"),
    ADMIN_PROTECTED(60202, "内置管理员账号不可停用或删除"),
    SELF_OPERATION_FORBIDDEN(60203, "不能对本人执行该操作"),
    DICT_NOT_FOUND(60401, "字典项不存在"),
    DICT_DUPLICATED(60402, "字典项重复");

    private final int code;
    private final String message;
}
