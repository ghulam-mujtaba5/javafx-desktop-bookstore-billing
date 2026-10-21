package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import com.example.t.e2e.harness.CalculationOracle.RegionalPreset;
import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 5: Multi-Regional Tax & Currency Switching E2E Tests")
public class MultiRegionalTaxE2ETest {

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
    @DisplayName("F5.1: USA Regional Preset (USD, Sales Tax, 8.25%)")
    public void testUsaRegionalPreset() {
        RegionalPreset usa = RegionalPreset.USA;
        assertEquals("USD", usa.getCurrencyCode());
        assertEquals("$", usa.getCurrencySymbol());
        assertEquals("Sales Tax", usa.getTaxName());
        assertEquals(8.25, usa.getDefaultTaxRate(), 0.001);
        assertEquals("EN", usa.getReceiptLanguage());

        CartItem item = new CartItem(1, "Notebook", "Stationery", 2, 10.00, "NB-1");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(item), 0.0, usa, 25.00);

        assertEquals(20.00, res.getSubtotal(), 0.001);
        assertEquals(1.65, res.getTaxAmount(), 0.001); // 8.25% of 20.00
        assertEquals(21.65, res.getNetTotal(), 0.001);
        assertEquals(3.35, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F5.2: UK Regional Preset (GBP, £, 20% VAT)")
    public void testUkRegionalPreset() {
        RegionalPreset uk = RegionalPreset.UK;
        assertEquals("GBP", uk.getCurrencyCode());
        assertEquals("£", uk.getCurrencySymbol());
        assertEquals("VAT", uk.getTaxName());
        assertEquals(20.00, uk.getDefaultTaxRate(), 0.001);

        CartItem item = new CartItem(1, "Fountain Pen", "Stationery", 1, 50.00, "FP-1");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(item), 10.0, uk, 50.00);

        // Subtotal = 50.00, 10% disc = 5.00, Taxable = 45.00
        // 20% VAT = 9.00, Net Total = 54.00
        assertEquals(50.00, res.getSubtotal(), 0.001);
        assertEquals(5.00, res.getDiscountAmount(), 0.001);
        assertEquals(45.00, res.getTaxableAmount(), 0.001);
        assertEquals(9.00, res.getTaxAmount(), 0.001);
        assertEquals(54.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("F5.3: UAE Regional Preset (AED, 5% VAT, Bilingual Arabic-English)")
    public void testUaeRegionalPreset() {
        RegionalPreset uae = RegionalPreset.GULF_UAE;
        assertEquals("AED", uae.getCurrencyCode());
        assertEquals("VAT", uae.getTaxName());
        assertEquals(5.00, uae.getDefaultTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", uae.getReceiptLanguage());

        CartItem item = new CartItem(1, "Calligraphy Set", "Stationery", 1, 100.00, "CS-1");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(item), 0.0, uae, 110.00);

        assertEquals(100.00, res.getSubtotal(), 0.001);
        assertEquals(5.00, res.getTaxAmount(), 0.001); // 5% of 100
        assertEquals(105.00, res.getNetTotal(), 0.001);
        assertEquals(5.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F5.4: Saudi Arabia Regional Preset (SAR, 15% VAT, Bilingual Arabic-English)")
    public void testSaudiRegionalPreset() {
        RegionalPreset saudi = RegionalPreset.GULF_SAUDI;
        assertEquals("SAR", saudi.getCurrencyCode());
        assertEquals("VAT", saudi.getTaxName());
        assertEquals(15.00, saudi.getDefaultTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", saudi.getReceiptLanguage());

        CartItem item = new CartItem(1, "Arabic Literature Anthology", "Books", 2, 80.00, "AL-1");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(item), 0.0, saudi, 200.00);

        assertEquals(160.00, res.getSubtotal(), 0.001);
        assertEquals(24.00, res.getTaxAmount(), 0.001); // 15% of 160.00
        assertEquals(184.00, res.getNetTotal(), 0.001);
        assertEquals(16.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F5.5: Qatar Regional Preset (QAR, 0% VAT, Bilingual Arabic-English)")
    public void testQatarRegionalPreset() {
        RegionalPreset qatar = RegionalPreset.GULF_QATAR;
        assertEquals("QAR", qatar.getCurrencyCode());
        assertEquals(0.00, qatar.getDefaultTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", qatar.getReceiptLanguage());

        CartItem item = new CartItem(1, "Textbook", "Books", 1, 75.00, "Q-TB");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(item), 0.0, qatar, 100.00);

        assertEquals(75.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(75.00, res.getNetTotal(), 0.001);
        assertEquals(25.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F5.6: Store Configuration Switch in Database reflects new region")
    public void testStoreConfigurationSwitch() throws Exception {
        Map<String, Object> initial = db.getStoreSettings();
        assertEquals("USA", initial.get("region_preset"));

        // Switch settings to UK
        boolean updated = db.updateStoreSettings(
                "London Bookshop & Stationery", "42 Oxford Street, London", "+44 20 7946 0912",
                "UK", "GBP", "£", "VAT", 20.00, "EN");
        assertTrue(updated, "Update store settings must succeed");

        Map<String, Object> refreshed = db.getStoreSettings();
        assertEquals("UK", refreshed.get("region_preset"));
        assertEquals("GBP", refreshed.get("currency_code"));
        assertEquals("£", refreshed.get("currency_symbol"));
        assertEquals("VAT", refreshed.get("tax_name"));
        assertEquals(20.00, (Double) refreshed.get("tax_rate"), 0.001);
    }
}
