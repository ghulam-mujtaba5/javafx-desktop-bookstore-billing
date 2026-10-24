package com.example.t.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.example.t.Logger;
import com.example.t.dao.SettingsDao;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.sql.SQLException;

/**
 * Utility for managing AtlantaFX application themes (PrimerLight / PrimerDark),
 * dynamic CSS class switching on the Scene root (.theme-light / .theme-dark),
 * and persistence to SQLite store_settings via SettingsDao.
 */
public class ThemeManager {

    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK = "DARK";

    public static final String CLASS_THEME_LIGHT = "theme-light";
    public static final String CLASS_THEME_DARK = "theme-dark";

    private static String currentTheme = THEME_LIGHT;

    /**
     * Gets the currently active UI theme name ("LIGHT" or "DARK").
     *
     * @return current theme string
     */
    public static String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Applies the specified theme to the application and scene, persisting preference if dao provided.
     *
     * @param scene       active JavaFX Scene (may be null if initializing before scene display)
     * @param themeName   "LIGHT" or "DARK" (defaults to "LIGHT" if null)
     * @param settingsDao SettingsDao for SQLite persistence (optional, may be null)
     */
    public static void applyTheme(Scene scene, String themeName, SettingsDao settingsDao) {
        String targetTheme = (themeName != null && THEME_DARK.equalsIgnoreCase(themeName.trim()))
                ? THEME_DARK
                : THEME_LIGHT;

        currentTheme = targetTheme;

        // Apply AtlantaFX UserAgentStylesheet
        try {
            if (THEME_DARK.equals(targetTheme)) {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            } else {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            }
        } catch (Throwable t) {
            Logger.warn("Could not set UserAgentStylesheet via Application: " + t.getMessage());
        }

        // Apply CSS class to Scene root if available
        if (scene != null) {
            Runnable updateRoot = () -> {
                Parent root = scene.getRoot();
                if (root != null) {
                    if (THEME_DARK.equals(targetTheme)) {
                        root.getStyleClass().remove(CLASS_THEME_LIGHT);
                        if (!root.getStyleClass().contains(CLASS_THEME_DARK)) {
                            root.getStyleClass().add(CLASS_THEME_DARK);
                        }
                    } else {
                        root.getStyleClass().remove(CLASS_THEME_DARK);
                        if (!root.getStyleClass().contains(CLASS_THEME_LIGHT)) {
                            root.getStyleClass().add(CLASS_THEME_LIGHT);
                        }
                    }
                }
            };

            if (Platform.isFxApplicationThread()) {
                updateRoot.run();
            } else {
                Platform.runLater(updateRoot);
            }
        }

        // Persist to SQLite store_settings
        if (settingsDao != null) {
            try {
                settingsDao.updateTheme(targetTheme);
                Logger.info("Persisted UI theme '" + targetTheme + "' to store_settings.");
            } catch (SQLException e) {
                Logger.warn("Failed to persist UI theme to database: " + e.getMessage());
            }
        }
    }

    /**
     * Toggles the current theme between PrimerLight and PrimerDark.
     *
     * @param scene       active JavaFX Scene
     * @param settingsDao SettingsDao for SQLite persistence
     * @return newly active theme name
     */
    public static String toggleTheme(Scene scene, SettingsDao settingsDao) {
        String newTheme = THEME_DARK.equalsIgnoreCase(currentTheme) ? THEME_LIGHT : THEME_DARK;
        applyTheme(scene, newTheme, settingsDao);
        return newTheme;
    }
}
