package com.crm.svc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.svc.entity.ServiceTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceTicketMapper extends BaseMapper<ServiceTicket> {
}
