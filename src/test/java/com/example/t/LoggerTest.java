package com.example.t;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LoggerTest {

    @Test
    @DisplayName("Logger methods should execute without throwing exceptions")
    void testLoggerMethods() {
        assertDoesNotThrow(() -> {
            Logger.setLevel(Logger.Level.DEBUG);
            assertEquals(Logger.Level.DEBUG, Logger.getLevel());

            Logger.debug("Debug message");
            Logger.debug("Formatted %s %d", "debug", 42);

            Logger.info("Info message");
            Logger.info("Formatted %s %d", "info", 100);

            Logger.warn("Warning message");
            Logger.warn("Formatted %s %d", "warn", 200);

            Logger.error("Error message");
            Logger.error("Error with exception", new RuntimeException("Test exception"));

            // Restore level
            Logger.setLevel(Logger.Level.INFO);
        });
    }

    @Test
    @DisplayName("Null message should be handled gracefully")
    void testNullMessage() {
        assertDoesNotThrow(() -> {
            Logger.info(null);
            Logger.warn(null);
            Logger.error(null);
            Logger.error(null, (Throwable) null);
        });
    }
}
