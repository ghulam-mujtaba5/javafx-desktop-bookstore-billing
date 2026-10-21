package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 4: Discounting Engine Boundary & Corner Cases")
public class DiscountBoundariesE2ETest {

    @Test
    @DisplayName("B4.1: Negative Discount (< 0.0%) throws IllegalArgumentException")
    public void testNegativeDiscountRejection() {
        CartItem item = new CartItem(1, "Book", "Books", 1, 50.00, "BK-1");
        assertThrows(IllegalArgumentException.class, () -> {
            CalculationOracle.calculate(Collections.singletonList(item), -0.01, 10.0, 50.00);
        }, "Negative discount percentage must be rejected");
    }

    @Test
    @DisplayName("B4.2: Discount exceeding 100.0% throws IllegalArgumentException")
    public void testExcessiveDiscountRejection() {
        CartItem item = new CartItem(1, "Book", "Books", 1, 50.00, "BK-1");
        assertThrows(IllegalArgumentException.class, () -> {
            CalculationOracle.calculate(Collections.singletonList(item), 100.01, 10.0, 50.00);
        }, "Discount greater than 100.0% must be rejected");
    }

    @Test
    @DisplayName("B4.3: Exact 0.0% Discount Boundary")
    public void testZeroDiscountBoundary() {
        CartItem item = new CartItem(1, "Book", "Books", 1, 75.00, "BK-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 0.0, 0.0, 75.00);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(75.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B4.4: Exact 100.0% Discount Boundary yields 0.00 Net Total")
    public void testFullDiscountBoundary() {
        CartItem item = new CartItem(1, "Book", "Books", 2, 40.00, "BK-1");
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 100.0, 20.0, 0.00);
        assertEquals(80.00, res.getSubtotal(), 0.001);
        assertEquals(80.00, res.getDiscountAmount(), 0.001);
        assertEquals(0.00, res.getTaxableAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B4.5: Fractional Discount (33.33% on 300.00)")
    public void testFractionalDiscount() {
        CartItem item = new CartItem(1, "Encyclopaedia", "Books", 1, 300.00, "ENC-1");
        // 33.33% of 300.00 = 99.99
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 33.33, 0.0, 300.00);
        assertEquals(99.99, res.getDiscountAmount(), 0.001);
        assertEquals(200.01, res.getTaxableAmount(), 0.001);
        assertEquals(200.01, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("B4.6: High-Value Order with 15% Bulk Corporate Discount")
    public void testHighValueOrderDiscount() {
        CartItem item = new CartItem(1, "Library Annual Supply", "Books", 100, 100.00, "LIB-1");
        // Subtotal = 10,000.00, 15% discount = 1,500.00, Taxable = 8,500.00
        OrderCalculationResult res = CalculationOracle.calculate(Collections.singletonList(item), 15.0, 5.0, 10000.00);
        assertEquals(10000.00, res.getSubtotal(), 0.001);
        assertEquals(1500.00, res.getDiscountAmount(), 0.001);
        assertEquals(8500.00, res.getTaxableAmount(), 0.001);
        assertEquals(425.00, res.getTaxAmount(), 0.001); // 5% of 8500
        assertEquals(8925.00, res.getNetTotal(), 0.001);
        assertEquals(1075.00, res.getChangeDue(), 0.001);
    }
}
