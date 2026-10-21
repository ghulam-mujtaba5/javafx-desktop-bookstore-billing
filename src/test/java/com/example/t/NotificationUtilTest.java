package com.example.t;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class NotificationUtilTest {

    @Test
    @DisplayName("Notification methods should handle invocations gracefully without throwing unhandled exceptions")
    void testNotificationMethods() {
        assertDoesNotThrow(() -> {
            NotificationUtil.showInfo("Information", "This is an info notice");
            NotificationUtil.showSuccess("Success", "Operation succeeded");
            NotificationUtil.showWarning("Warning", "Low inventory warning");
            NotificationUtil.showError("Error", "Sample error notification");
        });
    }

    @Test
    @DisplayName("Notification methods should tolerate null parameters gracefully")
    void testNullParameters() {
        assertDoesNotThrow(() -> {
            NotificationUtil.showInfo(null, null);
            NotificationUtil.showSuccess(null, null);
            NotificationUtil.showWarning(null, null);
            NotificationUtil.showError(null, null);
        });
    }
}
