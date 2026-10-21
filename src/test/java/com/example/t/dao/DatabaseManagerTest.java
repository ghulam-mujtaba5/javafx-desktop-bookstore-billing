package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.database.SchemaMigrationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - DatabaseManager & Schema Migration Unit Tests")
public class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private Path testDbFile;

    @BeforeEach
    public void setUp() {
        testDbFile = tempDir.resolve("test_bookstore.db");
        DatabaseManager.setCustomDatabasePath(testDbFile);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Database Connection, Foreign Keys, and WAL Mode")
    public void testConnectionPragmas() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            assertNotNull(conn, "Connection must not be null");

            // Verify foreign keys enabled
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA foreign_keys;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys must be 1 (ON)");
            }

            // Verify journal mode is WAL on file database
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA journal_mode;")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase(), "Journal mode must be WAL");
            }
        }
    }

    @Test
    @DisplayName("Verify Automatic Schema Creation of all 6 Normalized Tables")
    public void testSchemaInitialization() throws SQLException {
        DatabaseManager.initializeDatabase();

        Set<String> tables = new HashSet<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase());
            }
        }

        assertTrue(tables.contains("schema_migrations"), "Must have schema_migrations table");
        assertTrue(tables.contains("categories"), "Must have categories table");
        assertTrue(tables.contains("products"), "Must have products table");
        assertTrue(tables.contains("invoices"), "Must have invoices table");
        assertTrue(tables.contains("invoice_items"), "Must have invoice_items table");
        assertTrue(tables.contains("store_settings"), "Must have store_settings table");
    }

    @Test
    @DisplayName("Verify Schema Migration Version Audit Record")
    public void testMigrationVersionRecorded() throws SQLException {
        DatabaseManager.initializeDatabase();

        try (Connection conn = DatabaseManager.getConnection()) {
            int version = SchemaMigrationManager.getCurrentVersion(conn);
            assertTrue(version >= 1, "Recorded migration version must be >= 1");
        }
    }

    @Test
    @DisplayName("Verify Seeding of Default Categories and Sample Catalog")
    public void testCatalogSeeding() throws SQLException {
        DatabaseManager.initializeDatabase();

        try (Connection conn = DatabaseManager.getConnection();
             Statement s = conn.createStatement()) {

            // Check categories
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM categories;")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 4, "Must have seeded at least 4 categories");
            }

            // Check sample products
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM products;")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 6, "Must have seeded at least 6 products");
            }

            // Check default store settings
            try (ResultSet rs = s.executeQuery("SELECT region_preset, currency_code, tax_rate FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next());
                assertEquals("USA", rs.getString("region_preset"));
                assertEquals("USD", rs.getString("currency_code"));
                assertEquals(8.25, rs.getDouble("tax_rate"), 0.001);
            }
        }
    }

    @Test
    @DisplayName("Verify Custom AppData Directory Resolution via System Property")
    public void testAppDataDirectoryOverride() throws IOException {
        Path customData = tempDir.resolve("custom_app_data");
        System.setProperty("bookstore.data.dir", customData.toString());

        try {
            Path resolved = DatabaseManager.getDataDirectory();
            assertEquals(customData.toAbsolutePath(), resolved.toAbsolutePath());
            assertTrue(Files.exists(resolved), "Resolved directory must be created if absent");
        } finally {
            System.clearProperty("bookstore.data.dir");
        }
    }
}
