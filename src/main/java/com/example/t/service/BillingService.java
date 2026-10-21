package com.example.t.service;

import com.example.t.model.InvoiceItem;
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

        public OrderItem(int productId, String name, String category, int quantity, double unitPrice, String sku) {
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
            }
            if (unitPrice < 0) {
                throw new IllegalArgumentException("Price cannot be negative: " + unitPrice);
            }
            this.productId = productId;
            this.name = name != null ? name : "";
            this.category = category != null ? category : "";
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.sku = sku != null ? sku : "";
        }

        public static OrderItem fromInvoiceItem(InvoiceItem item) {
            return new OrderItem(item.getProductId(), item.getProductName(), "",
                    item.getQuantity(), item.getUnitPrice(), item.getSku());
        }

        public int getProductId() { return productId; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public String getSku() { return sku; }

        public double getLineSubtotal() {
            return roundMoney(unitPrice * quantity);
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
                rawSubtotal += item.getUnitPrice() * item.getQuantity();
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
}
