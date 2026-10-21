package com.example.t;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

/**
 * Utility for displaying desktop notifications and alerts.
 * Uses ControlsFX Notifications for modern toast-style popups,
 * with fallbacks to JavaFX Alert and console output for headless environments.
 */
public class NotificationUtil {

    private static final int DEFAULT_HIDE_DELAY_SECONDS = 4;

    public static void showError(String title, String message) {
        show(AlertType.ERROR, title, message);
    }

    public static void showSuccess(String title, String message) {
        show(AlertType.INFORMATION, title, message);
    }

    public static void showWarning(String title, String message) {
        show(AlertType.WARNING, title, message);
    }

    public static void showInfo(String title, String message) {
        show(AlertType.INFORMATION, title, message);
    }

    private static void show(AlertType type, String title, String message) {
        String safeTitle = title != null ? title : "";
        String safeMessage = message != null ? message : "";

        Runnable action = () -> {
            try {
                Notifications notifications = Notifications.create()
                        .title(safeTitle)
                        .text(safeMessage)
                        .hideAfter(Duration.seconds(DEFAULT_HIDE_DELAY_SECONDS))
                        .position(Pos.BOTTOM_RIGHT);

                switch (type) {
                    case ERROR -> notifications.showError();
                    case WARNING -> notifications.showWarning();
                    case INFORMATION, CONFIRMATION -> notifications.showInformation();
                    default -> notifications.show();
                }
            } catch (Throwable t) {
                // Fallback to standard JavaFX Alert
                try {
                    Alert alert = new Alert(type);
                    alert.setTitle(safeTitle);
                    alert.setHeaderText(null);
                    alert.setContentText(safeMessage);
                    alert.showAndWait();
                } catch (Throwable fallbackErr) {
                    System.err.println("[" + type + "] " + safeTitle + ": " + safeMessage);
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            try {
                Platform.runLater(action);
            } catch (IllegalStateException e) {
                // JavaFX toolkit not initialized (e.g. running in headless unit tests)
                System.err.println("[" + type + "] " + safeTitle + ": " + safeMessage);
            }
        }
    }
}
