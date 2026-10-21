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

@DisplayName("Tier 4 - Scenario 4: Saudi Riyadh Educational Supply End-to-End Session")
public class GulfSaudiEducationalScenarioE2ETest {

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
    @DisplayName("Complete Saudi Educational Session: 15% VAT -> Bulk School Supplies -> Transaction Audit")
    public void testSaudiEducationalSupplySession() throws Exception {
        // Step 1: Configure store for Saudi Arabia
        db.updateStoreSettings(
                "مكتبة الرياض التعليمية (Riyadh Educational Supplies)",
                "King Fahd Road, Al Olaya, Riyadh 12214",
                "+966 11 465 0000", "GULF_SAUDI", "SAR", "SAR", "VAT", 15.00, "BILINGUAL_AR_EN");

        Map<String, Object> settings = db.getStoreSettings();
        assertEquals("SAR", settings.get("currency_code"));
        assertEquals(15.00, (Double) settings.get("tax_rate"), 0.001);

        // Step 2: Assemble bulk educational basket
        CartItem textbooks = new CartItem(harness.getJava21Id(), "Modern Java in Action", "Books", 5, 45.00, "BK-002");
        CartItem notebooks = new CartItem(harness.getNotebookId(), "A5 Spiral Notebook", "Stationery", 20, 4.50, "ST-001");
        CartItem pens = new CartItem(harness.getPenId(), "Gel Pen Pack", "Stationery", 10, 6.00, "ST-002");
        List<CartItem> cart = Arrays.asList(textbooks, notebooks, pens);

        // Step 3: Math verification with 20% institutional discount and 15% Saudi VAT
        // Subtotal = (5 * 45 = 225.00) + (20 * 4.50 = 90.00) + (10 * 6.00 = 60.00) = 375.00
        // 20% discount = 75.00 -> Taxable = 300.00
        // 15% Saudi VAT = 45.00
        // Net Total = 300.00 + 45.00 = 345.00
        // Cash Tendered = SAR 400.00 -> Change = SAR 55.00
        OrderCalculationResult res = CalculationOracle.calculateForRegion(cart, 20.0, RegionalPreset.GULF_SAUDI, 400.00);

        assertEquals(375.00, res.getSubtotal(), 0.001);
        assertEquals(75.00, res.getDiscountAmount(), 0.001);
        assertEquals(300.00, res.getTaxableAmount(), 0.001);
        assertEquals(45.00, res.getTaxAmount(), 0.001);
        assertEquals(345.00, res.getNetTotal(), 0.001);
        assertEquals(400.00, res.getCashTendered(), 0.001);
        assertEquals(55.00, res.getChangeDue(), 0.001);

        // Step 4: Checkout transaction
        String institutionName = "مدارس الرياض الأهلية (Riyadh International Schools)";
        int invoiceId = db.createInvoiceWithTransaction(
                "INV-KSA-2026-888", institutionName, "+966551234567",
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "SAR", "GULF_SAUDI", res.getItems());

        assertTrue(invoiceId > 0);

        // Step 5: Verify bulk stock decrements
        assertEquals(25, db.getProductStock(harness.getJava21Id()), "Java 21 stock reduced from 30 to 25");
        assertEquals(80, db.getProductStock(harness.getNotebookId()), "Notebooks stock reduced from 100 to 80");
        assertEquals(65, db.getProductStock(harness.getPenId()), "Pens stock reduced from 75 to 65");

        // Step 6: Verify CSV ledger format
        String csvLine = CsvTestHelper.formatRow(Arrays.asList(
                "INV-KSA-2026-888", institutionName, "375.00", "75.00", "45.00", "345.00", "55.00"
        ));
        List<List<String>> parsed = CsvTestHelper.parseCsv(csvLine);
        assertEquals("345.00", parsed.get(0).get(5));
        assertEquals("55.00", parsed.get(0).get(6));
    }
}
