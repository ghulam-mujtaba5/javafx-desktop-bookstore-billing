package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 6: Invoicing History Boundary & Corner Cases")
public class InvoiceBoundariesE2ETest {

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
    @DisplayName("B6.1: Non-existent Invoice ID returns null")
    public void testNonExistentInvoiceId() throws Exception {
        Map<String, Object> inv = db.getInvoiceById(999999);
        assertNull(inv, "Non-existent invoice ID must return null");
    }

    @Test
    @DisplayName("B6.2: Invoice Items for non-existent invoice returns empty list")
    public void testNonExistentInvoiceItems() throws Exception {
        List<Map<String, Object>> items = db.getInvoiceItems(999999);
        assertNotNull(items);
        assertTrue(items.isEmpty(), "Non-existent invoice must have 0 line items");
    }

    @Test
    @DisplayName("B6.3: Large Bulk Order Invoice with 50 distinct line items")
    public void testLargeBulkOrderInvoice() throws Exception {
        List<CartItem> items = new ArrayList<>();
        double subtotal = 0.0;

        for (int i = 0; i < 50; i++) {
            int pid = db.insertProduct("BULK-ITEM-" + i, "Bulk Item #" + i,
                    harness.getStationeryCategoryId(), 2.00, 1.00, 100, 5, null, null, null);
            items.add(new CartItem(pid, "Bulk Item #" + i, "Stationery", 1, 2.00, "BULK-ITEM-" + i));
            subtotal += 2.00;
        }

        int invoiceId = db.createInvoiceWithTransaction("INV-BULK-50", "Wholesale Buyer", "555-9999",
                subtotal, 0.0, 0.0, 10.0, subtotal * 0.10, subtotal * 1.10, 200.00, 200.00 - (subtotal * 1.10),
                "USD", "USA", items);

        assertTrue(invoiceId > 0);
        List<Map<String, Object>> persistedItems = db.getInvoiceItems(invoiceId);
        assertEquals(50, persistedItems.size(), "All 50 bulk items must be persisted");
    }

    @Test
    @DisplayName("B6.4: Customer Name with Special Characters and Quotes")
    public void testCustomerNameWithSpecialCharacters() throws Exception {
        String complexName = "Dr. Patrick O'Brian & Sons, LLC \"Books & Arts\"";
        CartItem item = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 1, 4.50, "ST-001");

        int invoiceId = db.createInvoiceWithTransaction("INV-SPECIAL-NAME", complexName, null,
                4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));

        Map<String, Object> inv = db.getInvoiceById(invoiceId);
        assertNotNull(inv);
        assertEquals(complexName, inv.get("customer_name"), "Quotes and ampersands must be preserved without error");
    }

    @Test
    @DisplayName("B6.5: Single Line Item with Minimum $0.01 Value")
    public void testMinimumValueLineItem() throws Exception {
        int pennyId = db.insertProduct("PENNY-1", "Single Sticky Sheet", harness.getStationeryCategoryId(),
                0.01, 0.005, 100, 10, null, null, null);

        CartItem item = new CartItem(pennyId, "Single Sticky Sheet", "Stationery", 1, 0.01, "PENNY-1");
        int invoiceId = db.createInvoiceWithTransaction("INV-PENNY", "Cash Customer", null,
                0.01, 0.0, 0.0, 0.0, 0.0, 0.01, 1.00, 0.99, "USD", "USA", Collections.singletonList(item));

        Map<String, Object> inv = db.getInvoiceById(invoiceId);
        assertEquals(0.01, (Double) inv.get("net_total"), 0.001);
        assertEquals(0.99, (Double) inv.get("change_due"), 0.001);
    }

    @Test
    @DisplayName("B6.6: Duplicate Invoice Number violates UNIQUE constraint")
    public void testDuplicateInvoiceNumber() throws Exception {
        CartItem item = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 1, 4.50, "ST-001");
        db.createInvoiceWithTransaction("INV-DUP-NUM", "Buyer 1", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));

        assertThrows(Exception.class, () -> {
            db.createInvoiceWithTransaction("INV-DUP-NUM", "Buyer 2", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));
        }, "Inserting a duplicate invoice_number must be rejected by the unique constraint");
    }
}
