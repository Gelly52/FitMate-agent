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
 * - 使用 CallerRunsPolicy 作为兜底拒绝策略，避免任务被直接丢弃；
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
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
