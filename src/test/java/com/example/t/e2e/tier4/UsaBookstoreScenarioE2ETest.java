package com.example.t.e2e.tier4;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import com.example.t.e2e.harness.CalculationOracle.RegionalPreset;
import com.example.t.e2e.harness.CsvTestHelper;
import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 4 - Scenario 1: USA University Bookstore Rush End-to-End Session")
public class UsaBookstoreScenarioE2ETest {

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
    @DisplayName("Complete USA Bookstore Rush Shopping Session: Cart -> Math -> Transaction -> Audit")
    public void testUsaBookstoreRushSession() throws Exception {
        // Step 1: Verify store configuration is USA
        Map<String, Object> settings = db.getStoreSettings();
        assertEquals("USA", settings.get("region_preset"));
        assertEquals("USD", settings.get("currency_code"));
        assertEquals("$", settings.get("currency_symbol"));

        // Step 2: Record baseline stock counts
        int initialCleanCodeStock = db.getProductStock(harness.getCleanCodeId());   // 50
        int initialJava21Stock = db.getProductStock(harness.getJava21Id());         // 30
        int initialNotebookStock = db.getProductStock(harness.getNotebookId());     // 100
        int initialPenStock = db.getProductStock(harness.getPenId());               // 75

        // Step 3: Build student's shopping cart
        CartItem item1 = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 1, 35.00, "BK-001");
        CartItem item2 = new CartItem(harness.getJava21Id(), "Java 21 in Action", "Books", 1, 45.00, "BK-002");
        CartItem item3 = new CartItem(harness.getNotebookId(), "A5 Spiral Notebook", "Stationery", 2, 4.50, "ST-001");
        CartItem item4 = new CartItem(harness.getPenId(), "Gel Pen Pack", "Stationery", 1, 6.00, "ST-002");
        List<CartItem> cart = Arrays.asList(item1, item2, item3, item4);

        // Step 4: Execute POS calculation with 10% student discount and 8.25% Texas sales tax
        // Subtotal = 35.00 + 45.00 + 9.00 + 6.00 = 95.00
        // 10% discount = 9.50 -> Taxable = 85.50
        // 8.25% Sales Tax of 85.50 = 7.05375 -> rounds to 7.05
        // Net Total = 85.50 + 7.05 = 92.55
        // Student tenders $100.00 bill -> Change Due = 7.45
        OrderCalculationResult res = CalculationOracle.calculateForRegion(cart, 10.0, RegionalPreset.USA, 100.00);

        assertEquals(95.00, res.getSubtotal(), 0.001);
        assertEquals(9.50, res.getDiscountAmount(), 0.001);
        assertEquals(85.50, res.getTaxableAmount(), 0.001);
        assertEquals(7.05, res.getTaxAmount(), 0.001);
        assertEquals(92.55, res.getNetTotal(), 0.001);
        assertEquals(100.00, res.getCashTendered(), 0.001);
        assertEquals(7.45, res.getChangeDue(), 0.001);

        // Step 5: Execute atomic checkout transaction in SQLite
        int invoiceId = db.createInvoiceWithTransaction(
                "INV-USA-2026-001", "Emma Watson (CS Major)", "555-0142",
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "USD", "USA", res.getItems());

        assertTrue(invoiceId > 0, "Invoice must be created with valid ID");

        // Step 6: Verify stock decrements in catalog
        assertEquals(initialCleanCodeStock - 1, db.getProductStock(harness.getCleanCodeId()), "Clean Code stock decremented by 1");
        assertEquals(initialJava21Stock - 1, db.getProductStock(harness.getJava21Id()), "Java 21 stock decremented by 1");
        assertEquals(initialNotebookStock - 2, db.getProductStock(harness.getNotebookId()), "Notebook stock decremented by 2");
        assertEquals(initialPenStock - 1, db.getProductStock(harness.getPenId()), "Pen stock decremented by 1");

        // Step 7: Verify invoice header and line items persistence
        Map<String, Object> invoice = db.getInvoiceById(invoiceId);
        assertNotNull(invoice);
        assertEquals("INV-USA-2026-001", invoice.get("invoice_number"));
        assertEquals("Emma Watson (CS Major)", invoice.get("customer_name"));
        assertEquals(92.55, (Double) invoice.get("net_total"), 0.001);
        assertEquals(7.45, (Double) invoice.get("change_due"), 0.001);

        List<Map<String, Object>> lineItems = db.getInvoiceItems(invoiceId);
        assertEquals(4, lineItems.size(), "All 4 cart items must be recorded in line items table");

        // Step 8: Export session to CSV and verify RFC 4180 output
        List<String> exportRow = Arrays.asList(
                (String) invoice.get("invoice_number"),
                (String) invoice.get("customer_name"),
                String.valueOf(invoice.get("subtotal")),
                String.valueOf(invoice.get("tax_amount")),
                String.valueOf(invoice.get("net_total")),
                String.valueOf(invoice.get("change_due"))
        );
        String csvLine = CsvTestHelper.formatRow(exportRow);
        List<List<String>> parsedCsv = CsvTestHelper.parseCsv(csvLine);
        assertEquals(1, parsedCsv.size());
        assertEquals("INV-USA-2026-001", parsedCsv.get(0).get(0));
        assertEquals("Emma Watson (CS Major)", parsedCsv.get(0).get(1));
        assertEquals("92.55", parsedCsv.get(0).get(4));
    }
}
