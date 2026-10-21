package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 3: POS Billing Calculations E2E Tests")
public class PosBillingE2ETest {

    @Test
    @DisplayName("F3.1: Single Item Order Calculation without Discount")
    public void testSingleItemCalculation() {
        CartItem item = new CartItem(1, "Clean Code", "Books", 2, 35.00, "BK-001");
        // Subtotal = 2 * 35.00 = 70.00
        // Discount = 0% -> 0.00
        // Tax = 8.25% of 70.00 = 5.775 -> 5.78
        // Total = 75.78
        // Cash = 100.00 -> Change = 24.22
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 8.25, 100.00);

        assertEquals(70.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(70.00, res.getTaxableAmount(), 0.001);
        assertEquals(5.78, res.getTaxAmount(), 0.001);
        assertEquals(75.78, res.getNetTotal(), 0.001);
        assertEquals(24.22, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F3.2: Multi-Item Basket Calculation with Books and Stationery")
    public void testMultiItemBasketCalculation() {
        CartItem book = new CartItem(1, "Modern Java in Action", "Books", 1, 45.00, "BK-002");
        CartItem notebook = new CartItem(2, "A5 Spiral Notebook", "Stationery", 3, 4.50, "ST-001");
        CartItem pen = new CartItem(3, "Gel Pen Pack", "Stationery", 2, 6.00, "ST-002");

        // Subtotal = 45.00 + (3 * 4.50 = 13.50) + (2 * 6.00 = 12.00) = 70.50
        // Discount = 10% -> 7.05
        // Taxable = 70.50 - 7.05 = 63.45
        // Tax = 5.0% of 63.45 = 3.1725 -> 3.17
        // Net Total = 63.45 + 3.17 = 66.62
        // Cash = 70.00 -> Change = 3.38
        List<CartItem> cart = Arrays.asList(book, notebook, pen);
        OrderCalculationResult res = CalculationOracle.calculate(cart, 10.0, 5.0, 70.00);

        assertEquals(70.50, res.getSubtotal(), 0.001);
        assertEquals(7.05, res.getDiscountAmount(), 0.001);
        assertEquals(63.45, res.getTaxableAmount(), 0.001);
        assertEquals(3.17, res.getTaxAmount(), 0.001);
        assertEquals(66.62, res.getNetTotal(), 0.001);
        assertEquals(3.38, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F3.3: Taxable Amount strictly equals Subtotal minus Discount")
    public void testTaxableAmountFormula() {
        CartItem item = new CartItem(1, "Reference Book", "Books", 1, 150.00, "REF-1");
        // 20% discount on 150.00 = 30.00 discount -> Taxable amount = 120.00
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 20.0, 10.0, 150.00);

        assertEquals(150.00, res.getSubtotal(), 0.001);
        assertEquals(30.00, res.getDiscountAmount(), 0.001);
        assertEquals(120.00, res.getTaxableAmount(), 0.001);
        assertEquals(12.00, res.getTaxAmount(), 0.001); // 10% of 120
        assertEquals(132.00, res.getNetTotal(), 0.001);
    }

    @Test
    @DisplayName("F3.4: Cash Tendered with change calculation")
    public void testCashTenderedAndChange() {
        CartItem item = new CartItem(1, "Stationery Set", "Stationery", 1, 28.50, "SET-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 0.0, 50.00);

        assertEquals(28.50, res.getNetTotal(), 0.001);
        assertEquals(50.00, res.getCashTendered(), 0.001);
        assertEquals(21.50, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F3.5: Exact Cash Tendered produces zero change due")
    public void testExactCashChangeZero() {
        CartItem item = new CartItem(1, "1984 Book", "Books", 2, 15.00, "BK-003");
        // Subtotal = 30.00, 20% VAT = 6.00, Net Total = 36.00
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 20.0, 36.00);

        assertEquals(36.00, res.getNetTotal(), 0.001);
        assertEquals(0.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("F3.6: Half-Up Rounding to exactly two decimal places")
    public void testHalfUpRoundingPrecision() {
        // 1 item @ 10.555 -> rounds to 10.56
        assertEquals(10.56, CalculationOracle.roundMoney(10.555), 0.0001);
        // 1 item @ 10.554 -> rounds to 10.55
        assertEquals(10.55, CalculationOracle.roundMoney(10.554), 0.0001);
        // Tax rate 7.5% on 33.33 = 2.49975 -> rounds to 2.50
        CartItem item = new CartItem(1, "Odd Price Item", "Books", 1, 33.33, "ODD-1");
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 0.0, 7.5, 40.00);

        assertEquals(2.50, res.getTaxAmount(), 0.001);
        assertEquals(35.83, res.getNetTotal(), 0.001);
        assertEquals(4.17, res.getChangeDue(), 0.001);
    }
}
