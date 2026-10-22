package com.example.t.service;

import com.example.t.model.InvoiceItem;
import com.example.t.model.PaymentTender;
import com.example.t.model.PaymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SplitTenderBillingTest {

    @Test
    @DisplayName("Single exact cash tender applies full amount with zero change")
    public void testExactCashTender() {
        PaymentTender cash = new PaymentTender(PaymentType.CASH, 50.00);
        BillingService.SplitTenderResult result = BillingService.calculateSplitTender(50.00, List.of(cash));

        assertEquals(50.00, result.getNetTotal(), 0.001);
        assertEquals(50.00, result.getTotalApplied(), 0.001);
        assertEquals(50.00, result.getTotalCashTendered(), 0.001);
        assertEquals(0.00, result.getTotalChangeDue(), 0.001);
        assertEquals(0.00, result.getRemainingBalance(), 0.001);
        assertTrue(result.isFullyPaid());
    }

    @Test
    @DisplayName("Cash over-tender calculates change correctly")
    public void testCashOverTenderWithChange() {
        // Customer owes $35.00, hands $50.00 bill
        PaymentTender cash = new PaymentTender(PaymentType.CASH, 50.00);
        BillingService.SplitTenderResult result = BillingService.calculateSplitTender(35.00, List.of(cash));

        assertEquals(35.00, result.getNetTotal(), 0.001);
        assertEquals(35.00, result.getTotalApplied(), 0.001);
        assertEquals(50.00, result.getTotalCashTendered(), 0.001);
        assertEquals(15.00, result.getTotalChangeDue(), 0.001);
        assertEquals(0.00, result.getRemainingBalance(), 0.001);
        assertTrue(result.isFullyPaid());

        PaymentTender processed = result.getTenders().get(0);
        assertEquals(35.00, processed.getAmount(), 0.001);
        assertEquals(50.00, processed.getTenderedAmount(), 0.001);
        assertEquals(15.00, processed.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Partial cash under-tender leaves positive remaining balance")
    public void testCashUnderTenderRemainingBalance() {
        // Customer owes $100.00, pays $40.00 cash
        PaymentTender cash = new PaymentTender(PaymentType.CASH, 40.00);
        BillingService.SplitTenderResult result = BillingService.calculateSplitTender(100.00, List.of(cash));

        assertEquals(100.00, result.getNetTotal(), 0.001);
        assertEquals(40.00, result.getTotalApplied(), 0.001);
        assertEquals(0.00, result.getTotalChangeDue(), 0.001);
        assertEquals(60.00, result.getRemainingBalance(), 0.001);
        assertFalse(result.isFullyPaid());
    }

    @Test
    @DisplayName("Card payment exceeding remaining balance throws IllegalArgumentException (no cashback)")
    public void testCardOverTenderRejected() {
        PaymentTender card = new PaymentTender(PaymentType.CARD, 60.00);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculateSplitTender(50.00, List.of(card)));
        assertTrue(ex.getMessage().contains("cannot exceed remaining balance"));
    }

    @Test
    @DisplayName("Store credit exceeding remaining balance throws IllegalArgumentException")
    public void testStoreCreditOverTenderRejected() {
        PaymentTender voucher = new PaymentTender(PaymentType.STORE_CREDIT, 100.00);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculateSplitTender(75.00, List.of(voucher)));
        assertTrue(ex.getMessage().contains("cannot exceed remaining balance"));
    }

    @Test
    @DisplayName("3-way split: Store Credit + Card + Cash over-tender completes order with accurate change")
    public void testThreeWaySplitTender() {
        // Net Total = $100.00
        // Tender 1: Store Credit voucher $20.00 (remaining becomes $80.00)
        // Tender 2: Card $50.00 (remaining becomes $30.00)
        // Tender 3: Cash $40.00 on $30.00 remaining (applied $30.00, change $10.00)
        PaymentTender t1 = new PaymentTender(PaymentType.STORE_CREDIT, 20.00, 20.00, 0.0, "VOUCHER-123");
        PaymentTender t2 = new PaymentTender(PaymentType.CARD, 50.00, 50.00, 0.0, "VISA-4242");
        PaymentTender t3 = new PaymentTender(PaymentType.CASH, 40.00, 40.00, 0.0, null);

        BillingService.SplitTenderResult result = BillingService.calculateSplitTender(100.00, List.of(t1, t2, t3));

        assertEquals(100.00, result.getNetTotal(), 0.001);
        assertEquals(100.00, result.getTotalApplied(), 0.001);
        assertEquals(40.00, result.getTotalCashTendered(), 0.001);
        assertEquals(10.00, result.getTotalChangeDue(), 0.001);
        assertEquals(0.00, result.getRemainingBalance(), 0.001);
        assertTrue(result.isFullyPaid());

        List<PaymentTender> processed = result.getTenders();
        assertEquals(3, processed.size());
        assertEquals(20.00, processed.get(0).getAmount(), 0.001);
        assertEquals(50.00, processed.get(1).getAmount(), 0.001);
        assertEquals(30.00, processed.get(2).getAmount(), 0.001);
        assertEquals(10.00, processed.get(2).getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("Line-item discount compounds with cart discount without double-counting")
    public void testLineItemDiscountCompoundingWithCartDiscount() {
        // Item 1: 1 x $100.00 with 20% line discount -> line total = $80.00 (disc = $20.00)
        // Item 2: 1 x $50.00 with 0% line discount  -> line total = $50.00
        // Net Subtotal before cart discount = $130.00
        // Cart Discount: 10% on $130.00 = $13.00
        // Taxable Amount = $117.00
        // Tax Rate: 10% -> Tax Amount = $11.70
        // Net Total = $128.70
        InvoiceItem ii1 = new InvoiceItem(1, "Book 1", "B1", 1, 100.00);
        ii1.setDiscountPercent(20.0);
        assertEquals(80.00, ii1.getLineTotal(), 0.001);
        assertEquals(20.00, ii1.getDiscountAmount(), 0.001);

        InvoiceItem ii2 = new InvoiceItem(2, "Book 2", "B2", 1, 50.00);
        assertEquals(50.00, ii2.getLineTotal(), 0.001);

        BillingService.OrderItem oi1 = BillingService.OrderItem.fromInvoiceItem(ii1);
        BillingService.OrderItem oi2 = BillingService.OrderItem.fromInvoiceItem(ii2);

        BillingService.CalculationResult calc = BillingService.calculate(
                List.of(oi1, oi2),
                10.0, // 10% cart discount
                10.0, // 10% tax rate
                150.00 // $150 cash tendered
        );

        assertEquals(130.00, calc.getSubtotal(), 0.001);
        assertEquals(13.00, calc.getDiscountAmount(), 0.001);
        assertEquals(117.00, calc.getTaxableAmount(), 0.001);
        assertEquals(11.70, calc.getTaxAmount(), 0.001);
        assertEquals(128.70, calc.getNetTotal(), 0.001);
        assertEquals(21.30, calc.getChangeDue(), 0.001); // 150.00 - 128.70
    }
}
