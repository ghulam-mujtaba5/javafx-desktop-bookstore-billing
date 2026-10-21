package com.example.t;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordUtilTest {

    @Test
    @DisplayName("generateSalt should produce unique non-empty hex strings")
    void testGenerateSalt() {
        String salt1 = PasswordUtil.generateSalt();
        String salt2 = PasswordUtil.generateSalt();

        assertNotNull(salt1);
        assertNotNull(salt2);
        assertFalse(salt1.isEmpty());
        assertNotEquals(salt1, salt2);
        assertEquals(32, salt1.length()); // 16 bytes = 32 hex chars
    }

    @Test
    @DisplayName("hashPassword should produce consistent hash for same password and salt")
    void testHashPasswordDeterministic() {
        String password = "superSecretPassword123";
        String salt = PasswordUtil.generateSalt();

        String hash1 = PasswordUtil.hashPassword(password, salt);
        String hash2 = PasswordUtil.hashPassword(password, salt);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 = 32 bytes = 64 hex chars
    }

    @Test
    @DisplayName("Different passwords with same salt must yield different hashes")
    void testDifferentPasswordsProduceDifferentHashes() {
        String salt = PasswordUtil.generateSalt();
        String hashA = PasswordUtil.hashPassword("passwordA", salt);
        String hashB = PasswordUtil.hashPassword("passwordB", salt);

        assertNotEquals(hashA, hashB);
    }

    @Test
    @DisplayName("verifyPassword should return true for correct password and false for wrong password")
    void testVerifyPasswordWithSaltAndHash() {
        String password = "adminSecretPassword!";
        String salt = PasswordUtil.generateSalt();
        String expectedHash = PasswordUtil.hashPassword(password, salt);

        assertTrue(PasswordUtil.verifyPassword(password, salt, expectedHash));
        assertFalse(PasswordUtil.verifyPassword("wrongPassword", salt, expectedHash));
        assertFalse(PasswordUtil.verifyPassword(password, "differentSalt", expectedHash));
    }

    @Test
    @DisplayName("hashPassword(password) should format as salt:hash and verify correctly")
    void testCompositeHashAndVerify() {
        String password = "book shop";
        String composite = PasswordUtil.hashPassword(password);

        assertTrue(composite.contains(":"));
        assertTrue(PasswordUtil.verifyPassword(password, composite));
        assertFalse(PasswordUtil.verifyPassword("incorrect", composite));
    }

    @Test
    @DisplayName("verifyPassword should support legacy plaintext passwords during migration")
    void testLegacyPlaintextFallback() {
        String legacyPassword = "book shop";
        assertTrue(PasswordUtil.verifyPassword("book shop", legacyPassword));
        assertFalse(PasswordUtil.verifyPassword("wrong", legacyPassword));
    }

    @Test
    @DisplayName("sha256Hex and verifyPassword should support legacy unsalted 64-char hex SHA-256 hashes")
    void testSha256HexAndLegacyUnsaltedHexHashValidation() {
        String input = "admin";
        String shaHex = PasswordUtil.sha256Hex(input);
        assertNotNull(shaHex);
        assertEquals(64, shaHex.length());
        assertEquals("8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918", shaHex.toLowerCase());

        // Test verification with unsalted hex hash (both lowercase and uppercase)
        assertTrue(PasswordUtil.verifyPassword("admin", shaHex));
        assertTrue(PasswordUtil.verifyPassword("admin", shaHex.toUpperCase()));
        assertFalse(PasswordUtil.verifyPassword("wrongPassword", shaHex));
        assertFalse(PasswordUtil.verifyPassword("Admin", shaHex)); // case sensitive
    }

    @Test
    @DisplayName("Null checks should fail safely without unhandled runtime exceptions")
    void testNullSafety() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null, "salt"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword("pass", null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.sha256Hex(null));
        assertFalse(PasswordUtil.verifyPassword(null, "salt", "hash"));
        assertFalse(PasswordUtil.verifyPassword("pass", null, "hash"));
        assertFalse(PasswordUtil.verifyPassword("pass", "salt", null));
        assertFalse(PasswordUtil.verifyPassword(null, "stored"));
        assertFalse(PasswordUtil.verifyPassword("pass", null));
    }
}
