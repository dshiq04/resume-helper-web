package com.jobtrack.application.service;

import com.jobtrack.system.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 简历文件落盘加密（AES-256-GCM），密钥由 config.json 中的 storage.secret 派生。
 * 文件内容格式：12 字节 IV + 密文。
 */
@Component
public class FileCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public FileCrypto(AppProperties properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.getStorage().getSecret().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化文件加密密钥", exception);
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] output = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, output, IV_LENGTH, encrypted.length);
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("文件加密失败", exception);
        }
    }

    public byte[] decrypt(byte[] payload) {
        try {
            if (payload.length <= IV_LENGTH) {
                throw new IllegalStateException("文件内容不完整");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("文件解密失败", exception);
        }
    }
}
