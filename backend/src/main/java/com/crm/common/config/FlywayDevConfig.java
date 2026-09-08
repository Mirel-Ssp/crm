package com.crm.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

/**
 * dev/test 环境 Flyway 策略：migrate 前先 repair。
 * 作用：本地开发中修改已执行过的迁移脚本（如补 CREATE EXTENSION、ON CONFLICT）后，
 * repair 会自动对齐 flyway_schema_history 中的 checksum 并清理失败记录，
 * 避免 “Migration checksum mismatch” 阻断启动。
 * 仅 dev profile 生效；prod 严格校验、不做 repair（防止掩盖迁移事故）。
 */
@Configuration
@Profile("dev")
public class FlywayDevConfig {

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
