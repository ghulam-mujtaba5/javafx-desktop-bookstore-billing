package com.example.t.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain model representing a held/parked shopping cart in POS.
 * Ensures complete snapshot isolation by deep-copying line items.
 */
public class ParkedCart {

    private final String cartId;
    private String displayName;
    private String customerName;
    private String customerPhone;
    private final List<InvoiceItem> items;
    private double discountPercent;
    private String notes;
    private final LocalDateTime parkedAt;
    private boolean giftReceipt;

    public ParkedCart(String customerName, String customerPhone, List<InvoiceItem> items,
                      double discountPercent, String notes, boolean giftReceipt) {
        this(UUID.randomUUID().toString(), null, customerName, customerPhone,
                items, discountPercent, notes, LocalDateTime.now(), giftReceipt);
    }

    public ParkedCart(String cartId, String customerName, List<InvoiceItem> items, double discountPercent) {
        this(cartId, null, customerName, "", items, discountPercent, "", LocalDateTime.now(), false);
    }

    public ParkedCart(String cartId, String displayName, String customerName, String customerPhone,
                      List<InvoiceItem> items, double discountPercent, String notes,
                      LocalDateTime parkedAt, boolean giftReceipt) {
        this.cartId = (cartId != null && !cartId.trim().isEmpty()) ? cartId : UUID.randomUUID().toString();
        this.customerName = (customerName != null && !customerName.trim().isEmpty()) ? customerName : "Walk-in Customer";
        this.customerPhone = customerPhone != null ? customerPhone : "";
        this.items = new ArrayList<>();
        if (items != null) {
            for (InvoiceItem item : items) {
                if (item != null) {
                    this.items.add(copyItem(item));
                }
            }
        }
        this.discountPercent = discountPercent;
        this.notes = notes != null ? notes : "";
        this.parkedAt = parkedAt != null ? parkedAt : LocalDateTime.now();
        this.giftReceipt = giftReceipt;

        if (displayName != null && !displayName.trim().isEmpty()) {
            this.displayName = displayName;
        } else {
            this.displayName = buildDefaultDisplayName();
        }
    }

    public static InvoiceItem copyItem(InvoiceItem src) {
        if (src == null) return null;
        return new InvoiceItem(
                src.getItemId(),
                src.getInvoiceId(),
                src.getProductId(),
                src.getProductName(),
                src.getSku(),
                src.getQuantity(),
                src.getUnitPrice(),
                src.getLineTotal(),
                src.getDiscountPercent(),
                src.getDiscountAmount(),
                src.getNotes()
        );
    }

    private String buildDefaultDisplayName() {
        return String.format("%s (%d items • $%.2f • %s)",
                customerName,
                getTotalQuantity(),
                getEstimatedSubtotal(),
                getFormattedTime());
    }

    public int getTotalQuantity() {
        int total = 0;
        for (InvoiceItem item : items) {
            total += item.getQuantity();
        }
        return total;
    }

    public double getEstimatedSubtotal() {
        double subtotal = 0.0;
        for (InvoiceItem item : items) {
            subtotal += item.getLineTotal();
        }
        return subtotal;
    }

    public String getFormattedTime() {
        return parkedAt.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }

    public String getCartId() {
        return cartId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public List<InvoiceItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<InvoiceItem> createDeepCopyItems() {
        List<InvoiceItem> copy = new ArrayList<>();
        for (InvoiceItem item : items) {
            copy.add(copyItem(item));
        }
        return copy;
    }

    public List<InvoiceItem> restoreItemsSnapshot() {
        return createDeepCopyItems();
    }

    public double getCartDiscountPercent() {
        return discountPercent;
    }

    public int getItemCount() {
        return getTotalQuantity();
    }

    public String getParkedTimeFormatted() {
        return getFormattedTime();
    }

    public double getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(double discountPercent) {
        this.discountPercent = discountPercent;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getParkedAt() {
        return parkedAt;
    }

    public boolean isGiftReceipt() {
        return giftReceipt;
    }

    public void setGiftReceipt(boolean giftReceipt) {
        this.giftReceipt = giftReceipt;
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : buildDefaultDisplayName();
    }
}
