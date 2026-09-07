package com.crm.common.web;

import com.crm.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查（INF-DV-01 交付标准：工程可起 + 健康检查可访问）
 * /api/health 额外探测数据库连通性（SELECT 1）
 */
@Tag(name = "INF 基础设施")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "健康检查（含数据库探测）")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        String db = "DOWN";
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            db = "UP";
        } catch (Exception ignored) {
            // 探测失败不影响应用存活，仅上报 db=DOWN
        }
        return Result.ok(Map.of("status", "UP", "db", db));
    }
}
