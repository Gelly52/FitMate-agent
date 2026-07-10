package com.itgeo.fitmate.api.wiki.application.scheduler;

import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Wiki 页面定时清理任务。
 * <p>
 * 清理策略：删除 {@code compiled_at} 早于 3 个月的 Wiki 页面，级联清理
 * 对应的 Redis 向量索引与关键词索引。
 * <p>
 * 执行时间：每天凌晨 3:30（错开 Agent 记录清理的 3:00）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiPageCleanupTask {

    private final WikiCompileService wikiCompileService;

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupExpiredWikiPages() {
        try {
            int deleted = wikiCompileService.cleanupExpiredPages();
            log.info("Wiki 过期页面定时清理完成，共清理 {} 页", deleted);
        } catch (Exception e) {
            log.error("Wiki 过期页面定时清理失败", e);
        }
    }
}
