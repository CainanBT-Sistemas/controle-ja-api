package com.cainanbt.softwares.controleja.services.billing;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class BillingTokens {
    private final byte[] key;
    private final byte[] encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public BillingTokens(BillingSettings settings) {
        try {
            key = settings.enabled() ? Base64.getDecoder().decode(settings.tokenKey()) : null;
            if (key != null && key.length != 32) throw new IllegalArgumentException();
            encryptionKey = key == null ? null : hmac("google-play-token-encryption");
        } catch (IllegalArgumentException | NullPointerException | GeneralSecurityException e) {
            throw new IllegalStateException("BILLING_GOOGLE_PLAY_TOKEN_KEY deve conter 32 bytes em Base64.");
        }
    }

    public static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponível.");
        }
    }

    public String accountId(UUID userId) {
        try {
            return HexFormat.of().formatHex(hmac("google-play-account:" + userId));
        } catch (GeneralSecurityException e) {
            throw BillingProblem.temporary();
        }
    }

    private byte[] hmac(String purpose) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] encrypt(String token, String fingerprint) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, nonce, fingerprint)
                    .doFinal(token.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw BillingProblem.temporary();
        }
    }

    public String decrypt(byte[] ciphertext, String fingerprint) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, fingerprint).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw BillingProblem.temporary();
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String fingerprint) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(fingerprint.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
