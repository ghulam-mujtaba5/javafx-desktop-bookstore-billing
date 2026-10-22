package com.example.t.model;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * InvoiceItem model representing an individual line item within a sales invoice.
 */
public class InvoiceItem {

    private final IntegerProperty itemId;
    private final IntegerProperty invoiceId;
    private final IntegerProperty productId;
    private final StringProperty productName;
    private final StringProperty sku;
    private final IntegerProperty quantity;
    private final DoubleProperty unitPrice;
    private final DoubleProperty lineTotal;
    private final DoubleProperty discountPercent;
    private final DoubleProperty discountAmount;
    private final StringProperty notes;

    public InvoiceItem() {
        this(0, 0, 0, "", "", 1, 0.0, 0.0, 0.0, 0.0, "");
    }

    public InvoiceItem(int itemId, int invoiceId, int productId, String productName,
                       String sku, int quantity, double unitPrice, double lineTotal) {
        this(itemId, invoiceId, productId, productName, sku, quantity, unitPrice, lineTotal, 0.0, 0.0, "");
    }

    public InvoiceItem(int productId, String productName, String sku, int quantity, double unitPrice) {
        this(0, 0, productId, productName, sku, quantity, unitPrice, roundMoney(quantity * unitPrice), 0.0, 0.0, "");
    }

    public InvoiceItem(int itemId, int invoiceId, int productId, String productName,
                       String sku, int quantity, double unitPrice, double lineTotal,
                       double discountPercent, double discountAmount, String notes) {
        this.itemId = new SimpleIntegerProperty(itemId);
        this.invoiceId = new SimpleIntegerProperty(invoiceId);
        this.productId = new SimpleIntegerProperty(productId);
        this.productName = new SimpleStringProperty(productName != null ? productName : "");
        this.sku = new SimpleStringProperty(sku != null ? sku : "");
        this.quantity = new SimpleIntegerProperty(quantity);
        this.unitPrice = new SimpleDoubleProperty(unitPrice);
        this.discountPercent = new SimpleDoubleProperty(discountPercent);
        this.discountAmount = new SimpleDoubleProperty(discountAmount);
        double calculatedTotal;
        if (lineTotal > 0) {
            calculatedTotal = lineTotal;
        } else {
            double raw = roundMoney(quantity * unitPrice);
            double disc = roundMoney(raw * (discountPercent / 100.0));
            calculatedTotal = roundMoney(raw - disc);
        }
        this.lineTotal = new SimpleDoubleProperty(calculatedTotal);
        this.notes = new SimpleStringProperty(notes != null ? notes : "");
    }

    public InvoiceItem(InvoiceItem other) {
        this(other.getItemId(), other.getInvoiceId(), other.getProductId(),
                other.getProductName(), other.getSku(), other.getQuantity(),
                other.getUnitPrice(), other.getLineTotal(),
                other.getDiscountPercent(), other.getDiscountAmount(), other.getNotes());
    }

    public int getItemId() {
        return itemId.get();
    }

    public IntegerProperty itemIdProperty() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId.set(itemId);
    }

    public int getInvoiceId() {
        return invoiceId.get();
    }

    public IntegerProperty invoiceIdProperty() {
        return invoiceId;
    }

    public void setInvoiceId(int invoiceId) {
        this.invoiceId.set(invoiceId);
    }

    public int getProductId() {
        return productId.get();
    }

    public IntegerProperty productIdProperty() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId.set(productId);
    }

    public String getProductName() {
        return productName.get();
    }

    public StringProperty productNameProperty() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName.set(productName != null ? productName : "");
    }

    public String getSku() {
        return sku.get();
    }

    public StringProperty skuProperty() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku.set(sku != null ? sku : "");
    }

    public int getQuantity() {
        return quantity.get();
    }

    public IntegerProperty quantityProperty() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity.set(quantity);
        recalculateLineTotal();
    }

    public double getUnitPrice() {
        return unitPrice.get();
    }

    public DoubleProperty unitPriceProperty() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice.set(unitPrice);
        recalculateLineTotal();
    }

    public double getLineTotal() {
        return lineTotal.get();
    }

    public DoubleProperty lineTotalProperty() {
        return lineTotal;
    }

    public void setLineTotal(double lineTotal) {
        this.lineTotal.set(lineTotal);
    }

    public double getDiscountPercent() {
        return discountPercent.get();
    }

    public DoubleProperty discountPercentProperty() {
        return discountPercent;
    }

    public void setDiscountPercent(double discountPercent) {
        this.discountPercent.set(discountPercent);
        recalculateLineTotal();
    }

    public double getDiscountAmount() {
        return discountAmount.get();
    }

    public DoubleProperty discountAmountProperty() {
        return discountAmount;
    }

    public void setDiscountAmount(double discountAmount) {
        this.discountAmount.set(discountAmount);
    }

    public String getNotes() {
        return notes.get();
    }

    public StringProperty notesProperty() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes.set(notes != null ? notes : "");
    }

    public void recalculateLineTotal() {
        double raw = roundMoney(getQuantity() * getUnitPrice());
        double disc = roundMoney(raw * (getDiscountPercent() / 100.0));
        setDiscountAmount(disc);
        setLineTotal(roundMoney(raw - disc));
    }

    private static double roundMoney(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public String toString() {
        return getProductName() + " (x" + getQuantity() + " @ " + getUnitPrice() + " = " + getLineTotal() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceItem that = (InvoiceItem) o;
        return getItemId() == that.getItemId() &&
                getProductId() == that.getProductId() &&
                Objects.equals(getProductName(), that.getProductName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getItemId(), getProductId(), getProductName());
    }
}
