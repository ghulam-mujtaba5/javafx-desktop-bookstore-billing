package com.example.t.e2e.harness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative mathematical calculation oracle for Bookstore & Stationery POS Billing.
 * Implements the exact formulas defined in PROJECT.md:
 * - subtotal = sum(price * quantity)
 * - discountAmount = round(subtotal * (discountPercent / 100.0))
 * - taxableAmount = subtotal - discountAmount
 * - taxAmount = round(taxableAmount * (taxRate / 100.0))
 * - netTotal = taxableAmount + taxAmount
 * - changeDue = cashTendered - netTotal
 *
 * All currency values are rounded HALF_UP to 2 decimal places.
 */
public class CalculationOracle {

    public enum RegionalPreset {
        USA("USA", "USD", "$", "Sales Tax", 8.25, "EN"),
        UK("UK", "GBP", "£", "VAT", 20.00, "EN"),
        GULF_UAE("UAE", "AED", "AED", "VAT", 5.00, "BILINGUAL_AR_EN"),
        GULF_SAUDI("Saudi Arabia", "SAR", "SAR", "VAT", 15.00, "BILINGUAL_AR_EN"),
        GULF_QATAR("Qatar", "QAR", "QAR", "VAT", 0.00, "BILINGUAL_AR_EN");

        private final String countryName;
        private final String currencyCode;
        private final String currencySymbol;
        private final String taxName;
        private final double defaultTaxRate;
        private final String receiptLanguage;

        RegionalPreset(String countryName, String currencyCode, String currencySymbol,
                       String taxName, double defaultTaxRate, String receiptLanguage) {
            this.countryName = countryName;
            this.currencyCode = currencyCode;
            this.currencySymbol = currencySymbol;
            this.taxName = taxName;
            this.defaultTaxRate = defaultTaxRate;
            this.receiptLanguage = receiptLanguage;
        }

        public String getCountryName() { return countryName; }
        public String getCurrencyCode() { return currencyCode; }
        public String getCurrencySymbol() { return currencySymbol; }
        public String getTaxName() { return taxName; }
        public double getDefaultTaxRate() { return defaultTaxRate; }
        public String getReceiptLanguage() { return receiptLanguage; }
    }

    public static class CartItem {
        private final int productId;
        private final String name;
        private final String category;
        private final int quantity;
        private final double unitPrice;
        private final String sku;

        public CartItem(int productId, String name, String category, int quantity, double unitPrice, String sku) {
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
            }
            if (unitPrice < 0) {
                throw new IllegalArgumentException("Price cannot be negative: " + unitPrice);
            }
            this.productId = productId;
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.sku = sku;
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

    public static class OrderCalculationResult {
        private final double subtotal;
        private final double discountPercent;
        private final double discountAmount;
        private final double taxableAmount;
        private final double taxRate;
        private final double taxAmount;
        private final double netTotal;
        private final double cashTendered;
        private final double changeDue;
        private final List<CartItem> items;

        public OrderCalculationResult(double subtotal, double discountPercent, double discountAmount,
                                      double taxableAmount, double taxRate, double taxAmount,
                                      double netTotal, double cashTendered, double changeDue,
                                      List<CartItem> items) {
            this.subtotal = subtotal;
            this.discountPercent = discountPercent;
            this.discountAmount = discountAmount;
            this.taxableAmount = taxableAmount;
            this.taxRate = taxRate;
            this.taxAmount = taxAmount;
            this.netTotal = netTotal;
            this.cashTendered = cashTendered;
            this.changeDue = changeDue;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
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
        public List<CartItem> getItems() { return items; }
    }

    public static double roundMoney(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Computes the complete order financial breakdown according to the project specification.
     */
    public static OrderCalculationResult calculate(List<CartItem> items, double discountPercent,
                                                  double taxRate, double cashTendered) {
        if (discountPercent < 0.0 || discountPercent > 100.0) {
            throw new IllegalArgumentException("Discount percentage must be between 0.0 and 100.0, got: " + discountPercent);
        }
        if (taxRate < 0.0) {
            throw new IllegalArgumentException("Tax rate cannot be negative, got: " + taxRate);
        }

        double rawSubtotal = 0.0;
        if (items != null) {
            for (CartItem item : items) {
                rawSubtotal += item.getUnitPrice() * item.getQuantity();
            }
        }
        double subtotal = roundMoney(rawSubtotal);

        // discountAmount = subtotal * (discountPercent / 100.0)
        double discountAmount = roundMoney(subtotal * (discountPercent / 100.0));

        // taxableAmount = subtotal - discountAmount
        double taxableAmount = roundMoney(subtotal - discountAmount);

        // taxAmount = taxableAmount * (taxRate / 100.0)
        double taxAmount = roundMoney(taxableAmount * (taxRate / 100.0));

        // netTotal = taxableAmount + taxAmount
        double netTotal = roundMoney(taxableAmount + taxAmount);

        // changeDue = cashTendered - netTotal
        double changeDue = roundMoney(cashTendered - netTotal);

        return new OrderCalculationResult(subtotal, discountPercent, discountAmount,
                taxableAmount, taxRate, taxAmount, netTotal, cashTendered, changeDue,
                items == null ? Collections.emptyList() : items);
    }

    public static OrderCalculationResult calculateForRegion(List<CartItem> items, double discountPercent,
                                                           RegionalPreset preset, double cashTendered) {
        return calculate(items, discountPercent, preset.getDefaultTaxRate(), cashTendered);
    }
}
