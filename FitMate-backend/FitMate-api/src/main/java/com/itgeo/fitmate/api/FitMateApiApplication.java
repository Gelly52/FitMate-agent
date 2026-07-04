package com.itgeo.fitmate.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 应用启动入口，负责启动 Spring Boot 并扫描 Mapper 接口。
 */
@MapperScan({
        "com.itgeo.fitmate.api.auth.infrastructure.mapper",
        "com.itgeo.fitmate.api.chat.infrastructure.mapper",
        "com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper",
        "com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper",
        "com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper",
        "com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper",
        "com.itgeo.fitmate.api.fitness.training.infrastructure.mapper",
        "com.itgeo.fitmate.api.rag.infrastructure.mapper",
        "com.itgeo.fitmate.api.agent.infrastructure.mapper",
        "com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper",
        "com.itgeo.fitmate.api.wiki.infrastructure.mapper"
})
@EnableAsync
@SpringBootApplication(exclude = {
        RedisVectorStoreAutoConfiguration.class,
        TransformersEmbeddingModelAutoConfiguration.class
})
public class FitMateApiApplication {

    /**
     * 启动应用前先加载 .env 配置，并写入系统属性供后续统一读取。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 加载项目环境变量文件，不存在时忽略。
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        // 将 .env 配置写入系统属性，便于 Spring 与业务代码统一获取。
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(FitMateApiApplication.class, args);
    }
}

//思考过程显示
//长期记忆功能
