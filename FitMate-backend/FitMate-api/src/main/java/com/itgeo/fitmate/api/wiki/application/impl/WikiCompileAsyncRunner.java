package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserMapper;
import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class WikiCompileAsyncRunner {

    private final WikiCompileService wikiCompileService;
    private final WikiCompileJobMapper compileJobMapper;
    private final UserMapper userMapper;

    @Async("wikiCompileExecutor")
    public void runAsync(Long jobId) {
        WikiCompileJob job = null;
        try {
            job = compileJobMapper.selectById(jobId);
            if (job != null && job.getCreatedByUserId() != null) {
                User user = userMapper.selectById(job.getCreatedByUserId());
                if (user != null) {
                    AuthenticatedUserContext ctx = AuthenticatedUserContext.builder()
                            .userId(user.getId())
                            .userKey(user.getUserKey())
                            .username(user.getUsername())
                            .nickname(user.getNickname())
                            .phone(user.getPhone())
                            .email(user.getUsername())
                            .build();
                    UserContextHolder.set(ctx);
                    log.info("Wiki 异步编译设置用户上下文: userId={}", user.getId());
                }
            }
            wikiCompileService.executeCompile(jobId);
        } catch (Exception e) {
            log.error("异步编译异常 job={}", jobId, e);
            try {
                if (job == null) {
                    job = compileJobMapper.selectById(jobId);
                }
                if (job != null && !"SUCCESS".equals(job.getStatus())) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("异步执行异常: " + e.getMessage());
                    compileJobMapper.updateById(job);
                }
            } catch (Exception ex) {
                log.error("异步编译兜底状态更新失败 job={}", jobId, ex);
            }
        } finally {
            UserContextHolder.clear();
        }
    }
}
