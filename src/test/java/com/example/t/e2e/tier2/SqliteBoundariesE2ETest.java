package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 7: SQLite Persistence Boundary & Corner Cases")
public class SqliteBoundariesE2ETest {

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
    @DisplayName("B7.1: SQL Injection Attempt in SKU lookup is properly parameterized")
    public void testSqlInjectionInSkuLookup() throws Exception {
        // String contains SQL injection payload
        String maliciousSku = "' OR '1'='1";
        var res = db.getProductBySku(maliciousSku);
        assertNull(res, "Parameterized query must not be vulnerable to SQL injection");
    }

    @Test
    @DisplayName("B7.2: SQL Injection Attempt in Customer Name insertion is neutralized")
    public void testSqlInjectionInCustomerName() throws Exception {
        String payload = "Hacker'); DROP TABLE products;--";
        int invoiceId;
        String sql = "INSERT INTO invoices (invoice_number, customer_name, subtotal, net_total, cash_tendered, change_due) " +
                "VALUES ('INV-INJECT', ?, 10.0, 10.0, 10.0, 0.0);";
        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, payload);
            pstmt.executeUpdate();
            try (var rs = pstmt.getGeneratedKeys()) {
                assertTrue(rs.next());
                invoiceId = rs.getInt(1);
            }
        }

        // Verify products table still exists and was not dropped
        assertFalse(db.getAllProducts().isEmpty(), "products table must still exist intact");

        var inv = db.getInvoiceById(invoiceId);
        assertNotNull(inv);
        assertEquals(payload, inv.get("customer_name"));
    }

    @Test
    @DisplayName("B7.3: Foreign Key constraint prevents Orphaned Line Item (invalid invoice_id)")
    public void testOrphanedLineItemInvalidInvoice() {
        assertThrows(SQLException.class, () -> {
            try (Statement stmt = db.getConnection().createStatement()) {
                stmt.executeUpdate("INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                        "VALUES (999999, " + harness.getCleanCodeId() + ", 1, 35.0, 35.0);");
            }
        }, "Inserting item with invalid invoice_id must fail foreign key check");
    }

    @Test
    @DisplayName("B7.4: Foreign Key constraint prevents Line Item with Invalid product_id")
    public void testLineItemInvalidProductId() throws Exception {
        // Create valid invoice
        int invId;
        try (Statement stmt = db.getConnection().createStatement()) {
            stmt.executeUpdate("INSERT INTO invoices (invoice_number, customer_name, subtotal, net_total, cash_tendered, change_due) " +
                    "VALUES ('INV-FK-TEST', 'Test', 10.0, 10.0, 10.0, 0.0);");
            try (var rs = stmt.getGeneratedKeys()) {
                rs.next();
                invId = rs.getInt(1);
            }
        }

        assertThrows(SQLException.class, () -> {
            try (Statement stmt = db.getConnection().createStatement()) {
                stmt.executeUpdate("INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                        "VALUES (" + invId + ", 999999, 1, 10.0, 10.0);");
            }
        }, "Inserting item with invalid product_id must fail foreign key check");
    }

    @Test
    @DisplayName("B7.5: Foreign Key constraint prevents Product with Invalid category_id")
    public void testProductInvalidCategoryId() {
        assertThrows(SQLException.class, () -> {
            db.insertProduct("BAD-CAT", "Bad Product", 999999, 10.0, 5.0, 10, 2, null, null, null);
        }, "Inserting product with invalid category_id must fail foreign key check");
    }

    @Test
    @DisplayName("B7.6: Incomplete Transaction Rollback leaves zero orphaned records")
    public void testRollbackLeavesZeroOrphans() throws Exception {
        int initialInvoiceCount = db.getAllInvoices().size();

        var conn = db.getConnection();
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int invId;
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("INSERT INTO invoices (invoice_number, customer_name, subtotal, net_total, cash_tendered, change_due) " +
                        "VALUES ('INV-RB', 'Rollback Customer', 50.0, 50.0, 50.0, 0.0);");
                try (var rs = s.getGeneratedKeys()) {
                    rs.next();
                    invId = rs.getInt(1);
                }
                s.executeUpdate("INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                        "VALUES (" + invId + ", " + harness.getCleanCodeId() + ", 1, 35.0, 35.0);");
            }

            // Simulate explicit rollback
            conn.rollback();
        } finally {
            conn.setAutoCommit(auto);
        }

        assertEquals(initialInvoiceCount, db.getAllInvoices().size(), "Rolled back transaction must leave 0 invoices");
    }
}
