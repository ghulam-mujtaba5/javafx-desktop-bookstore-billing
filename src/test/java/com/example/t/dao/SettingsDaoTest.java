package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - SettingsDao Unit Tests")
public class SettingsDaoTest {

    @TempDir
    Path tempDir;

    private SettingsDao settingsDao;

    @BeforeEach
    public void setUp() {
        Path testDb = tempDir.resolve("settings_test.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();
        settingsDao = new SettingsDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Default Store Settings Retrieval")
    public void testGetDefaultSettings() throws SQLException {
        StoreSettings s = settingsDao.getSettings();
        assertNotNull(s);
        assertTrue(s.getStoreName().equals("GM Bookstore & Stationery") || s.getStoreName().equals("Shop Name"),
                "Store name should match default or legacy imported shop name");
        assertEquals("USA", s.getRegionPreset());
        assertEquals("USD", s.getCurrencyCode());
        assertEquals("$", s.getCurrencySymbol());
        assertEquals(8.25, s.getTaxRate(), 0.001);
    }

    @Test
    @DisplayName("Verify Updating Store Settings and Regional Presets")
    public void testUpdateSettings() throws SQLException {
        StoreSettings s = settingsDao.getSettings();
        s.setStoreName("West End Books & Crafts");
        s.applyPreset(RegionalPreset.UK);

        boolean saved = settingsDao.saveSettings(s);
        assertTrue(saved);

        StoreSettings refreshed = settingsDao.getSettings();
        assertEquals("West End Books & Crafts", refreshed.getStoreName());
        assertEquals("UK", refreshed.getRegionPreset());
        assertEquals("GBP", refreshed.getCurrencyCode());
        assertEquals("£", refreshed.getCurrencySymbol());
        assertEquals("VAT", refreshed.getTaxName());
        assertEquals(20.00, refreshed.getTaxRate(), 0.001);
    }

    @Test
    @DisplayName("Verify Single Row Constraint (setting_id = 1)")
    public void testSingletonConstraintViolatedOnIdNotOne() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("INSERT INTO store_settings (setting_id, store_name, admin_password_hash) " +
                        "VALUES (2, 'Invalid Store', 'hash');");
            }, "Inserting setting_id = 2 must violate CHECK (setting_id = 1)");
        }
    }
}
