package com.example.t.controller;

import com.example.t.NotificationUtil;
import com.example.t.PasswordUtil;
import com.example.t.dao.SettingsDao;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/**
 * Controller for Store Settings & Regional Configuration.
 * Features administrative password protection, multi-regional presets (USA, UK, Gulf),
 * store branding configuration, and password management.
 */
public class SettingsController {

    @FXML private Label lockStatusBadge;
    @FXML private StackPane containerStack;
    @FXML private VBox lockOverlayBox;
    @FXML private PasswordField adminUnlockPasswordField;
    @FXML private Button btnUnlockSettings;
    @FXML private Label unlockErrorLabel;

    @FXML private ScrollPane settingsScrollPane;
    @FXML private ComboBox<String> presetComboBox;
    @FXML private TextField currencyCodeField;
    @FXML private TextField currencySymbolField;
    @FXML private TextField taxNameField;
    @FXML private TextField taxRateField;
    @FXML private ComboBox<String> receiptLanguageComboBox;

    @FXML private TextField storeNameField;
    @FXML private TextField storeAddressField;
    @FXML private TextField storePhoneField;

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button btnChangePassword;

    @FXML private Button btnSaveSettings;
    @FXML private Button btnResetDefaults;
    @FXML private Button btnLockSettings;
    @FXML private Label saveStatusLabel;

    private SettingsDao settingsDao;
    private StoreSettings settings;
    private Runnable onSettingsSaved;
    private boolean isUnlocked = false;

    @FXML
    public void initialize() {
        if (settingsDao == null) {
            settingsDao = new SettingsDao();
        }

        setupDropdowns();
        loadSettingsFromDatabase();
        lockScreen();
    }

    public void setSettingsDao(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
        loadSettingsFromDatabase();
    }

    public void setOnSettingsSaved(Runnable onSettingsSaved) {
        this.onSettingsSaved = onSettingsSaved;
    }

    private void setupDropdowns() {
        if (presetComboBox != null) {
            presetComboBox.setItems(FXCollections.observableArrayList(
                    "USA (USD $, 8.25% Sales Tax)",
                    "UK (GBP £, 20.0% VAT)",
                    "UAE (AED د.إ, 5.0% VAT, Bilingual)",
                    "Saudi Arabia (SAR ر.س, 15.0% VAT, Bilingual)",
                    "Qatar (QAR ر.ق, 0.0% VAT, Bilingual)",
                    "Custom"
            ));
        }

        if (receiptLanguageComboBox != null) {
            receiptLanguageComboBox.setItems(FXCollections.observableArrayList(
                    "EN",
                    "BILINGUAL_AR_EN"
            ));
        }
    }

    public void loadSettingsFromDatabase() {
        try {
            if (settingsDao != null) {
                settings = settingsDao.getSettings();
            }
        } catch (SQLException ignored) {}

        if (settings == null) {
            settings = StoreSettings.defaultSettings();
        }

        populateFieldsFromModel();
    }

    private void populateFieldsFromModel() {
        if (settings == null) return;

        if (storeNameField != null) storeNameField.setText(settings.getStoreName());
        if (storeAddressField != null) storeAddressField.setText(settings.getStoreAddress());
        if (storePhoneField != null) storePhoneField.setText(settings.getStorePhone());

        if (currencyCodeField != null) currencyCodeField.setText(settings.getCurrencyCode());
        if (currencySymbolField != null) currencySymbolField.setText(settings.getCurrencySymbol());
        if (taxNameField != null) taxNameField.setText(settings.getTaxName());
        if (taxRateField != null) taxRateField.setText(String.format("%.2f", settings.getTaxRate()));

        if (receiptLanguageComboBox != null) {
            receiptLanguageComboBox.setValue(settings.getReceiptLanguage());
        }

        if (presetComboBox != null) {
            String region = settings.getRegionPreset();
            if ("USA".equalsIgnoreCase(region)) {
                presetComboBox.setValue("USA (USD $, 8.25% Sales Tax)");
            } else if ("UK".equalsIgnoreCase(region)) {
                presetComboBox.setValue("UK (GBP £, 20.0% VAT)");
            } else if ("GULF_UAE".equalsIgnoreCase(region) || "UAE".equalsIgnoreCase(region)) {
                presetComboBox.setValue("UAE (AED د.إ, 5.0% VAT, Bilingual)");
            } else if ("GULF_SAUDI".equalsIgnoreCase(region) || "SAUDI".equalsIgnoreCase(region) || "SAUDI ARABIA".equalsIgnoreCase(region)) {
                presetComboBox.setValue("Saudi Arabia (SAR ر.س, 15.0% VAT, Bilingual)");
            } else if ("GULF_QATAR".equalsIgnoreCase(region) || "QATAR".equalsIgnoreCase(region)) {
                presetComboBox.setValue("Qatar (QAR ر.ق, 0.0% VAT, Bilingual)");
            } else {
                presetComboBox.setValue("Custom");
            }
        }
    }

    /**
     * Unlocks the configuration screen if the entered password matches.
     */
    @FXML
    public void handleUnlock(ActionEvent event) {
        String entered = adminUnlockPasswordField != null ? adminUnlockPasswordField.getText() : "";
        if (entered == null || entered.isEmpty()) {
            if (unlockErrorLabel != null) unlockErrorLabel.setText("Please enter the admin password.");
            return;
        }

        String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
        boolean matches = false;

        if (storedHash != null && !storedHash.trim().isEmpty()) {
            matches = PasswordUtil.verifyPassword(entered, storedHash);
        }

        if (matches) {
            unlockScreen();
        } else {
            if (unlockErrorLabel != null) {
                unlockErrorLabel.setText("Incorrect password. Default is 'admin'.");
            }
            NotificationUtil.showError("Authentication Failed", "Incorrect administrator password.");
        }
    }

    public void unlockScreen() {
        isUnlocked = true;
        if (lockOverlayBox != null) lockOverlayBox.setVisible(false);
        if (settingsScrollPane != null) settingsScrollPane.setVisible(true);
        if (adminUnlockPasswordField != null) adminUnlockPasswordField.clear();
        if (unlockErrorLabel != null) unlockErrorLabel.setText("");
        if (lockStatusBadge != null) {
            lockStatusBadge.setText("🔓 Unlocked");
            lockStatusBadge.getStyleClass().setAll("status-badge");
        }
    }

    @FXML
    public void handleLock(ActionEvent event) {
        lockScreen();
    }

    public void lockScreen() {
        isUnlocked = false;
        if (lockOverlayBox != null) lockOverlayBox.setVisible(true);
        if (settingsScrollPane != null) settingsScrollPane.setVisible(false);
        if (lockStatusBadge != null) {
            lockStatusBadge.setText("🔒 Locked");
            lockStatusBadge.getStyleClass().setAll("badge-low-stock");
        }
        if (saveStatusLabel != null) saveStatusLabel.setText("");
    }

    /**
     * Updates regional settings fields when a preset is chosen.
     */
    @FXML
    public void handlePresetChange(ActionEvent event) {
        String selection = presetComboBox != null ? presetComboBox.getValue() : null;
        if (selection == null) return;

        if (selection.startsWith("USA")) {
            applyPresetToForm(RegionalPreset.USA, "USD", "$", "Sales Tax", 8.25, "EN");
        } else if (selection.startsWith("UK")) {
            applyPresetToForm(RegionalPreset.UK, "GBP", "£", "VAT", 20.00, "EN");
        } else if (selection.startsWith("UAE")) {
            applyPresetToForm(RegionalPreset.GULF_UAE, "AED", "د.إ", "VAT", 5.00, "BILINGUAL_AR_EN");
        } else if (selection.startsWith("Saudi")) {
            applyPresetToForm(RegionalPreset.GULF_SAUDI, "SAR", "ر.س", "VAT", 15.00, "BILINGUAL_AR_EN");
        } else if (selection.startsWith("Qatar")) {
            applyPresetToForm(RegionalPreset.GULF_QATAR, "QAR", "ر.ق", "VAT", 0.00, "BILINGUAL_AR_EN");
        }
    }

    private void applyPresetToForm(RegionalPreset preset, String code, String symbol,
                                   String taxName, double taxRate, String lang) {
        if (currencyCodeField != null) currencyCodeField.setText(code);
        if (currencySymbolField != null) currencySymbolField.setText(symbol);
        if (taxNameField != null) taxNameField.setText(taxName);
        if (taxRateField != null) taxRateField.setText(String.format("%.2f", taxRate));
        if (receiptLanguageComboBox != null) receiptLanguageComboBox.setValue(lang);
    }

    /**
     * Changes the administrator password and updates the database.
     */
    @FXML
    public void handleChangePassword(ActionEvent event) {
        String current = currentPasswordField != null ? currentPasswordField.getText() : "";
        String newPass = newPasswordField != null ? newPasswordField.getText() : "";
        String confirm = confirmPasswordField != null ? confirmPasswordField.getText() : "";

        if (newPass == null || newPass.trim().isEmpty()) {
            NotificationUtil.showWarning("Invalid Password", "New password cannot be empty.");
            return;
        }

        if (!newPass.equals(confirm)) {
            NotificationUtil.showWarning("Mismatch", "New password and confirmation password do not match.");
            return;
        }

        String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
        boolean currentValid = false;
        if (storedHash != null && !storedHash.trim().isEmpty()) {
            currentValid = PasswordUtil.verifyPassword(current, storedHash);
        }

        if (!currentValid) {
            NotificationUtil.showError("Incorrect Password", "The current password entered is incorrect.");
            return;
        }

        // Hash new password using PasswordUtil
        String newHash = PasswordUtil.hashPassword(newPass);
        settings.setAdminPasswordHash(newHash);

        try {
            if (settingsDao != null) {
                settingsDao.saveSettings(settings);
            }
            if (currentPasswordField != null) currentPasswordField.clear();
            if (newPasswordField != null) newPasswordField.clear();
            if (confirmPasswordField != null) confirmPasswordField.clear();
            NotificationUtil.showSuccess("Password Updated", "Admin password has been updated securely.");
        } catch (SQLException e) {
            NotificationUtil.showError("Save Error", "Failed to persist new password: " + e.getMessage());
        }
    }

    /**
     * Persists all configured store settings and regional presets to SQLite.
     */
    @FXML
    public void handleSaveSettings(ActionEvent event) {
        if (settings == null) {
            settings = new StoreSettings();
        }

        // Validate tax rate
        double taxRate = 0.0;
        try {
            if (taxRateField != null && !taxRateField.getText().trim().isEmpty()) {
                taxRate = Double.parseDouble(taxRateField.getText().trim());
                if (taxRate < 0.0) throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            NotificationUtil.showWarning("Validation Error", "Tax rate must be a non-negative number.");
            return;
        }

        // Extract regional preset name
        String presetSelection = presetComboBox != null ? presetComboBox.getValue() : "USA";
        String regionName = "USA";
        if (presetSelection != null) {
            if (presetSelection.startsWith("USA")) regionName = "USA";
            else if (presetSelection.startsWith("UK")) regionName = "UK";
            else if (presetSelection.startsWith("UAE")) regionName = "GULF_UAE";
            else if (presetSelection.startsWith("Saudi")) regionName = "GULF_SAUDI";
            else if (presetSelection.startsWith("Qatar")) regionName = "GULF_QATAR";
            else regionName = "CUSTOM";
        }

        settings.setRegionPreset(regionName);
        if (storeNameField != null) settings.setStoreName(storeNameField.getText().trim());
        if (storeAddressField != null) settings.setStoreAddress(storeAddressField.getText().trim());
        if (storePhoneField != null) settings.setStorePhone(storePhoneField.getText().trim());

        if (currencyCodeField != null) settings.setCurrencyCode(currencyCodeField.getText().trim().toUpperCase());
        if (currencySymbolField != null) settings.setCurrencySymbol(currencySymbolField.getText().trim());
        if (taxNameField != null) settings.setTaxName(taxNameField.getText().trim());
        settings.setTaxRate(taxRate);

        if (receiptLanguageComboBox != null && receiptLanguageComboBox.getValue() != null) {
            settings.setReceiptLanguage(receiptLanguageComboBox.getValue());
        }

        try {
            if (settingsDao != null) {
                settingsDao.saveSettings(settings);
            }
            if (saveStatusLabel != null) {
                saveStatusLabel.setText("Settings saved successfully (" + regionName + ")!");
            }
            NotificationUtil.showSuccess("Settings Saved", "Store settings and regional presets have been updated.");

            if (onSettingsSaved != null) {
                onSettingsSaved.run();
            }
        } catch (SQLException e) {
            NotificationUtil.showError("Save Error", "Failed to save settings: " + e.getMessage());
        }
    }

    @FXML
    public void handleResetDefaults(ActionEvent event) {
        settings = StoreSettings.defaultSettings();
        populateFieldsFromModel();
        NotificationUtil.showInfo("Reset", "Settings reset to defaults. Click 'Save' to apply.");
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public StoreSettings getSettings() {
        return settings;
    }
}
