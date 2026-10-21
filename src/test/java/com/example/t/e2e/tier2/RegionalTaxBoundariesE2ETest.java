package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 5: Regional Tax Boundaries & Corner Cases")
public class RegionalTaxBoundariesE2ETest {

    @Test
    @DisplayName("B5.1: 0.0% Tax Rate Boundary (Zero Tax / Exempt)")
    public void testZeroPercentTaxBoundary() {
        CartItem item = new CartItem(1, "Exempt Book", "Books", 1, 50.00, "EX-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 0.0, 0.0, 50.00);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(50.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B5.2: 5.0% UAE VAT Boundary Rate")
    public void testFivePercentTaxBoundary() {
        CartItem item = new CartItem(1, "Notebook", "Stationery", 1, 10.00, "NB-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 0.0, 5.0, 20.00);
        assertEquals(0.50, res.getTaxAmount(), 0.001);
        assertEquals(10.50, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B5.3: 15.0% Saudi VAT Boundary Rate")
    public void testFifteenPercentTaxBoundary() {
        CartItem item = new CartItem(1, "Dictionary", "Books", 1, 100.00, "DIC-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 0.0, 15.0, 150.00);
        assertEquals(15.00, res.getTaxAmount(), 0.001);
        assertEquals(115.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B5.4: 20.0% UK VAT Boundary Rate")
    public void testTwentyPercentTaxBoundary() {
        CartItem item = new CartItem(1, "Desk Organizer", "Stationery", 1, 40.00, "DO-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 0.0, 20.0, 50.00);
        assertEquals(8.00, res.getTaxAmount(), 0.001);
        assertEquals(48.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B5.5: Negative Tax Rate throws IllegalArgumentException")
    public void testNegativeTaxRateRejection() {
        CartItem item = new CartItem(1, "Item", "Books", 1, 10.00, "IT-1");
        assertThrows(IllegalArgumentException.class, () -> {
            CalculationOracle.calculate(Collections.singletonList(item), 0.0, -1.0, 10.00);
        }, "Negative tax rate must be rejected");
    }

    @Test
    @DisplayName("B5.6: Tax Half-Up Rounding Boundary (0.005 rounds to 0.01)")
    public void testTaxHalfUpRoundingBoundary() {
        // Price 0.10, Tax Rate 5% -> 0.10 * 0.05 = 0.005 -> rounds to 0.01
        CartItem item = new CartItem(1, "Cheap Item", "Stationery", 1, 0.10, "CH-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 0.0, 5.0, 1.00);
        assertEquals(0.01, res.getTaxAmount(), 0.001);
        assertEquals(0.11, res.getNetTotal(), 0.001);
    }
}
