package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 7: SQLite Persistence & Migrations E2E Tests")
public class SqlitePersistenceE2ETest {

    private E2ETestHarness harness;
    private SqliteTestDatabase db;

    @BeforeEach
    public void setUp() throws Exception {
        harness = new E2ETestHarness();
        db = harness.getDatabase();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("F7.1: Verify All 6 Required Normalized Tables Exist")
    public void testAllTablesExist() throws Exception {
        Set<String> tableNames = new HashSet<>();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
            while (rs.next()) {
                tableNames.add(rs.getString(1).toLowerCase());
            }
        }

        assertTrue(tableNames.contains("schema_migrations"), "Missing schema_migrations table");
        assertTrue(tableNames.contains("categories"), "Missing categories table");
        assertTrue(tableNames.contains("products"), "Missing products table");
        assertTrue(tableNames.contains("invoices"), "Missing invoices table");
        assertTrue(tableNames.contains("invoice_items"), "Missing invoice_items table");
        assertTrue(tableNames.contains("store_settings"), "Missing store_settings table");
    }

    @Test
    @DisplayName("F7.2: Verify WAL Journal Mode is active on database")
    public void testWalJournalMode() throws Exception {
        String journalMode = db.getJournalMode();
        // File-based SQLite in our harness should be 'wal'
        assertEquals("wal", journalMode, "Database must operate in WAL journal mode");
    }

    @Test
    @DisplayName("F7.3: Verify Foreign Key Enforcement is Enabled")
    public void testForeignKeysEnabled() throws Exception {
        boolean fkEnabled = db.areForeignKeysEnabled();
        assertTrue(fkEnabled, "PRAGMA foreign_keys must be active (1)");
    }

    @Test
    @DisplayName("F7.4: Schema Migrations Table Records Applied Versions")
    public void testSchemaMigrationsVersion() throws Exception {
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_migrations;")) {
            assertTrue(rs.next());
            int version = rs.getInt(1);
            assertTrue(version >= 1, "Latest applied migration version must be at least 1");
        }
    }

    @Test
    @DisplayName("F7.5: Transaction Rollback on Error leaves Database Unchanged")
    public void testTransactionRollback() throws Exception {
        int initialStock = db.getProductStock(harness.getCleanCodeId());
        int initialInvoiceCount = db.getAllInvoices().size();

        var conn = db.getConnection();
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // Update stock
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("UPDATE products SET stock_quantity = 999 WHERE product_id = " + harness.getCleanCodeId());
            }
            // Trigger intentional failure (invalid foreign key into categories)
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("INSERT INTO products (sku, name, category_id, price) VALUES ('FAIL', 'Fail', 99999, 10.0)");
            }
            conn.commit();
            fail("Statement with invalid foreign key should have thrown SQLException");
        } catch (SQLException expected) {
            conn.rollback();
        } finally {
            conn.setAutoCommit(auto);
        }

        // Verify stock is untouched
        assertEquals(initialStock, db.getProductStock(harness.getCleanCodeId()),
                "Stock must remain at initial value after rollback");
        assertEquals(initialInvoiceCount, db.getAllInvoices().size());
    }

    @Test
    @DisplayName("F7.6: Store Settings Singleton constraint (setting_id = 1)")
    public void testStoreSettingsSingletonConstraint() throws Exception {
        // Attempt to insert a second settings row with setting_id = 2 -> must violate CHECK (setting_id = 1)
        assertThrows(SQLException.class, () -> {
            try (Statement stmt = db.getConnection().createStatement()) {
                stmt.executeUpdate("INSERT INTO store_settings (setting_id, store_name, admin_password_hash) " +
                        "VALUES (2, 'Second Store', 'hash');");
            }
        }, "Inserting setting_id != 1 must violate CHECK constraint");
    }
}
