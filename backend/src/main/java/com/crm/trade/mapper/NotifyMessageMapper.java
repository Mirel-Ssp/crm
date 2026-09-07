package com.crm.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.trade.entity.NotifyMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotifyMessageMapper extends BaseMapper<NotifyMessage> {

    /** 指定角色编码的启用用户 id（通知接收人，如 MANAGER） */
    @Select("SELECT u.id FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id "
            + "JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE r.code = #{roleCode} AND u.status = 'ACTIVE'")
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);
}
