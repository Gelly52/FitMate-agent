package com.itgeo.fitmate.mcp;

import com.itgeo.fitmate.mcp.email.EmailTool;
import com.itgeo.fitmate.mcp.fitness.metrics.BodyMetricsTool;
import com.itgeo.fitmate.mcp.fitness.training.TrainingLogTool;
import com.itgeo.fitmate.mcp.rag.RagManageTool;
import com.itgeo.fitmate.mcp.time.DateTool;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author gzx
 * &#064;description:  应用入口
 * &#064;date  2024-05-20 10:00:00
 */
@MapperScan("com.itgeo.fitmate.mcp")
@SpringBootApplication
public class FitMateMcpServerApplication {

    //http://localhost:9060/sse

    public static void main(String[] args) {
        SpringApplication.run(FitMateMcpServerApplication.class, args);
    }

    // 注册MCP工具回调提供器
    @Bean
    public ToolCallbackProvider registMCPTools(
            DateTool dateTool,
            EmailTool emailTool,
            TrainingLogTool trainingLogTool,
            BodyMetricsTool bodyMetricsTool,
            RagManageTool ragManageTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        dateTool,
                        emailTool,
                        trainingLogTool,
                        bodyMetricsTool,
                        ragManageTool)
                .build();

    }

}
