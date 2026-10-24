package com.example.t.e2e.v12;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.example.t.Product;
import com.example.t.controller.PosBillingController;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.database.SchemaMigrationManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService;
import com.example.t.service.PdfInvoiceService;
import com.example.t.util.ThemeManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Test Harness executed by Challenger 1 for Milestone M1.2-B.
 *
 * Objectives:
 * 1. Theme switching stability under rapid toggle cycles and headless execution.
 * 2. SQLite persistence of ui_theme column: verify V3 migration on fresh vs existing databases.
 * 3. Three-state stock badge transitions (above 5, 1..5, 0) and boundary correctness.
 * 4. Out of stock guard: verify 0-stock products can never be added to cart via any channel.
 * 5. Quick discount chips math (-0%, -5%, -10%, -15%, -20%) and interaction with multi-regional taxes.
 */
@DisplayName("Challenger 1 Empirical Challenge: Milestone M1.2-B Deliverables")
public class V12LookFeelChallenger1EmpiricalTest {

    private static Path tempDir;
    private PosBillingController posController;
    private ProductDao productDao;
    private SettingsDao settingsDao;
    private InvoiceDao invoiceDao;

    private Product prodInStock6;
    private Product prodLowStock5;
    private Product prodLowStock1;
    private Product prodOutOfStock0;
    private Product prodNegativeStock;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }

    private static void runOnFxThread(FxAction action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        future.get(5, TimeUnit.SECONDS);
    }

    private void invokeDiscount(String methodName) throws Exception {
        Method m = PosBillingController.class.getDeclaredMethod(methodName, ActionEvent.class);
        m.setAccessible(true);
        m.invoke(posController, new ActionEvent());
    }

    private double getParsedLabelAmount(String fieldName) throws Exception {
        Field f = PosBillingController.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        Label label = (Label) f.get(posController);
        assertNotNull(label, "Label " + fieldName + " must not be null");
        String text = label.getText().replaceAll("[^0-9.]", "").trim();
        return text.isEmpty() ? 0.0 : Double.parseDouble(text);
    }

    private FlowPane getProductTilesFlowPane() throws Exception {
        Field f = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        f.setAccessible(true);
        return (FlowPane) f.get(posController);
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("c1_lookfeel_test_");
        Path dbPath = tempDir.resolve("c1_lookfeel.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        settingsDao = new SettingsDao();
        invoiceDao = new InvoiceDao();

        // Seed test catalog across stock boundaries
        prodInStock6 = new Product(0, "In-Stock Boundary Title (Stock 6)", 6, 40.00, 20.00);
        prodInStock6.setSku("SKU-STK-06");
        prodInStock6.setCategory("Books");
        prodInStock6.setAuthor("Author Alpha");
        productDao.save(prodInStock6);

        prodLowStock5 = new Product(0, "Low-Stock Boundary Title (Stock 5)", 5, 30.00, 15.00);
        prodLowStock5.setSku("SKU-STK-05");
        prodLowStock5.setCategory("Books");
        prodLowStock5.setAuthor("Author Beta");
        productDao.save(prodLowStock5);

        prodLowStock1 = new Product(0, "Low-Stock Critical Title (Stock 1)", 1, 20.00, 10.00);
        prodLowStock1.setSku("SKU-STK-01");
        prodLowStock1.setCategory("Stationery");
        prodLowStock1.setAuthor("Author Gamma");
        productDao.save(prodLowStock1);

        prodOutOfStock0 = new Product(0, "Zero-Stock Title (Stock 0)", 0, 50.00, 25.00);
        prodOutOfStock0.setSku("SKU-STK-00");
        prodOutOfStock0.setCategory("Books");
        prodOutOfStock0.setAuthor("Author Delta");
        productDao.save(prodOutOfStock0);

        prodNegativeStock = new Product(0, "Negative-Stock Anomaly Title (Stock -2)", -2, 15.00, 8.00);
        prodNegativeStock.setSku("SKU-STK-NEG");
        prodNegativeStock.setCategory("Stationery");
        prodNegativeStock.setAuthor("Author Epsilon");
        productDao.save(prodNegativeStock);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root);
        posController = loader.getController();
        assertNotNull(posController);

        posController.setProductDao(productDao);
        posController.setSettingsDao(settingsDao);
        posController.setInvoiceDao(invoiceDao);
        posController.setPdfInvoiceService(new PdfInvoiceService(tempDir.resolve("invoices")));

        runOnFxThread(() -> {
            posController.loadCatalog();
            posController.setupCategoryChips();
            posController.renderProductTiles();
        });
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // 1. THEME SWITCHING STABILITY UNDER RAPID TOGGLES & HEADLESS EXECUTION
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: Rapid Theme Toggles (60 Cycles) with Scene and SQLite Persistence")
    public void testRapidThemeToggleCyclesWithSceneAndPersistence() throws Exception {
        StackPane testRoot = new StackPane();
        Scene testScene = new Scene(testRoot, 1024, 768);

        // Run 60 rapid toggle cycles with live SettingsDao persistence
        for (int i = 0; i < 60; i++) {
            final int cycle = i;
            runOnFxThread(() -> {
                String returnedTheme = ThemeManager.toggleTheme(testScene, settingsDao);
                assertNotNull(returnedTheme);
                assertEquals(ThemeManager.getCurrentTheme(), returnedTheme);
            });
        }

        // Verify root node and SQLite are in sync
        String finalTheme = ThemeManager.getCurrentTheme();
        if (ThemeManager.THEME_DARK.equals(finalTheme)) {
            assertTrue(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_DARK));
            assertFalse(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_LIGHT));
        } else {
            assertTrue(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_LIGHT));
            assertFalse(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_DARK));
        }

        // Verify persisted state in SQLite
        StoreSettings persisted = settingsDao.getSettings();
        assertNotNull(persisted, "Store settings must exist");
        assertEquals(finalTheme, persisted.getUiTheme(), "Persisted theme must match final active theme");
    }

    @Test
    @DisplayName("Challenge 1.2: ThemeManager Input Normalization & Null Safety")
    public void testThemeManagerInputNormalizationAndNullSafety() {
        // Null theme string should safely default to LIGHT
        assertDoesNotThrow(() -> ThemeManager.applyTheme(null, null, null));
        assertEquals(ThemeManager.THEME_LIGHT, ThemeManager.getCurrentTheme());

        // Case-insensitive DARK
        assertDoesNotThrow(() -> ThemeManager.applyTheme(null, "dark", null));
        assertEquals(ThemeManager.THEME_DARK, ThemeManager.getCurrentTheme());

        // Whitespace and arbitrary input defaults to LIGHT
        assertDoesNotThrow(() -> ThemeManager.applyTheme(null, "   ", null));
        assertEquals(ThemeManager.THEME_LIGHT, ThemeManager.getCurrentTheme());

        assertDoesNotThrow(() -> ThemeManager.applyTheme(null, "NON_EXISTENT_THEME", null));
        assertEquals(ThemeManager.THEME_LIGHT, ThemeManager.getCurrentTheme());
    }

    // =========================================================================
    // 2. SQLITE PERSISTENCE & SCHEMA MIGRATION V3 (FRESH VS EXISTING DATABASES)
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: Fresh Database Initializes Schema V3 with ui_theme Column")
    public void testFreshDatabaseMigrationV3() throws Exception {
        Path freshDb = tempDir.resolve("fresh_v3.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + freshDb.toAbsolutePath())) {
            boolean applied = SchemaMigrationManager.migrate(conn);
            assertTrue(applied, "Initial migration on fresh database must return true");

            // Verify current version is 3
            assertEquals(3, SchemaMigrationManager.CURRENT_SCHEMA_VERSION);

            // Verify store_settings table structure contains ui_theme
            boolean hasUiTheme = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(store_settings);")) {
                while (rs.next()) {
                    if ("ui_theme".equalsIgnoreCase(rs.getString("name"))) {
                        hasUiTheme = true;
                        break;
                    }
                }
            }
            assertTrue(hasUiTheme, "Fresh store_settings table must contain 'ui_theme' column");

            // Verify default row has ui_theme = 'LIGHT'
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next(), "Default store_settings row must exist");
                assertEquals("LIGHT", rs.getString("ui_theme"));
            }

            // Verify idempotency: second migrate call returns false
            boolean secondApply = SchemaMigrationManager.migrate(conn);
            assertFalse(secondApply, "Repeated migration must be idempotent and return false");
        }
    }

    @Test
    @DisplayName("Challenge 2.2: Existing V1 Database Successfully Upgrades to V3")
    public void testExistingV1DatabaseUpgradeToV3() throws Exception {
        Path legacyV1Db = tempDir.resolve("legacy_v1.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + legacyV1Db.toAbsolutePath())) {
            // 1. Manually setup a pure V1 database using V1 SQL script (which lacks ui_theme)
            InputStream is = SchemaMigrationManager.class.getResourceAsStream("/com/example/t/db/V1__init_schema.sql");
            assertNotNull(is, "V1__init_schema.sql must exist on classpath");
            String script = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().filter(line -> !line.trim().startsWith("--")).collect(Collectors.joining("\n"));

            try (Statement stmt = conn.createStatement()) {
                for (String sql : script.split(";")) {
                    if (!sql.trim().isEmpty()) {
                        stmt.execute(sql.trim());
                    }
                }
                // Insert a custom V1 store_settings row WITHOUT ui_theme
                stmt.executeUpdate("INSERT INTO store_settings (setting_id, store_name, store_address, store_phone, " +
                        "region_preset, currency_code, currency_symbol, tax_name, tax_rate, receipt_language, admin_password_hash) " +
                        "VALUES (1, 'V1 Historical Books', '10 Downing St', '0123456789', 'UK', 'GBP', '£', 'VAT', 20.0, 'EN', 'hash123');");

                // Record migration 1
                stmt.executeUpdate("INSERT INTO schema_migrations (version, description) VALUES (1, 'Initial V1');");
            }

            // Verify before migration: ui_theme does NOT exist
            boolean hasColBefore = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(store_settings);")) {
                while (rs.next()) {
                    if ("ui_theme".equalsIgnoreCase(rs.getString("name"))) {
                        hasColBefore = true;
                    }
                }
            }
            assertFalse(hasColBefore, "V1 database must not have ui_theme column initially");

            // 2. Run SchemaMigrationManager.migrate
            boolean migrated = SchemaMigrationManager.migrate(conn);
            assertTrue(migrated, "Migration from V1 to V3 must report success");

            // 3. Verify ui_theme was added with default 'LIGHT'
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT store_name, currency_symbol, ui_theme FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next());
                assertEquals("V1 Historical Books", rs.getString("store_name"), "Historical data must be preserved");
                assertEquals("£", rs.getString("currency_symbol"));
                assertEquals("LIGHT", rs.getString("ui_theme"), "Migrated row must have default 'LIGHT' theme");
            }

            // 4. Verify updateTheme works on this migrated DB
            SettingsDao dao = new SettingsDao();
            boolean updated = dao.updateTheme("DARK", conn);
            assertTrue(updated, "updateTheme must succeed on migrated V1 database");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next());
                assertEquals("DARK", rs.getString("ui_theme"));
            }
        }
    }

    // =========================================================================
    // 3. THREE-STATE STOCK BADGE TRANSITIONS & BOUNDARY CONTRACTS
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: Three-State Stock Badges Hierarchy & Boundary Classes")
    public void testThreeStateStockBadgeBoundaries() throws Exception {
        FlowPane flowPane = getProductTilesFlowPane();

        boolean verifiedStockIn6 = false;
        boolean verifiedStockLow5 = false;
        boolean verifiedStockLow1 = false;
        boolean verifiedStockOut0 = false;
        boolean verifiedStockOutNeg = false;

        for (javafx.scene.Node node : flowPane.getChildren()) {
            VBox tile = (VBox) node;
            Label title = (Label) tile.getChildren().get(0);
            Label category = (Label) tile.getChildren().get(1);
            HBox bottomRow = (HBox) tile.getChildren().get(2);

            assertTrue(title.getStyleClass().contains("product-tile-title"));
            assertTrue(category.getStyleClass().contains("product-tile-category"));
            Label stockBadge = (Label) bottomRow.getChildren().get(bottomRow.getChildren().size() - 1);
            assertTrue(stockBadge.getStyleClass().contains("product-tile-stock"));

            if (title.getText().contains("Stock 6")) {
                assertTrue(stockBadge.getStyleClass().contains("stock-in"), "Stock > 5 must have 'stock-in'");
                assertTrue(stockBadge.getText().contains("6 in stock"));
                verifiedStockIn6 = true;
            } else if (title.getText().contains("Stock 5")) {
                assertTrue(stockBadge.getStyleClass().contains("stock-low"), "Stock = 5 must have 'stock-low'");
                assertTrue(stockBadge.getText().contains("5 in stock"));
                verifiedStockLow5 = true;
            } else if (title.getText().contains("Stock 1")) {
                assertTrue(stockBadge.getStyleClass().contains("stock-low"), "Stock = 1 must have 'stock-low'");
                assertTrue(stockBadge.getText().contains("1 in stock"));
                verifiedStockLow1 = true;
            } else if (title.getText().contains("Stock 0")) {
                assertTrue(stockBadge.getStyleClass().contains("stock-out"), "Stock = 0 must have 'stock-out'");
                assertEquals("Out of Stock", stockBadge.getText());
                verifiedStockOut0 = true;
            } else if (title.getText().contains("Stock -2")) {
                assertTrue(stockBadge.getStyleClass().contains("stock-out"), "Negative stock must be classified 'stock-out'");
                assertEquals("Out of Stock", stockBadge.getText());
                verifiedStockOutNeg = true;
            }
        }

        assertTrue(verifiedStockIn6, "Stock 6 (stock-in) must be verified");
        assertTrue(verifiedStockLow5, "Stock 5 (stock-low) must be verified");
        assertTrue(verifiedStockLow1, "Stock 1 (stock-low) must be verified");
        assertTrue(verifiedStockOut0, "Stock 0 (stock-out) must be verified");
        assertTrue(verifiedStockOutNeg, "Negative stock (stock-out) must be verified");
    }

    // =========================================================================
    // 4. OUT OF STOCK GUARD (ZERO-STOCK CAN NEVER BE ADDED TO CART)
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Direct addProductToCart Guard with Zero & Negative Stock")
    public void testDirectAddProductToCartZeroAndNegativeStockGuard() {
        int initialCartCount = posController.getCartItems().size();

        // 1. Attempt adding 0-stock product
        posController.addProductToCart(prodOutOfStock0, 1);
        assertEquals(initialCartCount, posController.getCartItems().size(), "0-stock item must not be added");

        // 2. Attempt adding negative stock product
        posController.addProductToCart(prodNegativeStock, 1);
        assertEquals(initialCartCount, posController.getCartItems().size(), "Negative stock item must not be added");

        // 3. Attempt adding with non-positive requested quantities
        posController.addProductToCart(prodInStock6, 0);
        assertEquals(initialCartCount, posController.getCartItems().size(), "Requested qty 0 must not be added");

        posController.addProductToCart(prodInStock6, -3);
        assertEquals(initialCartCount, posController.getCartItems().size(), "Requested negative qty must not be added");
    }

    @Test
    @DisplayName("Challenge 4.2: Barcode Scanning Guard for Zero-Stock Product")
    public void testBarcodeScanZeroStockGuard() {
        int initialCartCount = posController.getCartItems().size();

        // Scan barcode of 0-stock product
        posController.handleScannedBarcode(prodOutOfStock0.getSku());
        assertEquals(initialCartCount, posController.getCartItems().size(),
                "Scanned barcode for 0-stock product must never enter cart");
    }

    @Test
    @DisplayName("Challenge 4.3: Stock Limit Enforcement (Ceiling Protection)")
    public void testStockLimitEnforcement() {
        // prodLowStock1 has stock = 1
        posController.addProductToCart(prodLowStock1, 1);
        assertEquals(1, posController.getCartItems().size());
        assertEquals(1, posController.getCartItems().get(0).getQuantity());

        // Attempting to add a 2nd unit exceeds available stock (1)
        posController.addProductToCart(prodLowStock1, 1);
        assertEquals(1, posController.getCartItems().get(0).getQuantity(),
                "Quantity must not increment beyond available stock of 1");

        // Attempting to add 10 units at once to an empty cart for prodLowStock5 (stock 5)
        posController.addProductToCart(prodLowStock5, 10);
        assertEquals(1, posController.getCartItems().size(), "prodLowStock5 must be rejected due to stock limit");
    }

    // =========================================================================
    // 5. QUICK DISCOUNT CHIPS MATH & MULTI-REGIONAL TAX INTERACTION
    // =========================================================================

    @Test
    @DisplayName("Challenge 5.1: All Quick Discount Chips Exact Math (-0%, -5%, -10%, -15%, -20%)")
    public void testAllQuickDiscountChipsMath() throws Exception {
        // Add prodInStock6 (6 in stock, unit price $40.00, qty 2 -> subtotal = $80.00)
        posController.addProductToCart(prodInStock6, 2);
        assertEquals(80.00, posController.getCartItems().get(0).getLineTotal(), 0.001);

        // -0% Chip
        invokeDiscount("handleDiscount0");
        assertEquals(0.00, getParsedLabelAmount("discountAmountLabel"), 0.001);
        assertEquals(80.00, getParsedLabelAmount("taxableAmountLabel"), 0.001);

        // -5% Chip: 5% of 80.00 = 4.00
        invokeDiscount("handleDiscount5");
        assertEquals(4.00, getParsedLabelAmount("discountAmountLabel"), 0.001);
        assertEquals(76.00, getParsedLabelAmount("taxableAmountLabel"), 0.001);

        // -10% Chip: 10% of 80.00 = 8.00
        invokeDiscount("handleDiscount10");
        assertEquals(8.00, getParsedLabelAmount("discountAmountLabel"), 0.001);
        assertEquals(72.00, getParsedLabelAmount("taxableAmountLabel"), 0.001);

        // -15% Chip: 15% of 80.00 = 12.00
        invokeDiscount("handleDiscount15");
        assertEquals(12.00, getParsedLabelAmount("discountAmountLabel"), 0.001);
        assertEquals(68.00, getParsedLabelAmount("taxableAmountLabel"), 0.001);

        // -20% Chip: 20% of 80.00 = 16.00
        invokeDiscount("handleDiscount20");
        assertEquals(16.00, getParsedLabelAmount("discountAmountLabel"), 0.001);
        assertEquals(64.00, getParsedLabelAmount("taxableAmountLabel"), 0.001);
    }

    @Test
    @DisplayName("Challenge 5.2: Multi-Regional Tax Interaction Across Discount Chips")
    public void testMultiRegionalTaxInteractionAcrossDiscountChips() {
        // Test pure math engine across RegionalPresets and Quick Discount Percentages
        List<BillingService.OrderItem> items = List.of(
                new BillingService.OrderItem(1, "Test Book", "Books", 3, 29.99, "SKU-TAX")
        );
        // Subtotal = 3 * 29.99 = 89.97

        double[] discountChips = {0.0, 5.0, 10.0, 15.0, 20.0};
        RegionalPreset[] presets = {
                RegionalPreset.USA,
                RegionalPreset.UK,
                RegionalPreset.GULF_SAUDI,
                RegionalPreset.GULF_UAE
        };

        for (RegionalPreset preset : presets) {
            for (double chip : discountChips) {
                BillingService.CalculationResult res = BillingService.calculateForRegion(items, chip, preset, 200.0);
                assertEquals(89.97, res.getSubtotal(), 0.001);

                double expectedDiscAmt = Math.round(89.97 * (chip / 100.0) * 100.0) / 100.0;
                assertEquals(expectedDiscAmt, res.getDiscountAmount(), 0.001);

                double expectedTaxable = Math.round((89.97 - expectedDiscAmt) * 100.0) / 100.0;
                assertEquals(expectedTaxable, res.getTaxableAmount(), 0.001);

                double expectedTax = Math.round(expectedTaxable * (preset.getDefaultTaxRate() / 100.0) * 100.0) / 100.0;
                assertEquals(expectedTax, res.getTaxAmount(), 0.001);

                double expectedNet = Math.round((expectedTaxable + expectedTax) * 100.0) / 100.0;
                assertEquals(expectedNet, res.getNetTotal(), 0.001);

                double expectedChange = Math.round((200.0 - expectedNet) * 100.0) / 100.0;
                assertEquals(expectedChange, res.getChangeDue(), 0.001);
            }
        }
    }
}
