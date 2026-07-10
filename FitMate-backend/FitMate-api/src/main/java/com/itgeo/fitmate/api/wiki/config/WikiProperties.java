package com.itgeo.fitmate.api.wiki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fitmate.wiki")
public class WikiProperties {

    /** 知识库总开关（前端 knowledgeBaseEnabled 对应） */
    private Boolean enabled = true;

    private Compile compile = new Compile();
    private Retrieval retrieval = new Retrieval();
    private Vectorstore vectorstore = new Vectorstore();
    private Keyword keyword = new Keyword();

    /** 页面保留月数，compiled_at 早于该阈值的页面将被清理 */
    private int retentionMonths = 3;
    /** 每个 space 的页面上限，超过时按最旧优先删除 */
    private int maxPagesPerSpace = 1000;

    @Data
    public static class Compile {
        private Integer asyncPoolSize = 3;
        private Integer maxRetry = 2;
    }

    @Data
    public static class Retrieval {
        private Integer defaultTopK = 4;
        private Integer maxTopK = 10;
        private Integer vectorRecallK = 8;
        private Integer keywordRecallK = 8;
        private Boolean rerankEnabled = true;
    }

    @Data
    public static class Vectorstore {
        private String indexName = "fitmate-wiki-vectorstore";
        private String prefix = "fitmate:wiki:embedding:";
    }

    @Data
    public static class Keyword {
        private String indexName = "fitmate-wiki-keyword-index";
        private String keyPrefix = "fitmate:wiki:chunk:";
    }
}
