package com.example.t.service;

import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService.CalculationResult;
import com.example.t.service.BillingService.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - BillingService Pure Math Calculation Engine Unit Tests")
public class BillingServiceTest {

    @Test
    @DisplayName("Single Item Calculation without Discount")
    public void testSingleItemCalculation() {
        OrderItem item = new OrderItem(1, "Clean Code", "Books", 2, 35.00, "BK-001");
        // Subtotal = 2 * 35.00 = 70.00
        // Discount = 0% -> 0.00
        // Tax = 8.25% of 70.00 = 5.775 -> 5.78
        // Net Total = 75.78
        // Cash = 100.00 -> Change = 24.22
        CalculationResult res = BillingService.calculate(
                Collections.singletonList(item), 0.0, 8.25, 100.00);

        assertEquals(70.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(70.00, res.getTaxableAmount(), 0.001);
        assertEquals(5.78, res.getTaxAmount(), 0.001);
        assertEquals(75.78, res.getNetTotal(), 0.001);
        assertEquals(24.22, res.getChangeDue(), 0.001);
        assertTrue(res.isCashSufficient());
    }

    @Test
    @DisplayName("Multi-Item Mixed Basket with 10% Discount and 5% Tax")
    public void testMultiItemMixedBasket() {
        OrderItem book = new OrderItem(1, "Modern Java in Action", "Books", 1, 45.00, "BK-002");
        OrderItem notebook = new OrderItem(2, "A5 Spiral Notebook", "Stationery", 3, 4.50, "ST-001");
        OrderItem pen = new OrderItem(3, "Gel Pen Pack", "Stationery", 2, 6.00, "ST-002");

        // Subtotal = 45.00 + (3 * 4.50 = 13.50) + (2 * 6.00 = 12.00) = 70.50
        // Discount 10% = 7.05
        // Taxable = 70.50 - 7.05 = 63.45
        // Tax 5% = 3.1725 -> 3.17
        // Net Total = 63.45 + 3.17 = 66.62
        // Cash = 70.00 -> Change = 3.38
        List<OrderItem> cart = Arrays.asList(book, notebook, pen);
        CalculationResult res = BillingService.calculate(cart, 10.0, 5.0, 70.00);

        assertEquals(70.50, res.getSubtotal(), 0.001);
        assertEquals(7.05, res.getDiscountAmount(), 0.001);
        assertEquals(63.45, res.getTaxableAmount(), 0.001);
        assertEquals(3.17, res.getTaxAmount(), 0.001);
        assertEquals(66.62, res.getNetTotal(), 0.001);
        assertEquals(3.38, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Regional Preset - UK 20% VAT Calculation")
    public void testUkRegionalPreset() {
        OrderItem item = new OrderItem(1, "Fountain Pen", "Stationery", 1, 50.00, "FP-1");
        // Subtotal = 50.00, 10% discount = 5.00, Taxable = 45.00
        // 20% VAT = 9.00, Net Total = 54.00
        CalculationResult res = BillingService.calculateForRegion(
                Collections.singletonList(item), 10.0, RegionalPreset.UK, 60.00);

        assertEquals(50.00, res.getSubtotal(), 0.001);
        assertEquals(5.00, res.getDiscountAmount(), 0.001);
        assertEquals(45.00, res.getTaxableAmount(), 0.001);
        assertEquals(9.00, res.getTaxAmount(), 0.001);
        assertEquals(54.00, res.getNetTotal(), 0.001);
        assertEquals(6.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Regional Preset - UAE 5% VAT Calculation")
    public void testUaeRegionalPreset() {
        OrderItem item = new OrderItem(1, "Calligraphy Set", "Stationery", 1, 100.00, "CS-1");
        CalculationResult res = BillingService.calculateForRegion(
                Collections.singletonList(item), 0.0, RegionalPreset.GULF_UAE, 110.00);

        assertEquals(100.00, res.getSubtotal(), 0.001);
        assertEquals(5.00, res.getTaxAmount(), 0.001);
        assertEquals(105.00, res.getNetTotal(), 0.001);
        assertEquals(5.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Regional Preset - Saudi Arabia 15% VAT Calculation")
    public void testSaudiRegionalPreset() {
        OrderItem item = new OrderItem(1, "Arabic Anthology", "Books", 2, 80.00, "AL-1");
        CalculationResult res = BillingService.calculateForRegion(
                Collections.singletonList(item), 0.0, RegionalPreset.GULF_SAUDI, 200.00);

        assertEquals(160.00, res.getSubtotal(), 0.001);
        assertEquals(24.00, res.getTaxAmount(), 0.001); // 15% of 160
        assertEquals(184.00, res.getNetTotal(), 0.001);
        assertEquals(16.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Regional Preset - Qatar 0% VAT Calculation")
    public void testQatarRegionalPreset() {
        OrderItem item = new OrderItem(1, "Textbook", "Books", 1, 75.00, "Q-TB");
        CalculationResult res = BillingService.calculateForRegion(
                Collections.singletonList(item), 0.0, RegionalPreset.GULF_QATAR, 100.00);

        assertEquals(75.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(75.00, res.getNetTotal(), 0.001);
        assertEquals(25.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Half-Up Rounding Precision Validation")
    public void testHalfUpRoundingPrecision() {
        // 10.555 -> 10.56
        assertEquals(10.56, BillingService.roundMoney(10.555), 0.0001);
        // 10.554 -> 10.55
        assertEquals(10.55, BillingService.roundMoney(10.554), 0.0001);
        // Tax rate 7.5% on 33.33 = 2.49975 -> 2.50
        OrderItem item = new OrderItem(1, "Odd Price Item", "Books", 1, 33.33, "ODD-1");
        CalculationResult res = BillingService.calculate(
                Collections.singletonList(item), 0.0, 7.5, 40.00);

        assertEquals(2.50, res.getTaxAmount(), 0.001);
        assertEquals(35.83, res.getNetTotal(), 0.001);
        assertEquals(4.17, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Exact Cash Tendered produces exactly zero change")
    public void testExactCashZeroChange() {
        OrderItem item = new OrderItem(1, "Notebook", "Stationery", 2, 10.00, "NB-1");
        CalculationResult res = BillingService.calculate(
                Collections.singletonList(item), 0.0, 0.0, 20.00);

        assertEquals(20.00, res.getNetTotal(), 0.001);
        assertEquals(0.00, res.getChangeDue(), 0.001);
        assertTrue(res.isCashSufficient());
    }

    @Test
    @DisplayName("Insufficient Cash Tendered sets isCashSufficient to false and negative changeDue")
    public void testInsufficientCash() {
        OrderItem item = new OrderItem(1, "Book", "Books", 1, 50.00, "BK-50");
        CalculationResult res = BillingService.calculate(
                Collections.singletonList(item), 0.0, 0.0, 40.00);

        assertEquals(50.00, res.getNetTotal(), 0.001);
        assertEquals(-10.00, res.getChangeDue(), 0.001);
        assertFalse(res.isCashSufficient());
    }

    @Test
    @DisplayName("Boundary Validation - Negative Discount or > 100% Throws Exception")
    public void testInvalidDiscountsThrow() {
        OrderItem item = new OrderItem(1, "Item", "General", 1, 10.00, "ITM");
        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(Collections.singletonList(item), -1.0, 5.0, 20.00));
        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(Collections.singletonList(item), 100.1, 5.0, 20.00));
    }

    @Test
    @DisplayName("Boundary Validation - Negative Tax Rate Throws Exception")
    public void testNegativeTaxRateThrows() {
        OrderItem item = new OrderItem(1, "Item", "General", 1, 10.00, "ITM");
        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(Collections.singletonList(item), 0.0, -5.0, 20.00));
    }

    @Test
    @DisplayName("Boundary Validation - Negative Cash Tendered Throws Exception")
    public void testNegativeCashThrows() {
        OrderItem item = new OrderItem(1, "Item", "General", 1, 10.00, "ITM");
        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(Collections.singletonList(item), 0.0, 5.0, -10.00));
    }

    @Test
    @DisplayName("Empty Items List produces Zero Financial Totals")
    public void testEmptyBasket() {
        CalculationResult res = BillingService.calculate(Collections.emptyList(), 0.0, 8.25, 10.00);
        assertEquals(0.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
        assertEquals(10.00, res.getChangeDue(), 0.001);
    }
}
