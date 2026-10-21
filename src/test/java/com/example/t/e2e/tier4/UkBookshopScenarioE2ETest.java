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

@DisplayName("Tier 4 - Scenario 2: UK High-Street Bookshop & Stationery End-to-End Session")
public class UkBookshopScenarioE2ETest {

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
    @DisplayName("Complete UK Bookshop Session: Regional Switch -> Cart -> 20% VAT -> Persistent Ledger")
    public void testUkBookshopSession() throws Exception {
        // Step 1: Configure store for United Kingdom
        db.updateStoreSettings("Piccadilly Books & Paper", "214 Piccadilly, London W1J 9HN",
                "+44 20 7434 0000", "UK", "GBP", "£", "VAT", 20.00, "EN");

        Map<String, Object> settings = db.getStoreSettings();
        assertEquals("UK", settings.get("region_preset"));
        assertEquals("GBP", settings.get("currency_code"));
        assertEquals("£", settings.get("currency_symbol"));
        assertEquals(20.00, (Double) settings.get("tax_rate"), 0.001);

        // Step 2: Customer selects items
        CartItem orwell = new CartItem(harness.getOrwellId(), "1984", "Books", 2, 15.00, "BK-003");
        CartItem notebook = new CartItem(harness.getNotebookId(), "A5 Spiral Notebook", "Stationery", 3, 4.50, "ST-001");
        CartItem sticky = new CartItem(harness.getStickyNotesId(), "Sticky Notes", "Stationery", 2, 2.50, "ST-003");
        List<CartItem> cart = Arrays.asList(orwell, notebook, sticky);

        // Step 3: Math calculation with 5% member discount and 20% VAT
        // Subtotal = (2 * 15.00 = 30.00) + (3 * 4.50 = 13.50) + (2 * 2.50 = 5.00) = 48.50
        // 5% discount = 2.425 -> 2.43
        // Taxable = 48.50 - 2.43 = 46.07
        // 20% VAT on 46.07 = 9.214 -> 9.21
        // Net Total = 46.07 + 9.21 = 55.28
        // Customer pays £60.00 -> Change = £4.72
        OrderCalculationResult res = CalculationOracle.calculateForRegion(cart, 5.0, RegionalPreset.UK, 60.00);

        assertEquals(48.50, res.getSubtotal(), 0.001);
        assertEquals(2.43, res.getDiscountAmount(), 0.001);
        assertEquals(46.07, res.getTaxableAmount(), 0.001);
        assertEquals(9.21, res.getTaxAmount(), 0.001);
        assertEquals(55.28, res.getNetTotal(), 0.001);
        assertEquals(60.00, res.getCashTendered(), 0.001);
        assertEquals(4.72, res.getChangeDue(), 0.001);

        // Step 4: Checkout transaction
        int invId = db.createInvoiceWithTransaction(
                "INV-UK-2026-042", "Oliver Twist", "+447911123456",
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "GBP", "UK", res.getItems());

        assertTrue(invId > 0);

        // Step 5: Verify sticky notes dropped from 4 to 2 (remains below threshold 5)
        assertEquals(2, db.getProductStock(harness.getStickyNotesId()));
        assertEquals(8, db.getProductStock(harness.getOrwellId()), "1984 stock dropped from 10 to 8");

        // Step 6: Verify CSV ledger
        String csvLine = CsvTestHelper.formatRow(Arrays.asList(
                "INV-UK-2026-042", "Oliver Twist", "GBP", "55.28", "4.72"
        ));
        List<List<String>> parsed = CsvTestHelper.parseCsv(csvLine);
        assertEquals("55.28", parsed.get(0).get(3));
    }
}
