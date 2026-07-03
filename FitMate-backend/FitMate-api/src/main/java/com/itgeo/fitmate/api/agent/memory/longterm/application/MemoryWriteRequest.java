package com.itgeo.fitmate.api.agent.memory.longterm.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemoryWriteRequest {
    private Long userId;
    private String memoryType;
    private String content;
    private String metadataJson;
    private String source;
    private String contentHash;

    public String computeHash() {
        if (contentHash == null && content != null) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                contentHash = sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return contentHash;
    }
}
