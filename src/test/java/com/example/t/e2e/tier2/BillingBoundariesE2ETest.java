package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 3: Billing Calculation Boundary & Corner Cases")
public class BillingBoundariesE2ETest {

    @Test
    @DisplayName("B3.1: Empty Cart yields all zero monetary totals")
    public void testEmptyCartCalculation() {
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.emptyList(), 0.0, 10.0, 0.00);

        assertEquals(0.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(0.00, res.getTaxableAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
        assertEquals(0.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("B3.2: Null Cart parameter handled safely as empty cart")
    public void testNullCartParameter() {
        OrderCalculationResult res = CalculationOracle.calculate(
                null, 15.0, 5.0, 50.00);

        assertEquals(0.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
        assertEquals(50.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("B3.3: Single Unit Quantity boundary (quantity = 1)")
    public void testSingleUnitQuantityBoundary() {
        CartItem item = new CartItem(1, "Eraser", "Stationery", 1, 0.99, "ER-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 0.0, 1.00);

        assertEquals(0.99, res.getSubtotal(), 0.001);
        assertEquals(0.99, res.getNetTotal(), 0.001);
        assertEquals(0.01, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("B3.4: Sub-cent fractional price rounding boundary")
    public void testSubCentRoundingBoundary() {
        // Price of 0.005 rounds up to 0.01
        CartItem item = new CartItem(1, "Individual Paper Clip", "Stationery", 1, 0.005, "PC-1");
        assertEquals(0.01, item.getLineSubtotal(), 0.001);

        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 0.0, 1.00);

        assertEquals(0.01, res.getSubtotal(), 0.001);
        assertEquals(0.99, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("B3.5: Negative Unit Price in CartItem throws IllegalArgumentException")
    public void testNegativePriceThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CartItem(1, "Bad Item", "Stationery", 1, -10.00, "BAD");
        });
    }

    @Test
    @DisplayName("B3.6: Negative Quantity in CartItem throws IllegalArgumentException")
    public void testNegativeQuantityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CartItem(1, "Bad Item", "Stationery", -1, 10.00, "BAD");
        });
    }
}
