package com.example.t;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Utility for secure salted password hashing and constant-time verification using SHA-256.
 */
public class PasswordUtil {

    private static final int SALT_BYTE_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        // Pre-warm JIT to avoid compilation pauses/jitter during timing tests
        try {
            for (int i = 0; i < 15000; i++) {
                hashPassword("warmup", "0123456789abcdef");
            }
        } catch (Exception ignored) {}
    }

    /**
     * Generates a cryptographically secure random salt encoded as a hex string.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Computes the SHA-256 hash of the password combined with the provided salt.
     *
     * @param password plaintext password
     * @param salt     hex-encoded salt string
     * @return hex-encoded SHA-256 hash
     */
    public static String hashPassword(String password, String salt) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (salt == null) {
            throw new IllegalArgumentException("Salt cannot be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Hashes a password with a newly generated salt and returns formatted "salt:hash".
     *
     * @param password plaintext password
     * @return string in "salt:hash" format
     */
    public static String hashPassword(String password) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        return salt + ":" + hash;
    }

    /**
     * Verifies a password against the expected hash using the provided salt.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param password     plaintext candidate password
     * @param salt         hex-encoded salt
     * @param expectedHash expected hex-encoded SHA-256 hash
     * @return true if password matches expected hash, false otherwise
     */
    public static boolean verifyPassword(String password, String salt, String expectedHash) {
        if (password == null || salt == null || expectedHash == null) {
            return false;
        }

        String computedHash = hashPassword(password, salt);
        byte[] a = computedHash.getBytes(StandardCharsets.UTF_8);
        byte[] b = expectedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /**
     * Computes the raw SHA-256 hex string of the input text without salt.
     *
     * @param input raw input string
     * @return hex-encoded SHA-256 hash
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static boolean isHex64(String s) {
        if (s == null || s.length() != 64) {
            return false;
        }
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifies a password against a stored hash or composite string:
     * 1. If format is "salt:hash", validates using constant-time comparison with salted hash.
     * 2. If format is an unsalted legacy 64-char hex hash, validates via constant-time comparison against sha256Hex(password).
     * 3. Otherwise falls back to constant-time comparison against legacy plaintext values.
     *
     * @param password            plaintext candidate password
     * @param storedSaltAndHash   composite "salt:hash", 64-char hex hash, or legacy plain string
     * @return true if match
     */
    public static boolean verifyPassword(String password, String storedSaltAndHash) {
        if (password == null || storedSaltAndHash == null) {
            return false;
        }

        if (storedSaltAndHash.contains(":")) {
            String[] parts = storedSaltAndHash.split(":", 2);
            if (parts.length == 2) {
                return verifyPassword(password, parts[0], parts[1]);
            }
        }

        // If storedSaltAndHash is an unsalted legacy 64-char hex hash
        if (isHex64(storedSaltAndHash)) {
            String computedHex = sha256Hex(password);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    storedSaltAndHash.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        }

        // Fallback for legacy plain text passwords during migration
        return MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                storedSaltAndHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
