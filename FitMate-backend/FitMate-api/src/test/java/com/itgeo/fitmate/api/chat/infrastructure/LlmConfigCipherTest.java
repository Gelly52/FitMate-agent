package com.itgeo.fitmate.api.chat.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigCipherTest {

    private LlmConfigCipher cipher;

    @BeforeEach
    void setUp() {
        // 32 字节密钥 Base64
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        String encryptionKey = Base64.getEncoder().encodeToString(key);
        cipher = new LlmConfigCipher(encryptionKey);
        cipher.init();
    }

    @Test
    void encrypt_then_decrypt_roundTrip() {
        String plain = "your_openai_api_key";
        String encrypted = cipher.encrypt(plain);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, cipher.decrypt(encrypted));
    }

    @Test
    void decrypt_empty_returns_empty() {
        assertEquals("", cipher.decrypt(""));
        assertEquals("", cipher.decrypt(null));
    }

    @Test
    void mask_keeps_prefix3_and_suffix4() {
        String plain = "sk-abc123def456e05f";
        String masked = cipher.mask(plain);
        assertTrue(masked.startsWith("sk-"));
        assertTrue(masked.endsWith("e05f"));
        assertTrue(masked.contains("****"));
    }

    @Test
    void mask_shortInput_returns_asterisks() {
        assertEquals("****", cipher.mask("ab"));
        assertEquals("****", cipher.mask(""));
    }
}
