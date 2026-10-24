package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.StoreSettings;

import java.sql.*;

/**
 * Data Access Object for Store Settings singleton configuration.
 */
public class SettingsDao {

    public StoreSettings getSettings() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getSettings(conn);
        }
    }

    public StoreSettings getSettings(Connection conn) throws SQLException {
        String sql = "SELECT * FROM store_settings WHERE setting_id = 1;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapRow(rs);
            }
        }

        // If no row exists, initialize default and return
        StoreSettings defaults = StoreSettings.defaultSettings();
        saveSettings(defaults, conn);
        return defaults;
    }

    public boolean saveSettings(StoreSettings settings) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return saveSettings(settings, conn);
        }
    }

    public boolean saveSettings(StoreSettings settings, Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM store_settings WHERE setting_id = 1;";
        boolean exists = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                exists = true;
            }
        }

        if (exists) {
            String updateSql = "UPDATE store_settings SET store_name = ?, store_address = ?, store_phone = ?, " +
                    "region_preset = ?, currency_code = ?, currency_symbol = ?, tax_name = ?, tax_rate = ?, " +
                    "receipt_language = ?, admin_password_hash = ?, ui_theme = ? WHERE setting_id = 1;";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, settings.getStoreName());
                pstmt.setString(2, settings.getStoreAddress());
                pstmt.setString(3, settings.getStorePhone());
                pstmt.setString(4, settings.getRegionPreset());
                pstmt.setString(5, settings.getCurrencyCode());
                pstmt.setString(6, settings.getCurrencySymbol());
                pstmt.setString(7, settings.getTaxName());
                pstmt.setDouble(8, settings.getTaxRate());
                pstmt.setString(9, settings.getReceiptLanguage());
                pstmt.setString(10, settings.getAdminPasswordHash());
                pstmt.setString(11, settings.getUiTheme() != null ? settings.getUiTheme() : "LIGHT");
                return pstmt.executeUpdate() > 0;
            }
        } else {
            String insertSql = "INSERT INTO store_settings (setting_id, store_name, store_address, store_phone, " +
                    "region_preset, currency_code, currency_symbol, tax_name, tax_rate, receipt_language, admin_password_hash, ui_theme) " +
                    "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, settings.getStoreName());
                pstmt.setString(2, settings.getStoreAddress());
                pstmt.setString(3, settings.getStorePhone());
                pstmt.setString(4, settings.getRegionPreset());
                pstmt.setString(5, settings.getCurrencyCode());
                pstmt.setString(6, settings.getCurrencySymbol());
                pstmt.setString(7, settings.getTaxName());
                pstmt.setDouble(8, settings.getTaxRate());
                pstmt.setString(9, settings.getReceiptLanguage());
                pstmt.setString(10, settings.getAdminPasswordHash());
                pstmt.setString(11, settings.getUiTheme() != null ? settings.getUiTheme() : "LIGHT");
                return pstmt.executeUpdate() > 0;
            }
        }
    }

    public boolean updateTheme(String theme) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return updateTheme(theme, conn);
        }
    }

    public boolean updateTheme(String theme, Connection conn) throws SQLException {
        String safeTheme = (theme != null && "DARK".equalsIgnoreCase(theme.trim())) ? "DARK" : "LIGHT";
        String sql = "UPDATE store_settings SET ui_theme = ? WHERE setting_id = 1;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, safeTheme);
            return pstmt.executeUpdate() > 0;
        }
    }

    private StoreSettings mapRow(ResultSet rs) throws SQLException {
        String theme = "LIGHT";
        try {
            String val = rs.getString("ui_theme");
            if (val != null && !val.isBlank()) {
                theme = val;
            }
        } catch (SQLException ignored) {
            // In case column does not exist in an older test fixture
        }

        return new StoreSettings(
                rs.getInt("setting_id"),
                rs.getString("store_name"),
                rs.getString("store_address"),
                rs.getString("store_phone"),
                rs.getString("region_preset"),
                rs.getString("currency_code"),
                rs.getString("currency_symbol"),
                rs.getString("tax_name"),
                rs.getDouble("tax_rate"),
                rs.getString("receipt_language"),
                rs.getString("admin_password_hash"),
                theme
        );
    }
}
