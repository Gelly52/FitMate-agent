package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Wiki 编译异步执行入口。
 *
 * 通过 @Async("wikiCompileExecutor") 在独立线程池中执行编译任务，
 * 不阻塞调用方（如 RagController.uploadRagDoc）。
 *
 * 编译状态由 WikiCompileServiceImpl 在 t_wiki_compile_job 中维护；
 * 本 runner 仅作为异步触发器，并在异常时兜底标记 FAILED。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WikiCompileAsyncRunner {

    private final WikiCompileService wikiCompileService;
    private final WikiCompileJobMapper compileJobMapper;

    /**
     * 异步执行编译任务。
     *
     * @param jobId compile job 主键
     */
    @Async("wikiCompileExecutor")
    public void runAsync(Long jobId) {
        try {
            wikiCompileService.executeCompile(jobId);
        } catch (Exception e) {
            log.error("异步编译异常 job={}", jobId, e);
            try {
                WikiCompileJob job = compileJobMapper.selectById(jobId);
                if (job != null && !"SUCCESS".equals(job.getStatus())) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("异步执行异常: " + e.getMessage());
                    compileJobMapper.updateById(job);
                }
            } catch (Exception ex) {
                log.error("异步编译兜底状态更新失败 job={}", jobId, ex);
            }
        }
    }
}
