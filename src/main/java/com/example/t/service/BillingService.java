package com.example.t.service;

import com.example.t.model.InvoiceItem;
import com.example.t.model.PaymentTender;
import com.example.t.model.PaymentType;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure mathematical calculation engine for POS billing, multi-regional tax/VAT,
 * discounts, and change computation.
 *
 * All financial values are rounded HALF_UP to 2 decimal places.
 */
public class BillingService {

    public static class OrderItem {
        private final int productId;
        private final String name;
        private final String category;
        private final int quantity;
        private final double unitPrice;
        private final String sku;
        private final double discountPercent;
        private final double discountAmount;

        public OrderItem(int productId, String name, String category, int quantity, double unitPrice, String sku, double discountPercent, double discountAmount) {
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
            }
            if (unitPrice < 0) {
                throw new IllegalArgumentException("Price cannot be negative: " + unitPrice);
            }
            if (discountPercent < 0.0 || discountPercent > 100.0) {
                throw new IllegalArgumentException("Discount percentage must be between 0.0 and 100.0: " + discountPercent);
            }
            this.productId = productId;
            this.name = name != null ? name : "";
            this.category = category != null ? category : "";
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.sku = sku != null ? sku : "";
            this.discountPercent = discountPercent;
            this.discountAmount = discountAmount > 0.0 ? discountAmount : roundMoney(roundMoney(unitPrice * quantity) * (discountPercent / 100.0));
        }

        public OrderItem(int productId, String name, String category, int quantity, double unitPrice, String sku) {
            this(productId, name, category, quantity, unitPrice, sku, 0.0, 0.0);
        }

        public static OrderItem fromInvoiceItem(InvoiceItem item) {
            return new OrderItem(item.getProductId(), item.getProductName(), "",
                    item.getQuantity(), item.getUnitPrice(), item.getSku(),
                    item.getDiscountPercent(), item.getDiscountAmount());
        }

        public int getProductId() { return productId; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public String getSku() { return sku; }
        public double getDiscountPercent() { return discountPercent; }
        public double getDiscountAmount() { return discountAmount; }

        public double getLineSubtotal() {
            double raw = roundMoney(unitPrice * quantity);
            return roundMoney(raw - discountAmount);
        }
    }

    public static class CalculationResult {
        private final double subtotal;
        private final double discountPercent;
        private final double discountAmount;
        private final double taxableAmount;
        private final double taxRate;
        private final double taxAmount;
        private final double netTotal;
        private final double cashTendered;
        private final double changeDue;
        private final List<OrderItem> items;

        public CalculationResult(double subtotal, double discountPercent, double discountAmount,
                                 double taxableAmount, double taxRate, double taxAmount,
                                 double netTotal, double cashTendered, double changeDue,
                                 List<OrderItem> items) {
            this.subtotal = subtotal;
            this.discountPercent = discountPercent;
            this.discountAmount = discountAmount;
            this.taxableAmount = taxableAmount;
            this.taxRate = taxRate;
            this.taxAmount = taxAmount;
            this.netTotal = netTotal;
            this.cashTendered = cashTendered;
            this.changeDue = changeDue;
            this.items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
        }

        public double getSubtotal() { return subtotal; }
        public double getDiscountPercent() { return discountPercent; }
        public double getDiscountAmount() { return discountAmount; }
        public double getTaxableAmount() { return taxableAmount; }
        public double getTaxRate() { return taxRate; }
        public double getTaxAmount() { return taxAmount; }
        public double getNetTotal() { return netTotal; }
        public double getCashTendered() { return cashTendered; }
        public double getChangeDue() { return changeDue; }
        public List<OrderItem> getItems() { return items; }
        public boolean isCashSufficient() { return cashTendered >= netTotal; }
    }

    /**
     * Rounds a monetary amount to 2 decimal places using HALF_UP rounding.
     */
    public static double roundMoney(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Computes the complete order financial breakdown.
     */
    public static CalculationResult calculate(List<OrderItem> items, double discountPercent,
                                              double taxRate, double cashTendered) {
        if (discountPercent < 0.0 || discountPercent > 100.0) {
            throw new IllegalArgumentException("Discount percentage must be between 0.0 and 100.0, got: " + discountPercent);
        }
        if (taxRate < 0.0) {
            throw new IllegalArgumentException("Tax rate cannot be negative, got: " + taxRate);
        }
        if (cashTendered < 0.0) {
            throw new IllegalArgumentException("Cash tendered cannot be negative: " + cashTendered);
        }

        double rawSubtotal = 0.0;
        if (items != null) {
            for (OrderItem item : items) {
                if (item.getDiscountAmount() > 0.0) {
                    rawSubtotal += item.getLineSubtotal();
                } else {
                    rawSubtotal += item.getUnitPrice() * item.getQuantity();
                }
            }
        }
        double subtotal = roundMoney(rawSubtotal);

        // discountAmount = round(subtotal * (discountPercent / 100.0))
        double discountAmount = roundMoney(subtotal * (discountPercent / 100.0));

        // taxableAmount = subtotal - discountAmount
        double taxableAmount = roundMoney(subtotal - discountAmount);

        // taxAmount = round(taxableAmount * (taxRate / 100.0))
        double taxAmount = roundMoney(taxableAmount * (taxRate / 100.0));

        // netTotal = taxableAmount + taxAmount
        double netTotal = roundMoney(taxableAmount + taxAmount);

        // changeDue = cashTendered - netTotal
        double changeDue = roundMoney(cashTendered - netTotal);

        return new CalculationResult(subtotal, discountPercent, discountAmount,
                taxableAmount, taxRate, taxAmount, netTotal, cashTendered, changeDue,
                items == null ? Collections.emptyList() : items);
    }

    public static CalculationResult calculateOrder(List<OrderItem> items, double discountPercent,
                                                   StoreSettings settings, double cashTendered) {
        double taxRate = settings != null ? settings.getTaxRate() : 0.0;
        return calculate(items, discountPercent, taxRate, cashTendered);
    }

    public static CalculationResult calculateForRegion(List<OrderItem> items, double discountPercent,
                                                       RegionalPreset preset, double cashTendered) {
        double taxRate = preset != null ? preset.getDefaultTaxRate() : 0.0;
        return calculate(items, discountPercent, taxRate, cashTendered);
    }

    public static CalculationResult calculateInvoiceItems(List<InvoiceItem> invoiceItems, double discountPercent,
                                                          StoreSettings settings, double cashTendered) {
        List<OrderItem> orderItems = new ArrayList<>();
        if (invoiceItems != null) {
            for (InvoiceItem ii : invoiceItems) {
                orderItems.add(OrderItem.fromInvoiceItem(ii));
            }
        }
        return calculateOrder(orderItems, discountPercent, settings, cashTendered);
    }

    public static class SplitTenderResult {
        private final double netTotal;
        private final double totalApplied;
        private final double totalCashTendered;
        private final double totalChangeDue;
        private final double remainingBalance;
        private final boolean fullyPaid;
        private final List<PaymentTender> tenders;

        public SplitTenderResult(double netTotal, double totalApplied, double totalCashTendered,
                                 double totalChangeDue, double remainingBalance, List<PaymentTender> tenders) {
            this.netTotal = netTotal;
            this.totalApplied = totalApplied;
            this.totalCashTendered = totalCashTendered;
            this.totalChangeDue = totalChangeDue;
            this.remainingBalance = remainingBalance;
            this.fullyPaid = remainingBalance <= 0.0001 && totalApplied >= netTotal - 0.0001;
            this.tenders = (tenders != null) ? Collections.unmodifiableList(new ArrayList<>(tenders)) : Collections.emptyList();
        }

        public SplitTenderResult(double netTotal, double totalApplied, double totalCashTendered,
                                 double totalChangeDue, double remainingBalance, boolean isFullyPaid) {
            this.netTotal = netTotal;
            this.totalApplied = totalApplied;
            this.totalCashTendered = totalCashTendered;
            this.totalChangeDue = totalChangeDue;
            this.remainingBalance = remainingBalance;
            this.fullyPaid = isFullyPaid;
            this.tenders = Collections.emptyList();
        }

        public double getNetTotal() { return netTotal; }
        public double getTotalApplied() { return totalApplied; }
        public double getTotalCashTendered() { return totalCashTendered; }
        public double getTotalTendered() { return totalCashTendered; }
        public double getTotalChangeDue() { return totalChangeDue; }
        public double getRemainingBalance() { return remainingBalance; }
        public boolean isFullyPaid() { return fullyPaid; }
        public List<PaymentTender> getTenders() { return tenders; }
    }

    /**
     * Computes the split tender payment breakdown across Cash, Card, and Store Credit tenders.
     * Cash tenders allow over-tendering with change due.
     * Card / Store Credit tenders are strictly bounded by the remaining balance (no cashback).
     */
    public static SplitTenderResult calculateSplitTender(double netTotal, List<PaymentTender> rawTenders) {
        if (netTotal < 0.0) {
            throw new IllegalArgumentException("Net total cannot be negative: " + netTotal);
        }
        double remaining = roundMoney(netTotal);
        double totalApplied = 0.0;
        double totalCashTendered = 0.0;
        double totalChangeDue = 0.0;
        List<PaymentTender> processed = new ArrayList<>();

        if (rawTenders != null) {
            for (PaymentTender t : rawTenders) {
                if (t == null) continue;
                double tendered = roundMoney(t.getTenderedAmount());
                if (tendered <= 0.0) {
                    throw new IllegalArgumentException("Tender amount must be greater than zero: " + tendered);
                }

                PaymentType type = t.getPaymentType() != null ? t.getPaymentType() : PaymentType.CASH;
                double applied;
                double change;

                if (type == PaymentType.CASH) {
                    totalCashTendered = roundMoney(totalCashTendered + tendered);
                    if (tendered >= remaining) {
                        applied = remaining;
                        change = roundMoney(tendered - remaining);
                        remaining = 0.0;
                    } else {
                        applied = tendered;
                        change = 0.0;
                        remaining = roundMoney(remaining - tendered);
                    }
                    totalChangeDue = roundMoney(totalChangeDue + change);
                } else {
                    // CARD or STORE_CREDIT: cannot exceed remaining balance
                    if (tendered > remaining + 0.0001) {
                        throw new IllegalArgumentException(String.format(
                                "%s tender (%.2f) cannot exceed remaining balance of %.2f. Cashback is not permitted on non-cash tenders.",
                                type.getDisplayName(), tendered, remaining));
                    }
                    applied = tendered;
                    change = 0.0;
                    remaining = roundMoney(remaining - applied);
                }

                totalApplied = roundMoney(totalApplied + applied);
                processed.add(new PaymentTender(type, applied, tendered, change, t.getReferenceInfo()));
            }
        }

        return new SplitTenderResult(roundMoney(netTotal), totalApplied, totalCashTendered,
                totalChangeDue, remaining, processed);
    }

    public static PaymentTender createTender(PaymentType type, double tenderedAmount, double currentRemaining, String ref) {
        if (tenderedAmount <= 0.0) {
            throw new IllegalArgumentException("Tender amount must be positive: " + tenderedAmount);
        }
        double roundedTendered = roundMoney(tenderedAmount);
        double roundedRemaining = roundMoney(currentRemaining);

        if (type == PaymentType.CASH) {
            double applied = roundMoney(Math.min(roundedTendered, roundedRemaining));
            double change = roundMoney(Math.max(0.0, roundedTendered - roundedRemaining));
            return new PaymentTender(PaymentType.CASH, applied, roundedTendered, change, ref);
        } else {
            if (roundedTendered > roundedRemaining + 0.0001) {
                throw new IllegalArgumentException(String.format(
                        "%s tender ($%.2f) exceeds remaining balance of $%.2f",
                        type.getDisplayName(), roundedTendered, roundedRemaining));
            }
            return new PaymentTender(type, roundedTendered, roundedTendered, 0.0, ref);
        }
    }
}
