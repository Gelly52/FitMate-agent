package com.itgeo.fitmate.api.agent.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Agent 异步执行线程池配置。
 *
 * 说明：
 * - 为 /agent/execute 的异步工作流提供专用线程池；
 * - 容量考量：单用户最多 3 个并发 run（RedisKeyConstants.AGENT_LOCK_SLOT_COUNT），
 *   多用户并发时需要更大容量，否则 CallerRunsPolicy 会把 Tomcat 接收线程拉去跑 Agent Loop；
 * - core=8 / max=16 可支持 2-5 个用户同时活跃且每用户多 run；
 * - queue=200 给短时 burst 留缓冲，超过仍走 CallerRunsPolicy 兜底；
 * - 关闭应用时等待已接收任务尽量执行完成。
 */
@Configuration
@EnableAsync
public class AgentAsyncConfig {

    /**
     * 创建 Agent 异步任务执行器。
     *
     * @return Agent 异步线程池执行器
     */
    @Bean("agentTaskExecutor")
    public ThreadPoolTaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("agent-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
