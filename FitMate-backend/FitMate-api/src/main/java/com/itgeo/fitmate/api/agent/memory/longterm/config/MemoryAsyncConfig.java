package com.itgeo.fitmate.api.agent.memory.longterm.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class MemoryAsyncConfig {

    @Bean("memoryTaskExecutor")
    public ThreadPoolTaskExecutor memoryTaskExecutor(MemoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncPoolSize());
        executor.setMaxPoolSize(properties.getAsyncPoolSize() * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("memory-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
