package com.itgeo.fitmate.api.chat.infrastructure;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM API Key 加解密与脱敏工具（AES-256/GCM）。
 * 密钥来自 fitmate.llm.encryption-key（32 字节 Base64）。
 * 注意：不使用 @Component，由 LlmConfigBeanConfig 显式注册 Bean。
 */
@Slf4j
public class LlmConfigCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final String encryptionKeyBase64;
    private SecretKeySpec secretKey;

    public LlmConfigCipher(String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    @PostConstruct
    public void init() {
        if (StrUtil.isBlank(encryptionKeyBase64)) {
            throw new IllegalStateException("fitmate.llm.encryption-key 未配置，请通过 env LLM_ENCRYPTION_KEY 注入 32 字节 Base64 密钥");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("fitmate.llm.encryption-key 解码后必须为 32 字节，当前=" + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("LlmConfigCipher 初始化成功");
    }

    /** 加密明文 → Base64(IV + ciphertext) */
    public String encrypt(String plain) {
        if (StrUtil.isBlank(plain)) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /** 解密 Base64(IV + ciphertext) → 明文；空值返回空 */
    public String decrypt(String encrypted) {
        if (StrUtil.isBlank(encrypted)) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    /** 脱敏：保留前 3 + 后 4，中间 ****；过短直接 **** */
    public String mask(String plain) {
        if (StrUtil.isBlank(plain) || plain.length() < 8) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }
}
