package com.crm.stat.service;

import com.crm.va.service.CustomerScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 批次5 定时任务：VA 评分每日重算 02:00（VA-1）+ STAT 聚合每日重建 02:30（ST-1）
 * 批次6 调整：STAT 每日重建改为增量（仅重算上次运行以来有变动的周期桶），手动触发仍为全量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Batch5Scheduler {

    private final CustomerScoreService scoreService;
    private final BusinessStatService statService;

    /** 上次 STAT 增量重建起点（内存态；重启后兜底 48h 窗口覆盖漏跑场景） */
    private final AtomicReference<LocalDateTime> lastStatRun = new AtomicReference<>();

    @Scheduled(cron = "0 0 2 * * *")
    public void recalcScores() {
        int n = scoreService.recalcAll();
        log.info("[Batch5] VA daily recalc done, {} customers scored", n);
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void rebuildStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = lastStatRun.getAndSet(now);
        if (since == null) {
            since = now.minusHours(48);
        }
        int n = statService.rebuildAllIncremental(since);
        log.info("[Batch5] STAT daily incremental rebuild done since {}, {} rows affected", since, n);
    }
}
