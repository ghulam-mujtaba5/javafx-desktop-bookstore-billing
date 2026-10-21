package com.example.t.model;

/**
 * Supported payment tender types for bookstore billing.
 */
public enum PaymentType {
    CASH("Cash"),
    CARD("Card / Electronic"),
    STORE_CREDIT("Store Credit / Voucher");

    private final String displayName;

    PaymentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PaymentType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return CASH;
        }
        String clean = text.trim().toUpperCase();
        for (PaymentType pt : values()) {
            if (pt.name().equals(clean)) {
                return pt;
            }
        }
        if (clean.contains("CREDIT") && clean.contains("STORE")) {
            return STORE_CREDIT;
        }
        if (clean.contains("CARD") || clean.contains("DEBIT") || clean.contains("CREDIT")) {
            return CARD;
        }
        return CASH;
    }
}
