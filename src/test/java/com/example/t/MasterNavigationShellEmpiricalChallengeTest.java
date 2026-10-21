package com.example.t;

import com.example.t.controller.MainShellController;
import com.example.t.controller.MainShellController.ViewType;
import com.example.t.controller.StockManagementController;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Harness for Milestone 3:
 * Master Navigation Shell, View Swapping Engine, Live Dynamic Header Synchronization,
 * Keyboard Navigation Shortcuts, and Headless Execution Safety.
 */
@DisplayName("Milestone 3 - Master Navigation Shell Empirical Challenge Test Suite")
public class MasterNavigationShellEmpiricalChallengeTest {

    private static Path tempDir;
    private MainShellController controller;
    private SettingsDao settingsDao;
    private Path dbPath;

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
        tempDir = Files.createTempDirectory("m3_challenge_shell_");
        dbPath = tempDir.resolve("challenge_shell.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        settingsDao = new SettingsDao();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "MainShell FXML root node must load successfully");
        controller = loader.getController();
        assertNotNull(controller, "MainShellController must be injected by FXMLLoader");
        controller.setSettingsDao(settingsDao);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // 1. RAPID VIEW SWAPPING STRESS HARNESS
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: Rapid view swapping stress (500 transitions across all views without exception or leak)")
    public void testRapidViewSwappingStress500Cycles() {
        StackPane contentArea = controller.getContentArea();
        assertNotNull(contentArea, "ContentArea StackPane must exist");

        ViewType[] views = ViewType.values();
        int totalTransitions = 500;

        for (int i = 0; i < totalTransitions; i++) {
            ViewType target = views[i % views.length];
            assertDoesNotThrow(() -> controller.navigateTo(target),
                    "View transition to " + target + " on iteration " + i + " must not throw");

            assertEquals(target, controller.getCurrentView(),
                    "Current view must reflect " + target + " at step " + i);

            // Invariant: StackPane contentArea must contain exactly ONE active view node, not accumulate orphans
            assertEquals(1, contentArea.getChildren().size(),
                    "ContentArea child count must remain strictly 1 to prevent memory stacking leaks");
        }

        // Return to Stock Management and verify functional integrity of the rendered screen
        controller.navigateTo(ViewType.STOCK_MANAGEMENT);
        assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView());
        assertEquals(1, contentArea.getChildren().size());

        Node activeNode = contentArea.getChildren().get(0);
        assertNotNull(activeNode, "Active StockManagement view node must be present");
        assertTrue(activeNode instanceof Parent, "Active view node must be a Parent node");

        // Verify nested catalog components remain intact
        TableView<?> table = (TableView<?>) ((Parent) activeNode).lookup("#productTable");
        assertNotNull(table, "Product TableView must be present after 500 view swaps");
        assertFalse(table.getItems().isEmpty(), "Catalog items must remain loaded and responsive");
    }

    @Test
    @DisplayName("Challenge 1.2: Randomized rapid view swapping (1,000 iterations stress test)")
    public void testRandomizedRapidViewSwapping1000Iterations() {
        ViewType[] views = ViewType.values();
        Random rng = new Random(42); // Deterministic seed

        for (int i = 0; i < 1000; i++) {
            ViewType target = views[rng.nextInt(views.length)];
            assertDoesNotThrow(() -> controller.navigateTo(target));
            assertEquals(target, controller.getCurrentView());
            assertEquals(1, controller.getContentArea().getChildren().size());

            // Check active button CSS class invariant
            switch (target) {
                case POS_BILLING:
                    assertTrue(controller.getNavPosBtn().getStyleClass().contains("active"));
                    assertFalse(controller.getNavStockBtn().getStyleClass().contains("active"));
                    break;
                case STOCK_MANAGEMENT:
                    assertTrue(controller.getNavStockBtn().getStyleClass().contains("active"));
                    assertFalse(controller.getNavPosBtn().getStyleClass().contains("active"));
                    break;
                case INVOICE_HISTORY:
                    assertTrue(controller.getNavInvoicesBtn().getStyleClass().contains("active"));
                    assertFalse(controller.getNavStockBtn().getStyleClass().contains("active"));
                    break;
                case STORE_SETTINGS:
                    assertTrue(controller.getNavSettingsBtn().getStyleClass().contains("active"));
                    assertFalse(controller.getNavStockBtn().getStyleClass().contains("active"));
                    break;
            }
        }
    }

    @Test
    @DisplayName("Challenge 1.3: Placeholder view action button safely navigates back to Stock Management")
    public void testPlaceholderActionButtonNavigation() {
        ViewType[] placeholderViews = {
                ViewType.POS_BILLING,
                ViewType.INVOICE_HISTORY,
                ViewType.STORE_SETTINGS
        };

        for (ViewType placeholderView : placeholderViews) {
            Node currentCard = controller.createPlaceholderView(placeholderView);
            assertNotNull(currentCard);
            controller.getContentArea().getChildren().setAll(currentCard);

            // Find the "Go to Stock Management [F12]" button inside the placeholder card
            Button returnBtn = (Button) ((Parent) currentCard).lookup(".btn-primary");
            assertNotNull(returnBtn, "Return to Stock Management button must exist in placeholder for " + placeholderView);

            // Fire action
            returnBtn.fire();

            // Should have transitioned back to STOCK_MANAGEMENT
            assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView(),
                    "Action on placeholder button must navigate back to STOCK_MANAGEMENT");
            assertTrue(controller.getNavStockBtn().getStyleClass().contains("active"));
        }
    }

    // =========================================================================
    // 2. LIVE DYNAMIC HEADER SYNCHRONIZATION VIA SETTINGS DAO
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: Live header dynamic mutation across all multi-regional presets (USA, UK, Gulf countries)")
    public void testLiveHeaderUpdatesAllRegionalPresets() throws SQLException {
        StoreSettings s = settingsDao.getSettings();
        assertNotNull(s, "Initial store settings must be present");

        // 1. USA Preset
        s.setStoreName("Silicon Valley Book Emporium");
        s.applyPreset(RegionalPreset.USA);
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("Silicon Valley Book Emporium", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("USA"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("USD"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("$"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("8.25% Sales Tax"));

        // 2. UK Preset
        s.setStoreName("Oxford & Cambridge Academic Press");
        s.applyPreset(RegionalPreset.UK);
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("Oxford & Cambridge Academic Press", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("UK"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("GBP"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("£"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("20.0% VAT"));

        // 3. UAE Preset (5% VAT)
        s.setStoreName("Dubai Mall Flagship Bookstore");
        s.applyPreset(RegionalPreset.GULF_UAE);
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("Dubai Mall Flagship Bookstore", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("GULF_UAE"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("AED"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("5.0% VAT"));

        // 4. Saudi Arabia Preset (15% VAT)
        s.setStoreName("Riyadh Digital Library");
        s.applyPreset(RegionalPreset.GULF_SAUDI);
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("Riyadh Digital Library", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("GULF_SAUDI"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("SAR"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("15.0% VAT"));

        // 5. Qatar Preset (0% VAT)
        s.setStoreName("Doha Corniche Books");
        s.applyPreset(RegionalPreset.GULF_QATAR);
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("Doha Corniche Books", controller.getStoreNameLabel().getText());
        assertTrue(controller.getRegionPresetLabel().getText().contains("GULF_QATAR"));
        assertTrue(controller.getRegionPresetLabel().getText().contains("QAR"));
        assertTrue(controller.getStatusBadgeLabel().getText().contains("0.0% VAT"));
    }

    @Test
    @DisplayName("Challenge 2.2: Live header updates with Unicode, Arabic, and special character store names")
    public void testLiveHeaderUnicodeStoreNames() throws SQLException {
        StoreSettings s = settingsDao.getSettings();

        // Arabic store name
        s.setStoreName("مكتبة الهلال والنور الحديثة");
        s.applyPreset(RegionalPreset.GULF_UAE);
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("مكتبة الهلال والنور الحديثة", controller.getStoreNameLabel().getText());

        // Japanese / Asian characters
        s.setStoreName("東京・神田 神保町書店");
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("東京・神田 神保町書店", controller.getStoreNameLabel().getText());

        // Symbols and punctuation
        s.setStoreName("O'Reilly & Sons \"Tech\" Books <#1> & Co.");
        settingsDao.saveSettings(s);
        controller.refreshHeader();

        assertEquals("O'Reilly & Sons \"Tech\" Books <#1> & Co.", controller.getStoreNameLabel().getText());
    }

    @Test
    @DisplayName("Challenge 2.3: Live header resilience when database encounters SQLException / disconnect")
    public void testLiveHeaderResilienceOnSqlException() {
        // Inject a SettingsDao that simulates a SQLite database error / disconnect
        SettingsDao failingDao = new SettingsDao() {
            @Override
            public StoreSettings getSettings() throws SQLException {
                throw new SQLException("Simulated SQLite disk I/O or lock error");
            }
        };
        controller.setSettingsDao(failingDao);

        // Calling refreshHeader() when database encounters SQLException should catch safely
        assertDoesNotThrow(() -> controller.refreshHeader(),
                "refreshHeader must catch SQLException and degrade gracefully");

        assertEquals("SQLite Warning", controller.getStatusBadgeLabel().getText(),
                "Status badge label should signal warning status upon database exception");
    }

    @Test
    @DisplayName("Challenge 2.4: Live header null-tolerance when SettingsDao is null")
    public void testLiveHeaderNullTolerance() {
        controller.setSettingsDao(null);
        assertDoesNotThrow(() -> controller.refreshHeader(),
                "refreshHeader must not throw NullPointerException when SettingsDao is null");
    }

    // =========================================================================
    // 3. KEYBOARD NAVIGATION SHORTCUTS HARNESS (F5, F12, F14, F15)
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: Dedicated verification of F5, F12, F14, F15 direct function key shortcuts")
    public void testDirectFunctionKeyShortcuts() {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root);
        controller.setupKeyShortcuts(scene);

        java.util.concurrent.atomic.AtomicBoolean downstreamReceived = new java.util.concurrent.atomic.AtomicBoolean(false);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> downstreamReceived.set(true));

        // 1. Direct F5 -> POS_BILLING
        controller.navigateTo(ViewType.STOCK_MANAGEMENT);
        downstreamReceived.set(false);
        KeyEvent f5 = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F5, false, false, false, false);
        javafx.event.Event.fireEvent(scene, f5);
        assertEquals(ViewType.POS_BILLING, controller.getCurrentView());
        assertFalse(downstreamReceived.get(), "F5 KeyEvent must be consumed by filter and not reach downstream handlers");

        // 2. Direct F12 -> STOCK_MANAGEMENT
        downstreamReceived.set(false);
        KeyEvent f12 = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F12, false, false, false, false);
        javafx.event.Event.fireEvent(scene, f12);
        assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView());
        assertFalse(downstreamReceived.get(), "F12 KeyEvent must be consumed by filter and not reach downstream handlers");

        // 3. Direct F14 -> INVOICE_HISTORY
        downstreamReceived.set(false);
        KeyEvent f14 = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F14, false, false, false, false);
        javafx.event.Event.fireEvent(scene, f14);
        assertEquals(ViewType.INVOICE_HISTORY, controller.getCurrentView());
        assertFalse(downstreamReceived.get(), "F14 KeyEvent must be consumed by filter and not reach downstream handlers");

        // 4. Direct F15 -> STORE_SETTINGS
        downstreamReceived.set(false);
        KeyEvent f15 = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F15, false, false, false, false);
        javafx.event.Event.fireEvent(scene, f15);
        assertEquals(ViewType.STORE_SETTINGS, controller.getCurrentView());
        assertFalse(downstreamReceived.get(), "F15 KeyEvent must be consumed by filter and not reach downstream handlers");
    }

    @Test
    @DisplayName("Challenge 3.2: Secondary shortcuts Ctrl+H and Ctrl+S trigger navigation")
    public void testSecondaryShortcutKeys() {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root);
        controller.setupKeyShortcuts(scene);

        java.util.concurrent.atomic.AtomicBoolean downstreamReceived = new java.util.concurrent.atomic.AtomicBoolean(false);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> downstreamReceived.set(true));

        // Ctrl+H -> INVOICE_HISTORY
        controller.navigateTo(ViewType.POS_BILLING);
        downstreamReceived.set(false);
        KeyEvent ctrlH = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.H, false, true, false, false);
        javafx.event.Event.fireEvent(scene, ctrlH);
        assertEquals(ViewType.INVOICE_HISTORY, controller.getCurrentView());
        assertFalse(downstreamReceived.get(), "Ctrl+H KeyEvent must be consumed by filter");

        // Ctrl+S -> STORE_SETTINGS
        downstreamReceived.set(false);
        KeyEvent ctrlS = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.S, false, true, false, false);
        javafx.event.Event.fireEvent(scene, ctrlS);
        assertEquals(ViewType.STORE_SETTINGS, controller.getCurrentView());
        assertFalse(downstreamReceived.get(), "Ctrl+S KeyEvent must be consumed by filter");
    }

    @Test
    @DisplayName("Challenge 3.3: Unmapped keys and plain letters (H, S) without Ctrl do not trigger view switching")
    public void testUnmappedKeysDoNotTriggerNavigation() {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root);
        controller.setupKeyShortcuts(scene);

        java.util.concurrent.atomic.AtomicBoolean downstreamReceived = new java.util.concurrent.atomic.AtomicBoolean(false);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> downstreamReceived.set(true));

        controller.navigateTo(ViewType.STOCK_MANAGEMENT);

        // Plain 'H' without Ctrl
        downstreamReceived.set(false);
        KeyEvent plainH = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.H, false, false, false, false);
        javafx.event.Event.fireEvent(scene, plainH);
        assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView(), "Plain 'H' must not switch view");
        assertTrue(downstreamReceived.get(), "Plain 'H' should bubble to downstream handlers");

        // Plain 'S' without Ctrl
        downstreamReceived.set(false);
        KeyEvent plainS = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.S, false, false, false, false);
        javafx.event.Event.fireEvent(scene, plainS);
        assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView(), "Plain 'S' must not switch view");
        assertTrue(downstreamReceived.get(), "Plain 'S' should bubble to downstream handlers");

        // Unrelated function keys
        KeyCode[] unrelatedKeys = { KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F6, KeyCode.F7,
                KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.ENTER, KeyCode.ESCAPE, KeyCode.SPACE };

        for (KeyCode kc : unrelatedKeys) {
            downstreamReceived.set(false);
            KeyEvent evt = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", kc, false, false, false, false);
            javafx.event.Event.fireEvent(scene, evt);
            assertEquals(ViewType.STOCK_MANAGEMENT, controller.getCurrentView(), "Unmapped key " + kc + " must not switch view");
            assertTrue(downstreamReceived.get(), "Unmapped key " + kc + " must bubble to downstream handlers");
        }
    }

    @Test
    @DisplayName("Challenge 3.4: SetupKeyShortcuts handles null scene gracefully")
    public void testSetupKeyShortcutsNullSafety() {
        assertDoesNotThrow(() -> controller.setupKeyShortcuts(null),
                "setupKeyShortcuts(null) must safely no-op without NPE");
    }

    // =========================================================================
    // 4. HEADLESS SAFETY & CONTROLLER RESILIENCE UNDER MAVEN EXECUTION
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Headless safety of UI shell controllers under Maven execution")
    public void testHeadlessSafetyOfControllers() {
        // Ensure controllers can be initialized and manipulated in headless environment
        assertNotNull(controller, "MainShellController must function in headless mode");
        assertNotNull(controller.getContentArea(), "ContentArea must be initialized");
        assertNotNull(controller.getNavPosBtn(), "Nav buttons must be initialized");
        assertNotNull(controller.getNavStockBtn(), "Nav buttons must be initialized");

        // Load StockManagementController independently in headless mode
        assertDoesNotThrow(() -> {
            FXMLLoader smLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/StockManagement.fxml"));
            Parent smRoot = smLoader.load();
            assertNotNull(smRoot);
            StockManagementController smController = smLoader.getController();
            assertNotNull(smController);
            smController.loadProducts();
            assertFalse(smController.getMasterData().isEmpty());
        }, "StockManagementController must instantiate and load data headlessly");
    }

    @Test
    @DisplayName("Challenge 4.2: Real-time clock thread operates as daemon thread (safe for Maven JVM termination)")
    public void testClockThreadIsDaemon() {
        // Inspect active threads in the JVM to locate clock daemon thread
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }

        Thread[] threads = new Thread[rootGroup.activeCount() * 2];
        int count = rootGroup.enumerate(threads);

        boolean foundNonDaemonClock = false;
        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            if (t != null && t.isAlive() && t.getName() != null && t.getName().contains("clock")) {
                if (!t.isDaemon()) {
                    foundNonDaemonClock = true;
                }
            }
        }
        assertFalse(foundNonDaemonClock, "Clock thread must be a daemon thread so it does not hang Maven build exit");
    }

    @Test
    @DisplayName("Challenge 4.3: MainShellController defensive safety when FXML fields are unpopulated")
    public void testDefensiveSafetyUnpopulatedFields() {
        // Instantiate a blank controller without FXML injection
        MainShellController blankController = new MainShellController();
        assertNull(blankController.getContentArea());

        // Public methods must not throw NullPointerException even if FXML fields are unpopulated
        assertDoesNotThrow(() -> blankController.refreshHeader());
        assertDoesNotThrow(() -> blankController.navigateTo(ViewType.POS_BILLING));
        assertDoesNotThrow(() -> blankController.navigateTo(ViewType.STOCK_MANAGEMENT));
        assertDoesNotThrow(() -> blankController.setupKeyShortcuts(null));
        assertEquals(ViewType.STOCK_MANAGEMENT, blankController.getCurrentView());
    }
}
