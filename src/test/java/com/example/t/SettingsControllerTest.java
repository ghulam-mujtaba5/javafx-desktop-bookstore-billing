package com.example.t;

import com.example.t.controller.SettingsController;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4 - Multi-Regional Settings Controller Tests")
public class SettingsControllerTest {

    private static Path tempDir;
    private SettingsController controller;
    private SettingsDao settingsDao;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already initialized
        }
    }

    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory("m4_settings_test_");
        Path dbPath = tempDir.resolve("settings_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        settingsDao = new SettingsDao();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/Settings.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "Settings root node must load from FXML");
        controller = loader.getController();
        assertNotNull(controller);

        controller.setSettingsDao(settingsDao);
    }

    @Test
    @DisplayName("M4.SET.1: Settings screen starts in locked state")
    public void testInitialLockedState() {
        assertFalse(controller.isUnlocked(), "Settings must be locked on initial load");
    }

    @Test
    @DisplayName("M4.SET.2: Invalid password fails to unlock screen")
    public void testInvalidPasswordFailsUnlock() throws Exception {
        Field passProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        passProp.setAccessible(true);
        PasswordField passField = (PasswordField) passProp.get(controller);

        passField.setText("wrongpassword");
        controller.handleUnlock(new ActionEvent());

        assertFalse(controller.isUnlocked(), "Screen must remain locked when invalid password entered");
    }

    @Test
    @DisplayName("M4.SET.3: Correct password unlocks configuration screen")
    public void testCorrectPasswordUnlocksScreen() throws Exception {
        Field passProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        passProp.setAccessible(true);
        PasswordField passField = (PasswordField) passProp.get(controller);

        passField.setText("admin"); // Default password
        controller.handleUnlock(new ActionEvent());

        assertTrue(controller.isUnlocked(), "Screen must unlock when correct password entered");
    }

    @Test
    @DisplayName("M4.SET.4: Multi-regional preset switching updates fields correctly")
    public void testMultiRegionalPresetSwitching() throws Exception {
        controller.unlockScreen();

        Field presetProp = SettingsController.class.getDeclaredField("presetComboBox");
        presetProp.setAccessible(true);
        ComboBox<String> presetCombo = (ComboBox<String>) presetProp.get(controller);

        Field currCodeProp = SettingsController.class.getDeclaredField("currencyCodeField");
        currCodeProp.setAccessible(true);
        TextField currCode = (TextField) currCodeProp.get(controller);

        Field currSymProp = SettingsController.class.getDeclaredField("currencySymbolField");
        currSymProp.setAccessible(true);
        TextField currSym = (TextField) currSymProp.get(controller);

        Field taxRateProp = SettingsController.class.getDeclaredField("taxRateField");
        taxRateProp.setAccessible(true);
        TextField taxRate = (TextField) taxRateProp.get(controller);

        // Switch to UK
        presetCombo.setValue("UK (GBP £, 20.0% VAT)");
        controller.handlePresetChange(new ActionEvent());

        assertEquals("GBP", currCode.getText());
        assertEquals("£", currSym.getText());
        assertEquals("20.00", taxRate.getText());

        // Switch to UAE
        presetCombo.setValue("UAE (AED د.إ, 5.0% VAT, Bilingual)");
        controller.handlePresetChange(new ActionEvent());

        assertEquals("AED", currCode.getText());
        assertEquals("د.إ", currSym.getText());
        assertEquals("5.00", taxRate.getText());

        // Switch to Saudi Arabia
        presetCombo.setValue("Saudi Arabia (SAR ر.س, 15.0% VAT, Bilingual)");
        controller.handlePresetChange(new ActionEvent());

        assertEquals("SAR", currCode.getText());
        assertEquals("ر.س", currSym.getText());
        assertEquals("15.00", taxRate.getText());
    }

    @Test
    @DisplayName("M4.SET.5: Saving configuration updates SQLite SettingsDao and triggers callback")
    public void testSaveConfigurationPersistsToDatabase() throws Exception {
        controller.unlockScreen();

        AtomicBoolean callbackFired = new AtomicBoolean(false);
        controller.setOnSettingsSaved(() -> callbackFired.set(true));

        Field storeNameProp = SettingsController.class.getDeclaredField("storeNameField");
        storeNameProp.setAccessible(true);
        TextField storeName = (TextField) storeNameProp.get(controller);
        storeName.setText("Babylon Books & Stationery");

        Field presetProp = SettingsController.class.getDeclaredField("presetComboBox");
        presetProp.setAccessible(true);
        ComboBox<String> presetCombo = (ComboBox<String>) presetProp.get(controller);
        presetCombo.setValue("UK (GBP £, 20.0% VAT)");
        controller.handlePresetChange(new ActionEvent());

        controller.handleSaveSettings(new ActionEvent());

        assertTrue(callbackFired.get(), "onSettingsSaved callback must fire on save");

        StoreSettings fromDb = settingsDao.getSettings();
        assertEquals("Babylon Books & Stationery", fromDb.getStoreName());
        assertEquals("UK", fromDb.getRegionPreset());
        assertEquals("GBP", fromDb.getCurrencyCode());
        assertEquals(20.0, fromDb.getTaxRate(), 0.001);
    }

    @Test
    @DisplayName("M4.SET.6: Updating admin password hashes and saves new credential")
    public void testUpdateAdminPassword() throws Exception {
        controller.unlockScreen();

        Field currProp = SettingsController.class.getDeclaredField("currentPasswordField");
        currProp.setAccessible(true);
        PasswordField curr = (PasswordField) currProp.get(controller);

        Field newProp = SettingsController.class.getDeclaredField("newPasswordField");
        newProp.setAccessible(true);
        PasswordField newPass = (PasswordField) newProp.get(controller);

        Field confProp = SettingsController.class.getDeclaredField("confirmPasswordField");
        confProp.setAccessible(true);
        PasswordField conf = (PasswordField) confProp.get(controller);

        curr.setText("admin");
        newPass.setText("SecurePass2026!");
        conf.setText("SecurePass2026!");

        controller.handleChangePassword(new ActionEvent());

        // Verify password in DB
        StoreSettings updated = settingsDao.getSettings();
        assertTrue(PasswordUtil.verifyPassword("SecurePass2026!", updated.getAdminPasswordHash()));
    }

    @Test
    @DisplayName("M4.SET.7: Adversarial test - old password 'admin' fails to unlock screen after password is changed")
    public void testOldAdminPasswordFailsAfterPasswordChange() throws Exception {
        controller.unlockScreen();

        Field currProp = SettingsController.class.getDeclaredField("currentPasswordField");
        currProp.setAccessible(true);
        PasswordField curr = (PasswordField) currProp.get(controller);

        Field newProp = SettingsController.class.getDeclaredField("newPasswordField");
        newProp.setAccessible(true);
        PasswordField newPass = (PasswordField) newProp.get(controller);

        Field confProp = SettingsController.class.getDeclaredField("confirmPasswordField");
        confProp.setAccessible(true);
        PasswordField conf = (PasswordField) confProp.get(controller);

        curr.setText("admin");
        newPass.setText("SuperSecret#9876");
        conf.setText("SuperSecret#9876");
        controller.handleChangePassword(new ActionEvent());

        // Lock screen
        controller.lockScreen();
        assertFalse(controller.isUnlocked());

        Field unlockPassProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        unlockPassProp.setAccessible(true);
        PasswordField unlockPass = (PasswordField) unlockPassProp.get(controller);

        // Attempting unlock with old default password 'admin' must fail
        unlockPass.setText("admin");
        controller.handleUnlock(new ActionEvent());
        assertFalse(controller.isUnlocked(), "Old password 'admin' must NOT unlock screen after password change");

        // Attempting unlock with 'book shop' legacy string must fail
        unlockPass.setText("book shop");
        controller.handleUnlock(new ActionEvent());
        assertFalse(controller.isUnlocked(), "Legacy string 'book shop' must NOT unlock screen");

        // Unlocking with new password must succeed
        unlockPass.setText("SuperSecret#9876");
        controller.handleUnlock(new ActionEvent());
        assertTrue(controller.isUnlocked(), "New password must unlock screen");
    }
}
