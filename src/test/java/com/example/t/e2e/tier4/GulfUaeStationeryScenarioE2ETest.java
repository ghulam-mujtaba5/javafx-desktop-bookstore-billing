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

@DisplayName("Tier 4 - Scenario 3: UAE Dubai Mall Modern Stationery End-to-End Session")
public class GulfUaeStationeryScenarioE2ETest {

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
    @DisplayName("Complete UAE Dubai Mall Session: Bilingual Store -> 5% VAT -> Arabic Receipt & Inventory Audit")
    public void testUaeStationerySession() throws Exception {
        // Step 1: Set store configuration to UAE preset
        db.updateStoreSettings(
                "مكتبة القلم الذهبي (Golden Pen Stationery)",
                "Dubai Mall, Financial Center Rd, Downtown Dubai",
                "+971 4 362 7500", "UAE", "AED", "AED", "VAT", 5.00, "BILINGUAL_AR_EN");

        Map<String, Object> settings = db.getStoreSettings();
        assertEquals("UAE", settings.get("region_preset"));
        assertEquals("BILINGUAL_AR_EN", settings.get("receipt_language"));
        assertEquals(5.00, (Double) settings.get("tax_rate"), 0.001);

        // Step 2: Customer cart selection
        CartItem pens = new CartItem(harness.getPenId(), "Gel Pen Pack", "Stationery", 4, 6.00, "ST-002");
        CartItem notebooks = new CartItem(harness.getNotebookId(), "A5 Spiral Notebook", "Stationery", 5, 4.50, "ST-001");
        CartItem book = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 1, 35.00, "BK-001");
        List<CartItem> cart = Arrays.asList(pens, notebooks, book);

        // Step 3: Math verification with 15% corporate discount and 5% UAE VAT
        // Subtotal = (4 * 6.00 = 24.00) + (5 * 4.50 = 22.50) + 35.00 = 81.50
        // 15% discount = 12.225 -> 12.23
        // Taxable = 81.50 - 12.23 = 69.27
        // 5% VAT on 69.27 = 3.4635 -> 3.46
        // Net Total = 69.27 + 3.46 = 72.73
        // Tendered AED 100.00 -> Change = AED 27.27
        OrderCalculationResult res = CalculationOracle.calculateForRegion(cart, 15.0, RegionalPreset.GULF_UAE, 100.00);

        assertEquals(81.50, res.getSubtotal(), 0.001);
        assertEquals(12.23, res.getDiscountAmount(), 0.001);
        assertEquals(69.27, res.getTaxableAmount(), 0.001);
        assertEquals(3.46, res.getTaxAmount(), 0.001);
        assertEquals(72.73, res.getNetTotal(), 0.001);
        assertEquals(27.27, res.getChangeDue(), 0.001);

        // Step 4: Execute database transaction with Arabic customer details
        String customerName = "سالم الشامسي (Salem Al-Shamsi)";
        int invoiceId = db.createInvoiceWithTransaction(
                "INV-DXB-2026-909", customerName, "+971509988776",
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "AED", "UAE", res.getItems());

        assertTrue(invoiceId > 0);

        // Step 5: Check line items and inventory
        assertEquals(71, db.getProductStock(harness.getPenId()), "Pens stock reduced from 75 to 71");
        assertEquals(95, db.getProductStock(harness.getNotebookId()), "Notebooks stock reduced from 100 to 95");
        assertEquals(49, db.getProductStock(harness.getCleanCodeId()), "Clean Code stock reduced from 50 to 49");

        Map<String, Object> inv = db.getInvoiceById(invoiceId);
        assertEquals(customerName, inv.get("customer_name"));
        assertEquals("AED", inv.get("currency_code"));

        // Step 6: Verify CSV export preserves Arabic unicode and numbers
        List<String> csvRow = Arrays.asList(
                (String) inv.get("invoice_number"),
                (String) inv.get("customer_name"),
                "AED", String.valueOf(inv.get("net_total")), String.valueOf(inv.get("change_due"))
        );
        String formatted = CsvTestHelper.formatRow(csvRow);
        List<List<String>> parsed = CsvTestHelper.parseCsv(formatted);
        assertEquals(customerName, parsed.get(0).get(1));
        assertEquals("72.73", parsed.get(0).get(3));
    }
}
