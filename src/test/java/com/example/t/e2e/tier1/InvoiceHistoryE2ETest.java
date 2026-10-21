package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 6: Invoicing & Ledger History E2E Tests")
public class InvoiceHistoryE2ETest {

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
    @DisplayName("F6.1: Create Invoice with line items in SQLite transaction")
    public void testCreateInvoice() throws Exception {
        CartItem item1 = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 1, 35.00, "BK-001");
        CartItem item2 = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 2, 4.50, "ST-001");

        int invoiceId = db.createInvoiceWithTransaction(
                "INV-1001", "Alice Smith", "555-0199",
                44.00, 0.0, 0.0, 8.25, 3.63, 47.63, 50.00, 2.37,
                "USD", "USA", Arrays.asList(item1, item2));

        assertTrue(invoiceId > 0, "Created invoice ID must be positive");

        Map<String, Object> inv = db.getInvoiceById(invoiceId);
        assertNotNull(inv);
        assertEquals("INV-1001", inv.get("invoice_number"));
        assertEquals("Alice Smith", inv.get("customer_name"));
        assertEquals("555-0199", inv.get("customer_phone"));
        assertEquals(44.00, (Double) inv.get("subtotal"), 0.001);
        assertEquals(47.63, (Double) inv.get("net_total"), 0.001);
        assertEquals(2.37, (Double) inv.get("change_due"), 0.001);
    }

    @Test
    @DisplayName("F6.2: Retrieve Line Items for an Invoice with product details")
    public void testGetInvoiceLineItems() throws Exception {
        CartItem item1 = new CartItem(harness.getJava21Id(), "Java 21", "Books", 1, 45.00, "BK-002");
        CartItem item2 = new CartItem(harness.getPenId(), "Gel Pen", "Stationery", 3, 6.00, "ST-002");

        int invoiceId = db.createInvoiceWithTransaction(
                "INV-1002", "Bob Jones", "555-0244",
                63.00, 0.0, 0.0, 5.0, 3.15, 66.15, 70.00, 3.85,
                "USD", "USA", Arrays.asList(item1, item2));

        List<Map<String, Object>> items = db.getInvoiceItems(invoiceId);
        assertEquals(2, items.size(), "Invoice must have exactly 2 line items");

        Map<String, Object> first = items.get(0);
        assertEquals(harness.getJava21Id(), first.get("product_id"));
        assertEquals(1, first.get("quantity"));
        assertEquals(45.00, (Double) first.get("unit_price"), 0.001);
        assertEquals(45.00, (Double) first.get("line_total"), 0.001);

        Map<String, Object> second = items.get(1);
        assertEquals(harness.getPenId(), second.get("product_id"));
        assertEquals(3, second.get("quantity"));
        assertEquals(6.00, (Double) second.get("unit_price"), 0.001);
        assertEquals(18.00, (Double) second.get("line_total"), 0.001);
    }

    @Test
    @DisplayName("F6.3: List All Invoices ordered by ID descending")
    public void testGetAllInvoicesOrdered() throws Exception {
        CartItem item = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 1, 4.50, "ST-001");
        db.createInvoiceWithTransaction("INV-A", "Customer A", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));
        db.createInvoiceWithTransaction("INV-B", "Customer B", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));

        List<Map<String, Object>> all = db.getAllInvoices();
        assertTrue(all.size() >= 2);
        // Most recent invoice appears first
        assertEquals("INV-B", all.get(0).get("invoice_number"));
        assertEquals("INV-A", all.get(1).get("invoice_number"));
    }

    @Test
    @DisplayName("F6.4: Blank or Null Customer Name defaults to 'Walk-in Customer'")
    public void testDefaultWalkInCustomer() throws Exception {
        CartItem item = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 1, 4.50, "ST-001");
        int invId1 = db.createInvoiceWithTransaction("INV-NULL-CUST", null, null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));
        int invId2 = db.createInvoiceWithTransaction("INV-BLANK-CUST", "   ", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));

        assertEquals("Walk-in Customer", db.getInvoiceById(invId1).get("customer_name"));
        assertEquals("Walk-in Customer", db.getInvoiceById(invId2).get("customer_name"));
    }

    @Test
    @DisplayName("F6.5: Verify Stock Decrement on Successful Invoice Creation")
    public void testInvoiceDecrementsStock() throws Exception {
        int initialStock = db.getProductStock(harness.getCleanCodeId());
        assertEquals(50, initialStock);

        CartItem item = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 3, 35.00, "BK-001");
        db.createInvoiceWithTransaction("INV-DEC", "Buyer", null, 105.00, 0.0, 0.0, 0.0, 0.0, 105.00, 110.00, 5.00, "USD", "USA", Collections.singletonList(item));

        int postStock = db.getProductStock(harness.getCleanCodeId());
        assertEquals(47, postStock, "Stock must decrement from 50 to 47 upon invoice creation");
    }

    @Test
    @DisplayName("F6.6: Cascade Delete removes Line Items when Invoice is deleted")
    public void testCascadeDeleteInvoiceItems() throws Exception {
        CartItem item = new CartItem(harness.getPenId(), "Gel Pen", "Stationery", 1, 6.00, "ST-002");
        int invId = db.createInvoiceWithTransaction("INV-DEL", "Temp", null, 6.00, 0.0, 0.0, 0.0, 0.0, 6.00, 10.00, 4.00, "USD", "USA", Collections.singletonList(item));

        assertFalse(db.getInvoiceItems(invId).isEmpty());

        // Delete invoice directly from database
        try (var stmt = db.getConnection().createStatement()) {
            stmt.executeUpdate("DELETE FROM invoices WHERE invoice_id = " + invId + ";");
        }

        assertNull(db.getInvoiceById(invId));
        assertTrue(db.getInvoiceItems(invId).isEmpty(), "Line items must be deleted via CASCADE");
    }
}
