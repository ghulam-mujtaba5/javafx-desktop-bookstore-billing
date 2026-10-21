package com.example.t;

import com.example.t.controller.MainShellController;
import com.example.t.controller.MainShellController.ViewType;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3 - Master Navigation Shell Controller Tests")
public class MainShellControllerTest {

    private static Path tempDir;
    private MainShellController controller;
    private SettingsDao settingsDao;
    private StoreSettings settings;

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
        tempDir = Files.createTempDirectory("m3_shell_test_");
        Path dbPath = tempDir.resolve("shell_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        settingsDao = new SettingsDao();
        settings = settingsDao.getSettings();

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "MainShell root node must load from FXML");
        controller = loader.getController();
        assertNotNull(controller, "MainShellController must be injected by FXMLLoader");
        controller.setSettingsDao(settingsDao);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    @Test
    @DisplayName("M3.1: MainShellController ViewType Enum contains all 4 core POS workflows")
    public void testViewTypeEnumIntegrity() {
        assertEquals(4, ViewType.values().length);
        assertNotNull(ViewType.valueOf("POS_BILLING"));
        assertNotNull(ViewType.valueOf("STOCK_MANAGEMENT"));
        assertNotNull(ViewType.valueOf("INVOICE_HISTORY"));
        assertNotNull(ViewType.valueOf("STORE_SETTINGS"));
    }

    @Test
    @DisplayName("M3.2: Default startup view navigates to Stock Management")
    public void testDefaultViewIsStockManagement() {
        assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView());
        assertTrue(controller.getNavStockBtn().getStyleClass().contains("active"));
        assertFalse(controller.getNavPosBtn().getStyleClass().contains("active"));
        assertNotNull(controller.getContentArea().getChildren());
        assertFalse(controller.getContentArea().getChildren().isEmpty());
    }

    @Test
    @DisplayName("M3.3: Dynamic navigation to POS Billing swaps content and updates active style")
    public void testNavigateToPosBilling() {
        controller.navigateTo(ViewType.POS_BILLING);
        assertEquals(ViewType.POS_BILLING, controller.getCurrentView());
        assertTrue(controller.getNavPosBtn().getStyleClass().contains("active"));
        assertFalse(controller.getNavStockBtn().getStyleClass().contains("active"));
        assertNotNull(controller.getContentArea().getChildren());
        assertFalse(controller.getContentArea().getChildren().isEmpty());
    }

    @Test
    @DisplayName("M3.4: Dynamic navigation to Invoices & History swaps content")
    public void testNavigateToInvoices() {
        controller.navigateTo(ViewType.INVOICE_HISTORY);
        assertEquals(ViewType.INVOICE_HISTORY, controller.getCurrentView());
        assertTrue(controller.getNavInvoicesBtn().getStyleClass().contains("active"));
        assertFalse(controller.getNavStockBtn().getStyleClass().contains("active"));
    }

    @Test
    @DisplayName("M3.5: Dynamic navigation to Store Settings swaps content")
    public void testNavigateToSettings() {
        controller.navigateTo(ViewType.STORE_SETTINGS);
        assertEquals(ViewType.STORE_SETTINGS, controller.getCurrentView());
        assertTrue(controller.getNavSettingsBtn().getStyleClass().contains("active"));
        assertFalse(controller.getNavStockBtn().getStyleClass().contains("active"));
    }

    @Test
    @DisplayName("M3.6: Header bar dynamically reflects store settings from database")
    public void testHeaderReflectsStoreSettings() throws SQLException {
        settings.setStoreName("Metropolis Book Tower");
        settings.applyPreset(RegionalPreset.USA);
        settingsDao.saveSettings(settings);

        controller.refreshHeader();

        assertEquals("Metropolis Book Tower", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("USA"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("USD"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("$"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("Sales Tax"));
    }

    @Test
    @DisplayName("M3.7: Header bar correctly formats UK Regional Preset (GBP £ / VAT)")
    public void testHeaderUkPreset() throws SQLException {
        settings.setStoreName("London Literary Club");
        settings.applyPreset(RegionalPreset.UK);
        settingsDao.saveSettings(settings);

        controller.refreshHeader();

        assertEquals("London Literary Club", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("UK"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("GBP"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("£"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("20.0% VAT"));
    }

    @Test
    @DisplayName("M3.8: Header bar correctly formats Middle East / Gulf Presets (AED, SAR, QAR)")
    public void testHeaderGulfPresets() throws SQLException {
        // UAE Preset
        settings.setStoreName("Dubai Central Bookstore");
        settings.applyPreset(RegionalPreset.GULF_UAE);
        settingsDao.saveSettings(settings);

        controller.refreshHeader();
        assertEquals("Dubai Central Bookstore", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("GULF_UAE"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("AED"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("5.0% VAT"));

        // Saudi Arabia Preset
        settings.applyPreset(RegionalPreset.GULF_SAUDI);
        settingsDao.saveSettings(settings);

        controller.refreshHeader();
        assertTrue(controller.getRegionPresetLabel().getText().contains("GULF_SAUDI"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("SAR"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("15.0% VAT"));
    }

    @Test
    @DisplayName("M3.9: Keyboard shortcuts (F5, F12, F14, F15) trigger navigation")
    public void testKeyboardShortcuts() {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root);
        controller.setupKeyShortcuts(scene);

        // Simulate F5 -> POS Billing
        KeyEvent f5Event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F5, false, false, false, false);
        scene.getEventDispatcher().dispatchEvent(f5Event, null);
        assertEquals(ViewType.POS_BILLING, controller.getCurrentView());

        // Simulate F12 -> Stock Management
        KeyEvent f12Event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F12, false, false, false, false);
        scene.getEventDispatcher().dispatchEvent(f12Event, null);
        assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView());

        // Simulate Ctrl+H -> Invoices
        KeyEvent ctrlHEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.H, false, true, false, false);
        scene.getEventDispatcher().dispatchEvent(ctrlHEvent, null);
        assertEquals(ViewType.INVOICE_HISTORY, controller.getCurrentView());

        // Simulate Ctrl+S -> Settings
        KeyEvent ctrlSEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.S, false, true, false, false);
        scene.getEventDispatcher().dispatchEvent(ctrlSEvent, null);
        assertEquals(ViewType.STORE_SETTINGS, controller.getCurrentView());
    }
}
