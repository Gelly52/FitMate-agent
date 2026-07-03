package com.itgeo.fitmate.api.agent.memory.longterm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fitmate.memory")
public class MemoryProperties {
    private boolean enabled = true;
    private int asyncPoolSize = 2;
    private Profile profile = new Profile();
    private Snapshot snapshot = new Snapshot();
    private Extract extract = new Extract();

    @Data
    public static class Profile {
        private int cacheTtlHours = 24;
    }

    @Data
    public static class Snapshot {
        private int windowDays = 14;
        private String cron = "0 0 2 * * *";
    }

    @Data
    public static class Extract {
        private int minConversationTurns = 3;
        private int minConversationChars = 100;
    }
}
