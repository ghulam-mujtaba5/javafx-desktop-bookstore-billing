package com.example.t.model;

import com.example.t.PasswordUtil;
import javafx.beans.property.*;

/**
 * Store settings singleton configuration model.
 */
public class StoreSettings {

    private final IntegerProperty settingId;
    private final StringProperty storeName;
    private final StringProperty storeAddress;
    private final StringProperty storePhone;
    private final StringProperty regionPreset;
    private final StringProperty currencyCode;
    private final StringProperty currencySymbol;
    private final StringProperty taxName;
    private final DoubleProperty taxRate;
    private final StringProperty receiptLanguage;
    private final StringProperty adminPasswordHash;
    private final StringProperty uiTheme;

    public StoreSettings() {
        this(1, "GM Bookstore & Stationery", "123 Main Street", "0300 0000000",
                "USA", "USD", "$", "Sales Tax", 8.25, "EN",
                PasswordUtil.hashPassword("admin"), "LIGHT"); // genuine salted hash for 'admin'
    }

    public StoreSettings(int settingId, String storeName, String storeAddress, String storePhone,
                         String regionPreset, String currencyCode, String currencySymbol,
                         String taxName, double taxRate, String receiptLanguage, String adminPasswordHash) {
        this(settingId, storeName, storeAddress, storePhone, regionPreset, currencyCode, currencySymbol,
                taxName, taxRate, receiptLanguage, adminPasswordHash, "LIGHT");
    }

    public StoreSettings(int settingId, String storeName, String storeAddress, String storePhone,
                         String regionPreset, String currencyCode, String currencySymbol,
                         String taxName, double taxRate, String receiptLanguage, String adminPasswordHash,
                         String uiTheme) {
        this.settingId = new SimpleIntegerProperty(settingId);
        this.storeName = new SimpleStringProperty(storeName != null ? storeName : "My Bookstore");
        this.storeAddress = new SimpleStringProperty(storeAddress != null ? storeAddress : "");
        this.storePhone = new SimpleStringProperty(storePhone != null ? storePhone : "");
        this.regionPreset = new SimpleStringProperty(regionPreset != null ? regionPreset : "USA");
        this.currencyCode = new SimpleStringProperty(currencyCode != null ? currencyCode : "USD");
        this.currencySymbol = new SimpleStringProperty(currencySymbol != null ? currencySymbol : "$");
        this.taxName = new SimpleStringProperty(taxName != null ? taxName : "Sales Tax");
        this.taxRate = new SimpleDoubleProperty(taxRate);
        this.receiptLanguage = new SimpleStringProperty(receiptLanguage != null ? receiptLanguage : "EN");
        this.adminPasswordHash = new SimpleStringProperty(adminPasswordHash != null ? adminPasswordHash : "");
        this.uiTheme = new SimpleStringProperty(uiTheme != null ? uiTheme : "LIGHT");
    }

    public static StoreSettings defaultSettings() {
        return new StoreSettings();
    }

    public void applyPreset(RegionalPreset preset) {
        if (preset == null) return;
        setRegionPreset(preset.name());
        setCurrencyCode(preset.getCurrencyCode());
        setCurrencySymbol(preset.getCurrencySymbol());
        setTaxName(preset.getTaxName());
        setTaxRate(preset.getDefaultTaxRate());
        setReceiptLanguage(preset.getReceiptLanguage());
    }

    public int getSettingId() {
        return settingId.get();
    }

    public IntegerProperty settingIdProperty() {
        return settingId;
    }

    public void setSettingId(int settingId) {
        this.settingId.set(settingId);
    }

    public String getStoreName() {
        return storeName.get();
    }

    public StringProperty storeNameProperty() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName.set(storeName);
    }

    public String getStoreAddress() {
        return storeAddress.get();
    }

    public StringProperty storeAddressProperty() {
        return storeAddress;
    }

    public void setStoreAddress(String storeAddress) {
        this.storeAddress.set(storeAddress);
    }

    public String getStorePhone() {
        return storePhone.get();
    }

    public StringProperty storePhoneProperty() {
        return storePhone;
    }

    public void setStorePhone(String storePhone) {
        this.storePhone.set(storePhone);
    }

    public String getRegionPreset() {
        return regionPreset.get();
    }

    public StringProperty regionPresetProperty() {
        return regionPreset;
    }

    public void setRegionPreset(String regionPreset) {
        this.regionPreset.set(regionPreset);
    }

    public String getCurrencyCode() {
        return currencyCode.get();
    }

    public StringProperty currencyCodeProperty() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode.set(currencyCode);
    }

    public String getCurrencySymbol() {
        return currencySymbol.get();
    }

    public StringProperty currencySymbolProperty() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol.set(currencySymbol);
    }

    public String getTaxName() {
        return taxName.get();
    }

    public StringProperty taxNameProperty() {
        return taxName;
    }

    public void setTaxName(String taxName) {
        this.taxName.set(taxName);
    }

    public double getTaxRate() {
        return taxRate.get();
    }

    public DoubleProperty taxRateProperty() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate.set(taxRate);
    }

    public String getReceiptLanguage() {
        return receiptLanguage.get();
    }

    public StringProperty receiptLanguageProperty() {
        return receiptLanguage;
    }

    public void setReceiptLanguage(String receiptLanguage) {
        this.receiptLanguage.set(receiptLanguage);
    }

    public String getAdminPasswordHash() {
        return adminPasswordHash.get();
    }

    public StringProperty adminPasswordHashProperty() {
        return adminPasswordHash;
    }

    public void setAdminPasswordHash(String adminPasswordHash) {
        this.adminPasswordHash.set(adminPasswordHash);
    }

    public String getUiTheme() {
        return uiTheme.get();
    }

    public StringProperty uiThemeProperty() {
        return uiTheme;
    }

    public void setUiTheme(String uiTheme) {
        this.uiTheme.set(uiTheme != null ? uiTheme : "LIGHT");
    }

    @Override
    public String toString() {
        return "StoreSettings{" +
                "name='" + getStoreName() + '\'' +
                ", region='" + getRegionPreset() + '\'' +
                ", currency='" + getCurrencyCode() + '\'' +
                ", taxRate=" + getTaxRate() +
                ", uiTheme='" + getUiTheme() + '\'' +
                '}';
    }
}
