package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 4: Discounting Engine E2E Tests")
public class DiscountCalculationE2ETest {

    @Test
    @DisplayName("F4.1: Zero (0%) Discount yields exactly 0.00 discount amount")
    public void testZeroDiscount() {
        CartItem item = new CartItem(1, "Textbook", "Books", 1, 100.00, "TB-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 10.0, 110.00);

        assertEquals(100.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(100.00, res.getTaxableAmount(), 0.001);
        assertEquals(10.00, res.getTaxAmount(), 0.001);
        assertEquals(110.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("F4.2: Standard 10% Discount calculation")
    public void testTenPercentDiscount() {
        CartItem item = new CartItem(1, "Notebook Bundle", "Stationery", 4, 15.00, "NB-4");
        // Subtotal = 60.00, 10% = 6.00, Taxable = 54.00
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 10.0, 5.0, 60.00);

        assertEquals(60.00, res.getSubtotal(), 0.001);
        assertEquals(6.00, res.getDiscountAmount(), 0.001);
        assertEquals(54.00, res.getTaxableAmount(), 0.001);
        assertEquals(2.70, res.getTaxAmount(), 0.001); // 5% of 54.00
        assertEquals(56.70, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("F4.3: Bulk 25% Discount calculation")
    public void testTwentyFivePercentDiscount() {
        CartItem item = new CartItem(1, "School Supplies", "Stationery", 1, 200.00, "SS-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 25.0, 0.0, 200.00);

        assertEquals(200.00, res.getSubtotal(), 0.001);
        assertEquals(50.00, res.getDiscountAmount(), 0.001);
        assertEquals(150.00, res.getTaxableAmount(), 0.001);
        assertEquals(150.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("F4.4: Half-Price 50% Clearance Discount")
    public void testFiftyPercentDiscount() {
        CartItem item = new CartItem(1, "Past Edition Book", "Books", 1, 80.00, "PB-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 50.0, 10.0, 50.00);

        assertEquals(80.00, res.getSubtotal(), 0.001);
        assertEquals(40.00, res.getDiscountAmount(), 0.001);
        assertEquals(40.00, res.getTaxableAmount(), 0.001);
        assertEquals(4.00, res.getTaxAmount(), 0.001);
        assertEquals(44.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("F4.5: Full 100% Promotional Giveaway Discount")
    public void testHundredPercentDiscount() {
        CartItem item = new CartItem(1, "Free Promotional Bookmark", "Stationery", 5, 2.00, "BM-1");
        // Subtotal = 10.00, 100% discount = 10.00, Taxable = 0.00, Tax = 0.00, Total = 0.00
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 100.0, 20.0, 0.00);

        assertEquals(10.00, res.getSubtotal(), 0.001);
        assertEquals(10.00, res.getDiscountAmount(), 0.001);
        assertEquals(0.00, res.getTaxableAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
        assertEquals(0.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F4.6: Single-Application Guarantee (prevents legacy double-discount bug)")
    public void testSingleApplicationGuarantee() {
        // In the legacy code, an item with price $100 and 10% discount was discounted in the cart
        // to $90, and then discounted AGAIN during invoice generation to $81.
        // We verify that a $100 item with 10% discount results in an EXACT taxable base of $90.00 (not $81.00).
        CartItem item = new CartItem(1, "Algorithm Book", "Books", 1, 100.00, "ALGO-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 10.0, 0.0, 100.00);

        assertEquals(100.00, res.getSubtotal(), 0.001, "Original base price must be preserved as subtotal");
        assertEquals(10.00, res.getDiscountAmount(), 0.001, "Discount must be applied exactly once (10.00)");
        assertEquals(90.00, res.getTaxableAmount(), 0.001, "Taxable base must be 90.00, NOT 81.00 (double discount)");
        assertEquals(90.00, res.getNetTotal(), 0.001);
    }
}
