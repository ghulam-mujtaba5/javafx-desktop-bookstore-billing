package com.example.t.model;

/**
 * Regional presets defining localization, currency, tax rates, and receipt formatting.
 */
public enum RegionalPreset {
    USA("USA", "USD", "$", "Sales Tax", 8.25, "EN"),
    UK("UK", "GBP", "£", "VAT", 20.00, "EN"),
    GULF_UAE("UAE", "AED", "د.إ", "VAT", 5.00, "BILINGUAL_AR_EN"),
    GULF_SAUDI("Saudi Arabia", "SAR", "SAR", "VAT", 15.00, "BILINGUAL_AR_EN"),
    GULF_QATAR("Qatar", "QAR", "QAR", "VAT", 0.00, "BILINGUAL_AR_EN"),
    CUSTOM("Custom", "USD", "$", "Tax", 0.00, "EN");

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

    public String getCountryName() {
        return countryName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public String getTaxName() {
        return taxName;
    }

    public double getDefaultTaxRate() {
        return defaultTaxRate;
    }

    public String getReceiptLanguage() {
        return receiptLanguage;
    }

    public boolean isBilingualArabic() {
        return "BILINGUAL_AR_EN".equalsIgnoreCase(receiptLanguage);
    }

    /**
     * Finds matching preset from region string, with fallback to USA.
     */
    public static RegionalPreset fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return USA;
        }
        String clean = text.trim().toUpperCase();
        for (RegionalPreset preset : values()) {
            if (preset.name().equals(clean) || preset.getCountryName().equalsIgnoreCase(clean)) {
                return preset;
            }
        }
        if (clean.equals("US") || clean.equals("UNITED STATES")) return USA;
        if (clean.equals("UNITED KINGDOM") || clean.equals("GB")) return UK;
        if (clean.contains("UAE") || clean.contains("EMIRATES") || clean.equals("AED")) return GULF_UAE;
        if (clean.contains("SAUDI") || clean.contains("KSA") || clean.equals("SAR")) return GULF_SAUDI;
        if (clean.contains("QATAR") || clean.equals("QAR")) return GULF_QATAR;

        return CUSTOM;
    }
}
