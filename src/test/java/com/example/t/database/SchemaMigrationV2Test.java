package com.example.t.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-A - Schema Migration V2 & Persistence Infrastructure Tests")
public class SchemaMigrationV2Test {

    @TempDir
    Path tempDir;

    private Path testDbFile;

    @BeforeEach
    public void setUp() {
        testDbFile = tempDir.resolve("test_migration_v2.db");
        DatabaseManager.setCustomDatabasePath(testDbFile);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Schema Version constant is updated to 2")
    public void testCurrentSchemaVersionConstant() {
        assertEquals(2, SchemaMigrationManager.CURRENT_SCHEMA_VERSION,
                "SchemaMigrationManager.CURRENT_SCHEMA_VERSION must be 2");
    }

    @Test
    @DisplayName("Verify DatabaseManager.getCoversDirectory resolves under data directory")
    public void testCoversDirectoryCreation() throws IOException {
        Path coversDir = DatabaseManager.getCoversDirectory();
        assertNotNull(coversDir, "Covers directory must not be null");
        assertTrue(Files.exists(coversDir), "Covers directory must exist on disk");
        assertTrue(Files.isDirectory(coversDir), "Covers directory must be a directory");
        assertTrue(coversDir.getFileName().toString().equalsIgnoreCase("covers"),
                "Directory name must be 'covers'");
    }

    @Test
    @DisplayName("Verify Fresh Database Migration creates all V1 + V2 tables and records both versions")
    public void testFreshMigrationV2() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean migrated = SchemaMigrationManager.migrate(conn);
            assertTrue(migrated, "Migration must return true on fresh database");

            int version = SchemaMigrationManager.getCurrentVersion(conn);
            assertEquals(2, version, "Recorded migration version must be 2");

            Set<String> tables = getTableNames(conn);
            assertTrue(tables.contains("schema_migrations"), "Must have schema_migrations table");
            assertTrue(tables.contains("categories"), "Must have categories table");
            assertTrue(tables.contains("products"), "Must have products table");
            assertTrue(tables.contains("invoices"), "Must have invoices table");
            assertTrue(tables.contains("invoice_items"), "Must have invoice_items table");
            assertTrue(tables.contains("store_settings"), "Must have store_settings table");
            assertTrue(tables.contains("catalog_cache"), "Must have catalog_cache table");
            assertTrue(tables.contains("invoice_payments"), "Must have invoice_payments table");
            assertTrue(tables.contains("financial_z_reports"), "Must have financial_z_reports table");

            // Verify invoice_items has new V2 columns
            Set<String> invoiceItemCols = getColumnNames(conn, "invoice_items");
            assertTrue(invoiceItemCols.contains("discount_percent"), "invoice_items must have discount_percent");
            assertTrue(invoiceItemCols.contains("discount_amount"), "invoice_items must have discount_amount");
            assertTrue(invoiceItemCols.contains("notes"), "invoice_items must have notes");

            // Verify catalog_cache columns
            Set<String> catalogCacheCols = getColumnNames(conn, "catalog_cache");
            assertTrue(catalogCacheCols.contains("isbn13"), "catalog_cache must have isbn13");
            assertTrue(catalogCacheCols.contains("isbn10"), "catalog_cache must have isbn10");
            assertTrue(catalogCacheCols.contains("title"), "catalog_cache must have title");
            assertTrue(catalogCacheCols.contains("subtitle"), "catalog_cache must have subtitle");
            assertTrue(catalogCacheCols.contains("authors"), "catalog_cache must have authors");
            assertTrue(catalogCacheCols.contains("publisher"), "catalog_cache must have publisher");
            assertTrue(catalogCacheCols.contains("published_year"), "catalog_cache must have published_year");
            assertTrue(catalogCacheCols.contains("cover_image_path"), "catalog_cache must have cover_image_path");
            assertTrue(catalogCacheCols.contains("cover_url"), "catalog_cache must have cover_url");
            assertTrue(catalogCacheCols.contains("source"), "catalog_cache must have source");
            assertTrue(catalogCacheCols.contains("raw_json"), "catalog_cache must have raw_json");

            // Verify invoice_payments columns
            Set<String> paymentCols = getColumnNames(conn, "invoice_payments");
            assertTrue(paymentCols.contains("payment_id"), "invoice_payments must have payment_id");
            assertTrue(paymentCols.contains("invoice_id"), "invoice_payments must have invoice_id");
            assertTrue(paymentCols.contains("payment_type"), "invoice_payments must have payment_type");
            assertTrue(paymentCols.contains("amount"), "invoice_payments must have amount");
            assertTrue(paymentCols.contains("tendered_amount"), "invoice_payments must have tendered_amount");
            assertTrue(paymentCols.contains("change_due"), "invoice_payments must have change_due");
            assertTrue(paymentCols.contains("reference_info"), "invoice_payments must have reference_info");

            // Verify financial_z_reports columns
            Set<String> zReportCols = getColumnNames(conn, "financial_z_reports");
            assertTrue(zReportCols.contains("z_report_id"), "financial_z_reports must have z_report_id");
            assertTrue(zReportCols.contains("z_sequence_number"), "financial_z_reports must have z_sequence_number");
            assertTrue(zReportCols.contains("total_cash_sales"), "financial_z_reports must have total_cash_sales");
            assertTrue(zReportCols.contains("total_card_sales"), "financial_z_reports must have total_card_sales");
            assertTrue(zReportCols.contains("total_credit_sales"), "financial_z_reports must have total_credit_sales");
            assertTrue(zReportCols.contains("expected_drawer_cash"), "financial_z_reports must have expected_drawer_cash");
            assertTrue(zReportCols.contains("actual_drawer_cash"), "financial_z_reports must have actual_drawer_cash");
            assertTrue(zReportCols.contains("cash_over_short"), "financial_z_reports must have cash_over_short");
            assertTrue(zReportCols.contains("cumulative_grand_total"), "financial_z_reports must have cumulative_grand_total");

            // Verify indexes
            Set<String> indexes = getIndexNames(conn);
            assertTrue(indexes.contains("idx_catalog_cache_isbn10"), "Must have index on catalog_cache(isbn10)");
            assertTrue(indexes.contains("idx_catalog_cache_title"), "Must have index on catalog_cache(title)");
            assertTrue(indexes.contains("idx_products_isbn"), "Must have index on products(isbn)");
            assertTrue(indexes.contains("idx_invoice_payments_invoice"), "Must have index on invoice_payments(invoice_id)");
            assertTrue(indexes.contains("idx_financial_z_reports_seq"), "Must have index on financial_z_reports(z_sequence_number)");
        }
    }

    @Test
    @DisplayName("Verify Upgrade from Version 1 database to Version 2 executes cleanly and preserves existing data")
    public void testUpgradeFromV1ToV2() throws SQLException {
        // 1. Manually set up a V1 database with version 1 recorded
        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, description TEXT, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                s.execute("INSERT INTO schema_migrations (version, description) VALUES (1, 'V1 Initial');");

                s.execute("CREATE TABLE categories (category_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, description TEXT);");
                s.execute("CREATE TABLE products (product_id INTEGER PRIMARY KEY AUTOINCREMENT, sku TEXT UNIQUE NOT NULL, name TEXT NOT NULL, category_id INTEGER, price REAL NOT NULL, purchase_price REAL DEFAULT 0.0, stock_quantity INTEGER NOT NULL DEFAULT 0, min_stock_threshold INTEGER DEFAULT 5, author TEXT, publisher TEXT, isbn TEXT, status TEXT DEFAULT 'ACTIVE', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                s.execute("CREATE TABLE invoices (invoice_id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT UNIQUE NOT NULL, customer_name TEXT DEFAULT 'Walk-in Customer', customer_phone TEXT, invoice_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, subtotal REAL NOT NULL, discount_percent REAL DEFAULT 0.0, discount_amount REAL DEFAULT 0.0, tax_rate REAL DEFAULT 0.0, tax_amount REAL DEFAULT 0.0, net_total REAL NOT NULL, cash_tendered REAL NOT NULL, change_due REAL NOT NULL, payment_method TEXT DEFAULT 'CASH', currency_code TEXT DEFAULT 'USD', region TEXT DEFAULT 'USA');");
                s.execute("CREATE TABLE invoice_items (item_id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id), product_id INTEGER NOT NULL REFERENCES products(product_id), quantity INTEGER NOT NULL, unit_price REAL NOT NULL, line_total REAL NOT NULL);");
                s.execute("CREATE TABLE store_settings (setting_id INTEGER PRIMARY KEY CHECK (setting_id = 1), store_name TEXT NOT NULL, store_address TEXT, store_phone TEXT, region_preset TEXT NOT NULL DEFAULT 'USA', currency_code TEXT NOT NULL DEFAULT 'USD', currency_symbol TEXT NOT NULL DEFAULT '$', tax_name TEXT NOT NULL DEFAULT 'Sales Tax', tax_rate REAL NOT NULL DEFAULT 0.0, receipt_language TEXT NOT NULL DEFAULT 'EN', admin_password_hash TEXT NOT NULL);");

                // Insert pre-existing item into invoice_items
                s.execute("INSERT INTO invoices (invoice_id, invoice_number, subtotal, net_total, cash_tendered, change_due) VALUES (1, 'INV-PRE-001', 50.0, 50.0, 50.0, 0.0);");
                s.execute("INSERT INTO products (product_id, sku, name, price) VALUES (1, 'SKU-PRE-1', 'Pre-existing Book', 50.0);");
                s.execute("INSERT INTO invoice_items (item_id, invoice_id, product_id, quantity, unit_price, line_total) VALUES (1, 1, 1, 1, 50.0, 50.0);");
            }

            int initialVersion = SchemaMigrationManager.getCurrentVersion(conn);
            assertEquals(1, initialVersion, "Initial version must be 1");

            // 2. Run migration
            boolean migrated = SchemaMigrationManager.migrate(conn);
            assertTrue(migrated, "Migration from 1 to 2 must succeed");

            int finalVersion = SchemaMigrationManager.getCurrentVersion(conn);
            assertEquals(2, finalVersion, "Upgraded database must be version 2");

            // 3. Verify existing row is preserved and has default column values
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT item_id, discount_percent, discount_amount, notes FROM invoice_items WHERE item_id = 1;")) {
                assertTrue(rs.next(), "Existing invoice_item must exist");
                assertEquals(1, rs.getInt("item_id"));
                assertEquals(0.0, rs.getDouble("discount_percent"), 0.001);
                assertEquals(0.0, rs.getDouble("discount_amount"), 0.001);
                assertNull(rs.getString("notes"));
            }
        }
    }

    @Test
    @DisplayName("Verify Migration is Idempotent when already at version 2")
    public void testMigrationIdempotency() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            SchemaMigrationManager.migrate(conn);
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn));

            // Run migration again
            boolean secondRun = SchemaMigrationManager.migrate(conn);
            assertFalse(secondRun, "Subsequent migration call must return false when schema is up to date");
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn));
        }
    }

    private Set<String> getTableNames(Connection conn) throws SQLException {
        Set<String> set = new HashSet<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
            while (rs.next()) {
                set.add(rs.getString(1).toLowerCase());
            }
        }
        return set;
    }

    private Set<String> getColumnNames(Connection conn, String table) throws SQLException {
        Set<String> set = new HashSet<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ");")) {
            while (rs.next()) {
                set.add(rs.getString("name").toLowerCase());
            }
        }
        return set;
    }

    private Set<String> getIndexNames(Connection conn) throws SQLException {
        Set<String> set = new HashSet<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='index';")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null) {
                    set.add(name.toLowerCase());
                }
            }
        }
        return set;
    }
}
