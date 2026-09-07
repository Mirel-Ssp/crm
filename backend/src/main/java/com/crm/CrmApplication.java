package com.crm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM 客户管理系统 · 模块化单体后端启动类
 * 业务域分包：system/customer/lead/opportunity/rms/ticket/report/trade/svc/va/stat
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.crm.**.mapper")
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
