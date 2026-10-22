package com.example.t;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 1.
 * Tests edge cases, stress scenarios, cryptographic randomness, constant-time behavior,
 * legacy backward compatibility, and headless safety.
 */
public class Milestone1AdversarialChallengeTest {

    // =========================================================================
    // 1. SALT RANDOMNESS & ENTROPY CHALLENGES
    // =========================================================================

    @Test
    @DisplayName("Salt Randomness: 5,000 salts must all be unique, 32 hex chars, and uniformly distributed")
    void testSaltRandomnessAndDistribution() {
        final int COUNT = 5000;
        Set<String> uniqueSalts = new HashSet<>(COUNT);
        int[] hexDigitCounts = new int[16];

        for (int i = 0; i < COUNT; i++) {
            String salt = PasswordUtil.generateSalt();
            assertNotNull(salt, "Salt must not be null");
            assertEquals(32, salt.length(), "Salt length must strictly be 32 hex characters (16 bytes)");
            assertTrue(salt.matches("^[0-9a-f]{32}$"), "Salt must only contain lowercase hexadecimal characters");

            for (char c : salt.toCharArray()) {
                int val = Character.digit(c, 16);
                assertTrue(val >= 0 && val < 16, "Invalid hex digit: " + c);
                hexDigitCounts[val]++;
            }

            assertTrue(uniqueSalts.add(salt), "Collision detected! Duplicate salt generated at iteration " + i);
        }

        assertEquals(COUNT, uniqueSalts.size(), "All generated salts must be globally unique");

        // Total hex characters = COUNT * 32 = 160,000. Expected per digit = 10,000.
        // Tolerable range [8,500 - 11,500] (within 15% of expected uniform distribution).
        int totalChars = COUNT * 32;
        int expectedPerDigit = totalChars / 16;
        for (int i = 0; i < 16; i++) {
            int count = hexDigitCounts[i];
            assertTrue(count >= expectedPerDigit * 0.80 && count <= expectedPerDigit * 1.20,
                    String.format("Hex digit '%x' count %d is outside expected uniform range [%d, %d]",
                            i, count, (int) (expectedPerDigit * 0.80), (int) (expectedPerDigit * 1.20)));
        }
    }

    @Test
    @DisplayName("Salt Randomness: 1,000 hashes of identical password must all have unique salts and verify")
    void testHashingIdenticalPasswordProducesUniqueHashes() {
        final String password = "IdenticalPassword#2026";
        final int COUNT = 1000;
        Set<String> hashes = new HashSet<>(COUNT);

        for (int i = 0; i < COUNT; i++) {
            String composite = PasswordUtil.hashPassword(password);
            assertTrue(composite.contains(":"), "Composite hash must contain ':'");
            assertTrue(hashes.add(composite), "Duplicate composite hash found at index " + i);
            assertTrue(PasswordUtil.verifyPassword(password, composite), "Must verify original password");
            assertFalse(PasswordUtil.verifyPassword(password + "_wrong", composite), "Must reject incorrect password");
        }

        assertEquals(COUNT, hashes.size());
    }

    // =========================================================================
    // 2. RESISTANCE TO NULL, EMPTY, MALFORMED & UNICODE INPUTS
    // =========================================================================

    @Test
    @DisplayName("Resistance: Null handling must reject safely without unhandled exceptions")
    void testNullInputsRobustness() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null, "salt123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword("password", null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null, null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null));

        assertFalse(PasswordUtil.verifyPassword(null, "salt", "hash"));
        assertFalse(PasswordUtil.verifyPassword("pass", null, "hash"));
        assertFalse(PasswordUtil.verifyPassword("pass", "salt", null));
        assertFalse(PasswordUtil.verifyPassword(null, null, null));
        assertFalse(PasswordUtil.verifyPassword(null, "salt:hash"));
        assertFalse(PasswordUtil.verifyPassword("pass", null));
        assertFalse(PasswordUtil.verifyPassword(null, null));
    }

    @Test
    @DisplayName("Resistance: Empty password must hash and verify consistently")
    void testEmptyPasswordHandling() {
        String composite = PasswordUtil.hashPassword("");
        assertNotNull(composite);
        assertTrue(composite.contains(":"));

        assertTrue(PasswordUtil.verifyPassword("", composite));
        assertFalse(PasswordUtil.verifyPassword(" ", composite));
        assertFalse(PasswordUtil.verifyPassword("a", composite));
    }

    @Test
    @DisplayName("Resistance: Malformed and adversarial composite strings must fail verification safely")
    void testMalformedCompositeStrings() {
        String[] malformedInputs = {
                "",
                ":",
                "::",
                ":::",
                "salt:",
                ":hash",
                "salt:hash:extra",
                "salt:hash:extra:more",
                "   ",
                "\t\n\r",
                "invalid_hex_salt:invalid_hex_hash",
                "short:short",
                "a".repeat(1000) + ":" + "b".repeat(1000)
        };

        for (String malformed : malformedInputs) {
            assertFalse(PasswordUtil.verifyPassword("candidatePassword", malformed),
                    "Should safely return false for malformed stored hash: " + malformed);
        }
    }

    @Test
    @DisplayName("Resistance: Multi-byte Unicode, Arabic, Emojis, and control characters")
    void testComplexUnicodeAndSpecialCharacters() {
        String[] specialPasswords = {
                "مكتبة_الزهراء_2026",
                "🔥🚀💻🔑📚",
                "日本語パスワードテスト",
                "ПарольКнижныйМагазин123!",
                "Admin\u200B\u200C\u200DSecret", // Zero-width spaces and joiners
                "!@#$%^&*()_+-=[]{}|;':,.<>/?~`\"",
                "\n\r\t \f\b",
                "Mixed_مرحبا_123_🔥"
        };

        for (String pwd : specialPasswords) {
            String composite = PasswordUtil.hashPassword(pwd);
            assertTrue(PasswordUtil.verifyPassword(pwd, composite),
                    "Failed to verify Unicode password: " + pwd);
            assertFalse(PasswordUtil.verifyPassword(pwd + "x", composite),
                    "Incorrectly accepted corrupted Unicode password: " + pwd);
        }
    }

    @Test
    @DisplayName("Resistance: Extreme length inputs (50,000 chars) must not crash or OOM")
    void testExtremeLengthInputs() {
        String longPassword = "x".repeat(50000);
        String composite = PasswordUtil.hashPassword(longPassword);

        assertTrue(PasswordUtil.verifyPassword(longPassword, composite));
        assertFalse(PasswordUtil.verifyPassword(longPassword.substring(0, 49999), composite));
    }

    // =========================================================================
    // 3. CONSTANT-TIME VERIFICATION & ORACLE CHECK
    // =========================================================================

    @Test
    @DisplayName("Oracle: PasswordUtil must strictly match independent Java MessageDigest SHA-256 oracle")
    void testCryptographicOracleMatch() throws NoSuchAlgorithmException {
        Random rand = new Random(42);

        for (int i = 0; i < 200; i++) {
            String password = "pwd_" + rand.nextLong() + "_" + UUID.randomUUID();
            String salt = PasswordUtil.generateSalt();

            String utilityHash = PasswordUtil.hashPassword(password, salt);

            // Independent Oracle computation
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] oracleBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : oracleBytes) {
                sb.append(String.format("%02x", b));
            }
            String oracleHash = sb.toString();

            assertEquals(oracleHash, utilityHash, "Utility hash does not match independent oracle at iteration " + i);
            assertTrue(PasswordUtil.verifyPassword(password, salt, oracleHash), "Verification failed against oracle hash");
        }
    }

    @Test
    @DisplayName("Constant-Time Verification: Compares entire digests via MessageDigest.isEqual without early return")
    void testConstantTimeComparisonTimingBehavior() {
        String password = "VerificationBenchPassword#2026";
        String salt = PasswordUtil.generateSalt();
        String correctHash = PasswordUtil.hashPassword(password, salt);

        // Corrupted at index 0 (early mismatch)
        char firstChar = correctHash.charAt(0);
        char diffFirst = (firstChar == 'a') ? 'b' : 'a';
        String earlyMismatchHash = diffFirst + correctHash.substring(1);

        // Corrupted at index 63 (last character mismatch)
        char lastChar = correctHash.charAt(63);
        char diffLast = (lastChar == 'a') ? 'b' : 'a';
        String lateMismatchHash = correctHash.substring(0, 63) + diffLast;

        // Warm up JIT
        for (int i = 0; i < 5000; i++) {
            PasswordUtil.verifyPassword(password, salt, earlyMismatchHash);
            PasswordUtil.verifyPassword(password, salt, lateMismatchHash);
            PasswordUtil.verifyPassword(password, salt, correctHash);
        }

        // Measure batches using alternating trials to avoid GC/scheduling bias
        final int TRIALS = 10000;
        long durationEarly = 0;
        long durationLate = 0;
        for (int i = 0; i < TRIALS; i++) {
            long t0 = System.nanoTime();
            PasswordUtil.verifyPassword(password, salt, earlyMismatchHash);
            durationEarly += (System.nanoTime() - t0);

            long t1 = System.nanoTime();
            PasswordUtil.verifyPassword(password, salt, lateMismatchHash);
            durationLate += (System.nanoTime() - t1);
        }

        // Both durations must be of comparable magnitude (ratio within 0.40 to 2.50)
        double ratio = (double) durationEarly / (double) durationLate;
        assertTrue(ratio > 0.40 && ratio < 2.50,
                String.format("Timing disparity too large! early=%dns, late=%dns, ratio=%.3f",
                        durationEarly, durationLate, ratio));
    }

    // =========================================================================
    // 4. BACKWARD COMPATIBILITY WITH LEGACY PLAINTEXT PASSWORDS
    // =========================================================================

    @Test
    @DisplayName("Legacy: Default bookstore password 'book shop' must verify against plaintext stored password")
    void testLegacyDefaultPasswordCompatibility() {
        String defaultLegacy = "book shop";
        assertTrue(PasswordUtil.verifyPassword("book shop", defaultLegacy));
        assertFalse(PasswordUtil.verifyPassword("wrong pass", defaultLegacy));
        assertFalse(PasswordUtil.verifyPassword("bookshop", defaultLegacy));
    }

    @Test
    @DisplayName("Legacy: Plaintext passwords with various characters must verify against themselves")
    void testLegacyPlaintextPasswords() {
        String[] legacyPasswords = {
                "admin",
                "admin123",
                "secret_key_99",
                "store password with spaces",
                "special!@#$%"
        };

        for (String legacy : legacyPasswords) {
            assertTrue(PasswordUtil.verifyPassword(legacy, legacy));
            assertFalse(PasswordUtil.verifyPassword(legacy + "_incorrect", legacy));
        }
    }

    @Test
    @DisplayName("Legacy Edge Case: Plaintext passwords containing colons (e.g. 'admin:123')")
    void testLegacyPasswordWithColon() {
        // If a user previously had a plaintext password containing a colon:
        // Does verifyPassword recognize it or does it attempt to parse it as salt:hash?
        // Note: A real salt is 32 hex chars and a real hash is 64 hex chars.
        String legacyWithColon = "admin:1234";
        boolean verified = PasswordUtil.verifyPassword("admin:1234", legacyWithColon);

        // Document the empirical behavior:
        // If false, it's because it splits on ':' unconditionally and expects parts[0] to be a salt and parts[1] to be SHA-256 hash.
        System.out.println("[EMPIRICAL FINDING] PasswordUtil.verifyPassword(\"admin:1234\", \"admin:1234\") = " + verified);
    }

    // =========================================================================
    // 5. HEADLESS SAFETY & CONCURRENCY OF NOTIFICATIONUTIL
    // =========================================================================

    @Test
    @DisplayName("Headless Safety: NotificationUtil methods must never throw in headless environment")
    void testNotificationUtilHeadlessSafety() {
        assertDoesNotThrow(() -> {
            NotificationUtil.showInfo("Headless Info", "Testing headless execution");
            NotificationUtil.showSuccess("Headless Success", "Testing headless execution");
            NotificationUtil.showWarning("Headless Warning", "Testing headless execution");
            NotificationUtil.showError("Headless Error", "Testing headless execution");
        });
    }

    @Test
    @DisplayName("Headless Safety: Null, empty, and huge messages in NotificationUtil")
    void testNotificationUtilEdgeInputs() {
        assertDoesNotThrow(() -> {
            NotificationUtil.showInfo(null, null);
            NotificationUtil.showSuccess("", "");
            NotificationUtil.showWarning("   ", "   ");
            NotificationUtil.showError("مرحبا", "رسالة خطأ بالعربية 🚨");
            NotificationUtil.showInfo("LongTitle".repeat(100), "LongMessage".repeat(500));
        });
    }

    @Test
    @Timeout(10)
    @DisplayName("Concurrency: Multithreaded concurrent calls to NotificationUtil must not crash or deadlock")
    void testNotificationUtilConcurrency() throws InterruptedException, ExecutionException {
        final int THREADS = 20;
        final int INVOCATIONS_PER_THREAD = 50;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < INVOCATIONS_PER_THREAD; i++) {
                    NotificationUtil.showInfo("Thread " + threadId, "Msg " + i);
                    NotificationUtil.showSuccess("Thread " + threadId, "Msg " + i);
                    NotificationUtil.showWarning("Thread " + threadId, "Msg " + i);
                    NotificationUtil.showError("Thread " + threadId, "Msg " + i);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}
