package com.example.t.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-A - Adversarial Stress & Schema Robustness Test Suite")
public class SchemaMigrationV2AdversarialTest {

    @TempDir
    Path tempDir;

    private Path testDbFile;

    @BeforeEach
    public void setUp() {
        testDbFile = tempDir.resolve("adversarial_migration_v2.db");
        DatabaseManager.setCustomDatabasePath(testDbFile);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Challenge 1: Fresh DB migration v0 -> v2 seeds catalog and records both migrations")
    public void testFreshMigrationRecordsBothVersionsAndSeedsCatalog() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean migrated = SchemaMigrationManager.migrate(conn);
            assertTrue(migrated, "Migration must report true");
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn), "Version must be 2");

            // Verify schema_migrations has version 1 and 2
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT version FROM schema_migrations ORDER BY version ASC;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
                assertFalse(rs.next(), "Only versions 1 and 2 should be recorded");
            }

            // Verify seed products and categories exist
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM products;")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 6, "Fresh database must have default seeded products");
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM categories;")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 4, "Fresh database must have default seeded categories");
            }
        }
    }

    @Test
    @DisplayName("Challenge 2: Upgrade migration v1 -> v2 preserves extensive existing store data and settings")
    public void testUpgradePreservesExistingDataAndCustomSettings() throws SQLException {
        // 1. Manually set up a V1 database with pre-existing custom settings, 100 products, 50 invoices, 100 items
        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, description TEXT, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                s.execute("INSERT INTO schema_migrations (version, description) VALUES (1, 'V1 Baseline');");

                s.execute("CREATE TABLE categories (category_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, description TEXT);");
                s.execute("CREATE TABLE products (product_id INTEGER PRIMARY KEY AUTOINCREMENT, sku TEXT UNIQUE NOT NULL, name TEXT NOT NULL, category_id INTEGER, price REAL NOT NULL, purchase_price REAL DEFAULT 0.0, stock_quantity INTEGER NOT NULL DEFAULT 0, min_stock_threshold INTEGER DEFAULT 5, author TEXT, publisher TEXT, isbn TEXT, status TEXT DEFAULT 'ACTIVE', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                s.execute("CREATE TABLE invoices (invoice_id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT UNIQUE NOT NULL, customer_name TEXT DEFAULT 'Walk-in Customer', customer_phone TEXT, invoice_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, subtotal REAL NOT NULL, discount_percent REAL DEFAULT 0.0, discount_amount REAL DEFAULT 0.0, tax_rate REAL DEFAULT 0.0, tax_amount REAL DEFAULT 0.0, net_total REAL NOT NULL, cash_tendered REAL NOT NULL, change_due REAL NOT NULL, payment_method TEXT DEFAULT 'CASH', currency_code TEXT DEFAULT 'USD', region TEXT DEFAULT 'USA');");
                s.execute("CREATE TABLE invoice_items (item_id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id), product_id INTEGER NOT NULL REFERENCES products(product_id), quantity INTEGER NOT NULL, unit_price REAL NOT NULL, line_total REAL NOT NULL);");
                s.execute("CREATE TABLE store_settings (setting_id INTEGER PRIMARY KEY CHECK (setting_id = 1), store_name TEXT NOT NULL, store_address TEXT, store_phone TEXT, region_preset TEXT NOT NULL DEFAULT 'USA', currency_code TEXT NOT NULL DEFAULT 'USD', currency_symbol TEXT NOT NULL DEFAULT '$', tax_name TEXT NOT NULL DEFAULT 'Sales Tax', tax_rate REAL NOT NULL DEFAULT 0.0, receipt_language TEXT NOT NULL DEFAULT 'EN', admin_password_hash TEXT NOT NULL);");

                // Custom settings: UK store
                s.execute("INSERT INTO store_settings (setting_id, store_name, store_address, store_phone, region_preset, currency_code, currency_symbol, tax_name, tax_rate, receipt_language, admin_password_hash) " +
                        "VALUES (1, 'Custom London Books', 'Baker St', '020-7946-0919', 'UK', 'GBP', '£', 'VAT', 20.0, 'EN', 'hash123');");

                // Insert category
                s.execute("INSERT INTO categories (category_id, name, description) VALUES (10, 'Rare Editions', 'Collectibles');");

                // Insert 100 products
                for (int i = 1; i <= 100; i++) {
                    s.execute(String.format("INSERT INTO products (product_id, sku, name, category_id, price, purchase_price, stock_quantity, isbn) " +
                            "VALUES (%d, 'SKU-%04d', 'Book Number %d', 10, %f, %f, %d, '97800000%05d');",
                            i, i, i, 10.0 + i, 5.0 + i, 50 + i, i));
                }

                // Insert 50 invoices and 100 invoice_items
                for (int inv = 1; inv <= 50; inv++) {
                    s.execute(String.format("INSERT INTO invoices (invoice_id, invoice_number, customer_name, subtotal, net_total, cash_tendered, change_due, currency_code) " +
                            "VALUES (%d, 'INV-%05d', 'Customer %d', 100.0, 100.0, 100.0, 0.0, 'GBP');",
                            inv, inv, inv));

                    s.execute(String.format("INSERT INTO invoice_items (item_id, invoice_id, product_id, quantity, unit_price, line_total) " +
                            "VALUES (%d, %d, %d, 2, 25.0, 50.0);",
                            (inv * 2) - 1, inv, inv));
                    s.execute(String.format("INSERT INTO invoice_items (item_id, invoice_id, product_id, quantity, unit_price, line_total) " +
                            "VALUES (%d, %d, %d, 1, 50.0, 50.0);",
                            (inv * 2), inv, (inv % 100) + 1));
                }
            }

            // Verify initial state
            assertEquals(1, SchemaMigrationManager.getCurrentVersion(conn));

            // 2. Perform Migration to V2
            boolean upgraded = SchemaMigrationManager.migrate(conn);
            assertTrue(upgraded, "Migration from V1 to V2 should return true");
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn));

            // 3. Verify store_settings was NOT overwritten by seed logic!
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT store_name, currency_symbol, tax_rate FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next());
                assertEquals("Custom London Books", rs.getString("store_name"), "Store name must be preserved");
                assertEquals("£", rs.getString("currency_symbol"), "Currency symbol must remain £");
                assertEquals(20.0, rs.getDouble("tax_rate"), 0.001, "Tax rate must remain 20.0");
            }

            // 4. Verify all 100 products exist with accurate data
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM products;")) {
                assertTrue(rs.next());
                assertEquals(100, rs.getInt(1), "All 100 products must be preserved");
            }

            // 5. Verify all 50 invoices and 100 items exist and invoice_items has new columns with null/0 defaults
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM invoice_items;")) {
                assertTrue(rs.next());
                assertEquals(100, rs.getInt(1), "All 100 invoice_items must be preserved");
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT item_id, invoice_id, product_id, quantity, unit_price, line_total, discount_percent, discount_amount, notes FROM invoice_items WHERE item_id = 42;")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt("item_id"));
                assertEquals(21, rs.getInt("invoice_id"));
                assertEquals(22, rs.getInt("product_id"));
                assertEquals(1, rs.getInt("quantity"));
                assertEquals(50.0, rs.getDouble("unit_price"), 0.001);
                assertEquals(50.0, rs.getDouble("line_total"), 0.001);
                assertEquals(0.0, rs.getDouble("discount_percent"), 0.001);
                assertEquals(0.0, rs.getDouble("discount_amount"), 0.001);
                assertNull(rs.getString("notes"), "Default notes must be null");
            }
        }
    }

    @Test
    @DisplayName("Challenge 3: Repeated migrations (10x) are strictly idempotent")
    public void testMigrationHighRepetitionIdempotency() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean firstRun = SchemaMigrationManager.migrate(conn);
            assertTrue(firstRun, "First migration must succeed");
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn));

            for (int i = 2; i <= 10; i++) {
                boolean repeatRun = SchemaMigrationManager.migrate(conn);
                assertFalse(repeatRun, "Iteration " + i + " must return false (no-op)");
                assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn), "Version must remain 2");
            }

            // Ensure no duplicate rows in schema_migrations
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "There should be exactly 2 migration records");
            }
        }
    }

    @Test
    @DisplayName("Challenge 4: Partial column pre-existence does not break migration")
    public void testPartialColumnPreExistence() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, description TEXT, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                s.execute("INSERT INTO schema_migrations (version, description) VALUES (1, 'V1 Baseline');");
                s.execute("CREATE TABLE categories (category_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, description TEXT);");
                s.execute("CREATE TABLE products (product_id INTEGER PRIMARY KEY AUTOINCREMENT, sku TEXT UNIQUE NOT NULL, name TEXT NOT NULL, price REAL NOT NULL, isbn TEXT);");
                s.execute("CREATE TABLE invoices (invoice_id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT UNIQUE NOT NULL, subtotal REAL NOT NULL, net_total REAL NOT NULL, cash_tendered REAL NOT NULL, change_due REAL NOT NULL);");
                s.execute("CREATE TABLE store_settings (setting_id INTEGER PRIMARY KEY CHECK (setting_id = 1), store_name TEXT NOT NULL, admin_password_hash TEXT NOT NULL);");

                // Intentionally create invoice_items with 'notes' ALREADY present, but missing 'discount_percent' and 'discount_amount'
                s.execute("CREATE TABLE invoice_items (" +
                        "item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id), " +
                        "product_id INTEGER NOT NULL REFERENCES products(product_id), " +
                        "quantity INTEGER NOT NULL, " +
                        "unit_price REAL NOT NULL, " +
                        "line_total REAL NOT NULL, " +
                        "notes TEXT" +
                        ");");
            }

            // Now run migration to version 2
            boolean migrated = SchemaMigrationManager.migrate(conn);
            assertTrue(migrated, "Migration should succeed even if 'notes' already exists");
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn));

            // Verify all 3 columns exist
            Set<String> cols = getColumnNames(conn, "invoice_items");
            assertTrue(cols.contains("notes"));
            assertTrue(cols.contains("discount_percent"));
            assertTrue(cols.contains("discount_amount"));
        }
    }

    @Test
    @DisplayName("Challenge 5: Foreign key cascade delete on invoice_payments")
    public void testInvoicePaymentForeignKeyCascadeDelete() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            SchemaMigrationManager.migrate(conn);

            // 1. Verify foreign key rejection: insert payment for non-existent invoice_id 9999
            SQLException ex = assertThrows(SQLException.class, () -> {
                try (Statement s = conn.createStatement()) {
                    s.execute("INSERT INTO invoice_payments (invoice_id, payment_type, amount, tendered_amount, change_due) " +
                            "VALUES (9999, 'CASH', 50.0, 50.0, 0.0);");
                }
            }, "Inserting payment for non-existent invoice must fail foreign key constraint");
            assertTrue(ex.getMessage().toLowerCase().contains("foreign key"), "Error message should mention foreign key: " + ex.getMessage());

            // 2. Create valid invoice
            int testInvoiceId;
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO invoices (invoice_number, subtotal, net_total, cash_tendered, change_due) " +
                        "VALUES ('INV-CASCADE-01', 100.0, 100.0, 100.0, 0.0);");
                try (ResultSet rs = s.executeQuery("SELECT last_insert_rowid();")) {
                    assertTrue(rs.next());
                    testInvoiceId = rs.getInt(1);
                }
            }

            // 3. Insert 3 payments for this invoice
            try (Statement s = conn.createStatement()) {
                s.execute(String.format("INSERT INTO invoice_payments (invoice_id, payment_type, amount, tendered_amount, change_due) " +
                        "VALUES (%d, 'CASH', 40.0, 50.0, 10.0);", testInvoiceId));
                s.execute(String.format("INSERT INTO invoice_payments (invoice_id, payment_type, amount, tendered_amount, change_due) " +
                        "VALUES (%d, 'CARD', 50.0, 50.0, 0.0);", testInvoiceId));
                s.execute(String.format("INSERT INTO invoice_payments (invoice_id, payment_type, amount, tendered_amount, change_due) " +
                        "VALUES (%d, 'STORE_CREDIT', 10.0, 10.0, 0.0);", testInvoiceId));
            }

            // Verify payments are stored
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM invoice_payments WHERE invoice_id = " + testInvoiceId + ";")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "Must have 3 payments recorded");
            }

            // 4. Delete the parent invoice
            try (Statement s = conn.createStatement()) {
                s.execute("DELETE FROM invoices WHERE invoice_id = " + testInvoiceId + ";");
            }

            // 5. Verify all payments were cascade deleted
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM invoice_payments WHERE invoice_id = " + testInvoiceId + ";")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "All payments must be cascade deleted when parent invoice is deleted");
            }
        }
    }

    @Test
    @DisplayName("Challenge 6: Unique constraint and NOT NULL on z_sequence_number in financial_z_reports")
    public void testFinancialZReportsConstraints() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            SchemaMigrationManager.migrate(conn);

            // 1. Insert a report with sequence 1001
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO financial_z_reports (z_sequence_number, period_start, period_end, total_cash_sales, total_card_sales, total_credit_sales, gross_sales, total_discount, taxable_sales, total_tax, net_sales, transaction_count, expected_drawer_cash, actual_drawer_cash, cash_over_short, cumulative_grand_total) " +
                        "VALUES (1001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100.0, 0.0, 0.0, 100.0, 0.0, 100.0, 0.0, 100.0, 5, 100.0, 100.0, 0.0, 100.0);");
            }

            // 2. Attempt duplicate sequence number 1001
            SQLException uniqueEx = assertThrows(SQLException.class, () -> {
                try (Statement s = conn.createStatement()) {
                    s.execute("INSERT INTO financial_z_reports (z_sequence_number, period_start, period_end, total_cash_sales, total_card_sales, total_credit_sales, gross_sales, total_discount, taxable_sales, total_tax, net_sales, transaction_count, expected_drawer_cash, actual_drawer_cash, cash_over_short, cumulative_grand_total) " +
                            "VALUES (1001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 200.0, 0.0, 0.0, 200.0, 0.0, 200.0, 0.0, 200.0, 10, 200.0, 200.0, 0.0, 300.0);");
                }
            }, "Duplicate z_sequence_number must throw SQLException");
            assertTrue(uniqueEx.getMessage().toLowerCase().contains("unique"), "Error should indicate UNIQUE constraint violation: " + uniqueEx.getMessage());

            // 3. Attempt NULL sequence number
            SQLException nullEx = assertThrows(SQLException.class, () -> {
                try (Statement s = conn.createStatement()) {
                    s.execute("INSERT INTO financial_z_reports (z_sequence_number, period_start, period_end, total_cash_sales, total_card_sales, total_credit_sales, gross_sales, total_discount, taxable_sales, total_tax, net_sales, transaction_count, expected_drawer_cash, actual_drawer_cash, cash_over_short, cumulative_grand_total) " +
                            "VALUES (NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 50.0, 0.0, 0.0, 50.0, 0.0, 50.0, 0.0, 50.0, 2, 50.0, 50.0, 0.0, 350.0);");
                }
            }, "NULL z_sequence_number must throw SQLException");
            assertTrue(nullEx.getMessage().toLowerCase().contains("not null") || nullEx.getMessage().toLowerCase().contains("constraint"),
                    "Error should indicate NOT NULL constraint failure: " + nullEx.getMessage());
        }
    }

    @Test
    @DisplayName("Challenge 7: Primary key uniqueness and NOT NULL title in catalog_cache")
    public void testCatalogCacheConstraints() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            SchemaMigrationManager.migrate(conn);

            // 1. Insert valid record
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO catalog_cache (isbn13, isbn10, title) VALUES ('978-0132350884', '0132350882', 'Clean Code');");
            }

            // 2. Duplicate primary key insertion without REPLACE
            SQLException pkEx = assertThrows(SQLException.class, () -> {
                try (Statement s = conn.createStatement()) {
                    s.execute("INSERT INTO catalog_cache (isbn13, isbn10, title) VALUES ('978-0132350884', '0132350882', 'Duplicate Clean Code');");
                }
            }, "Duplicate primary key isbn13 must fail");
            assertTrue(pkEx.getMessage().toLowerCase().contains("unique") || pkEx.getMessage().toLowerCase().contains("primary key"),
                    "Error should mention PRIMARY KEY or UNIQUE constraint: " + pkEx.getMessage());

            // 3. NULL title insertion
            SQLException titleEx = assertThrows(SQLException.class, () -> {
                try (Statement s = conn.createStatement()) {
                    s.execute("INSERT INTO catalog_cache (isbn13, isbn10, title) VALUES ('978-1111111111', '1111111111', NULL);");
                }
            }, "NULL title must fail NOT NULL constraint");
            assertTrue(titleEx.getMessage().toLowerCase().contains("not null"), "Error should mention NOT NULL: " + titleEx.getMessage());

            // 4. Verification that CatalogCacheDao rejects null or empty isbn13 at the application boundary
            com.example.t.dao.CatalogCacheDao dao = new com.example.t.dao.CatalogCacheDao();
            com.example.t.model.BookMetadata invalidMeta = new com.example.t.model.BookMetadata();
            invalidMeta.setTitle("Untitled Book");
            assertThrows(IllegalArgumentException.class, () -> dao.save(invalidMeta, conn),
                    "CatalogCacheDao must throw IllegalArgumentException if isbn13 is null or empty");
        }
    }

    @Test
    @DisplayName("Challenge 9: invoice_items existing columns are preserved and allow both legacy and V2 inserts")
    public void testInvoiceItemsColumnsIntegrity() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            SchemaMigrationManager.migrate(conn);

            // Verify all columns present in invoice_items
            Set<String> cols = getColumnNames(conn, "invoice_items");
            assertTrue(cols.contains("item_id"), "item_id must exist");
            assertTrue(cols.contains("invoice_id"), "invoice_id must exist");
            assertTrue(cols.contains("product_id"), "product_id must exist");
            assertTrue(cols.contains("quantity"), "quantity must exist");
            assertTrue(cols.contains("unit_price"), "unit_price must exist");
            assertTrue(cols.contains("line_total"), "line_total must exist");
            assertTrue(cols.contains("discount_percent"), "discount_percent must exist");
            assertTrue(cols.contains("discount_amount"), "discount_amount must exist");
            assertTrue(cols.contains("notes"), "notes must exist");

            // Create a test invoice
            int invId;
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO invoices (invoice_number, subtotal, net_total, cash_tendered, change_due) VALUES ('INV-ITEM-01', 100.0, 100.0, 100.0, 0.0);");
                try (ResultSet rs = s.executeQuery("SELECT last_insert_rowid();")) {
                    assertTrue(rs.next());
                    invId = rs.getInt(1);
                }
            }

            // Insert 1: Legacy style (without discount_percent, discount_amount, notes specified)
            try (Statement s = conn.createStatement()) {
                s.execute(String.format("INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) VALUES (%d, 1, 2, 25.0, 50.0);", invId));
            }

            // Insert 2: V2 style (with discount_percent, discount_amount, notes specified)
            try (Statement s = conn.createStatement()) {
                s.execute(String.format("INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total, discount_percent, discount_amount, notes) " +
                        "VALUES (%d, 2, 1, 50.0, 45.0, 10.0, 5.0, 'Damaged cover discount');", invId));
            }

            // Verify both rows
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM invoice_items WHERE invoice_id = " + invId + " ORDER BY item_id ASC;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("product_id"));
                assertEquals(2, rs.getInt("quantity"));
                assertEquals(25.0, rs.getDouble("unit_price"), 0.001);
                assertEquals(50.0, rs.getDouble("line_total"), 0.001);
                assertEquals(0.0, rs.getDouble("discount_percent"), 0.001);
                assertEquals(0.0, rs.getDouble("discount_amount"), 0.001);
                assertNull(rs.getString("notes"));

                assertTrue(rs.next());
                assertEquals(2, rs.getInt("product_id"));
                assertEquals(1, rs.getInt("quantity"));
                assertEquals(50.0, rs.getDouble("unit_price"), 0.001);
                assertEquals(45.0, rs.getDouble("line_total"), 0.001);
                assertEquals(10.0, rs.getDouble("discount_percent"), 0.001);
                assertEquals(5.0, rs.getDouble("discount_amount"), 0.001);
                assertEquals("Damaged cover discount", rs.getString("notes"));
            }
        }
    }

    @Test
    @DisplayName("Challenge 8: EXPLAIN QUERY PLAN verifies all created indexes are utilized")
    public void testIndexesAreUtilizedByQueryPlanner() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            SchemaMigrationManager.migrate(conn);

            // Check index on catalog_cache(isbn10)
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("EXPLAIN QUERY PLAN SELECT * FROM catalog_cache WHERE isbn10 = '0132350882';")) {
                assertTrue(rs.next());
                String plan = rs.getString("detail").toLowerCase();
                assertTrue(plan.contains("idx_catalog_cache_isbn10") || plan.contains("using index"),
                        "Query planner must utilize idx_catalog_cache_isbn10: " + plan);
            }

            // Check index on invoice_payments(invoice_id)
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("EXPLAIN QUERY PLAN SELECT * FROM invoice_payments WHERE invoice_id = 42;")) {
                assertTrue(rs.next());
                String plan = rs.getString("detail").toLowerCase();
                assertTrue(plan.contains("idx_invoice_payments_invoice") || plan.contains("using index"),
                        "Query planner must utilize idx_invoice_payments_invoice: " + plan);
            }

            // Check index on financial_z_reports(z_sequence_number)
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("EXPLAIN QUERY PLAN SELECT * FROM financial_z_reports WHERE z_sequence_number = 1;")) {
                assertTrue(rs.next());
                String plan = rs.getString("detail").toLowerCase();
                assertTrue(plan.contains("idx_financial_z_reports_seq") || plan.contains("using index"),
                        "Query planner must utilize idx_financial_z_reports_seq: " + plan);
            }
        }
    }

    @Test
    @DisplayName("Challenge 10: In-memory SQLite database migration completes cleanly without WAL pragma error")
    public void testInMemoryDatabaseMigration() throws SQLException {
        DatabaseManager.setCustomJdbcUrl("jdbc:sqlite::memory:");
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean migrated = SchemaMigrationManager.migrate(conn);
            assertTrue(migrated, "Migration must succeed in :memory: SQLite mode");
            assertEquals(2, SchemaMigrationManager.getCurrentVersion(conn));

            // Verify tables exist in memory
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM catalog_cache;")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
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
}
