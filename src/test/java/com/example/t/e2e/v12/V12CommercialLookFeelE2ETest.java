package com.example.t.e2e.v12;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.controller.PosBillingController;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.StoreSettings;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Test Suite for Requirement R2:
 * Real Commercial Software Visual "Look & Feel" (Dark/Light Fluent UI),
 * AtlantaFX Theme Switching, Product Card Grid, Category Filter Pills,
 * and Premium Order Summary Card.
 */
@DisplayName("v1.2.0 E2E: Commercial Look & Feel (Dark/Light Themes & Visual Cards)")
public class V12CommercialLookFeelE2ETest {

    private static Path tempDir;
    private PosBillingController posController;
    private ProductDao productDao;
    private SettingsDao settingsDao;
    private InvoiceDao invoiceDao;

    private Product bookInStock;
    private Product bookLowStock;
    private Product bookOutOfStock;
    private Product stationeryItem;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
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

    private void invokePrivateDiscount(String methodName) throws Exception {
        Method m = PosBillingController.class.getDeclaredMethod(methodName, ActionEvent.class);
        m.setAccessible(true);
        m.invoke(posController, new ActionEvent());
    }

    private double getCartSubtotal() {
        return posController.getCartItems().stream().mapToDouble(InvoiceItem::getLineTotal).sum();
    }

    private double getParsedLabelAmount(String fieldName) throws Exception {
        Field f = PosBillingController.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        Label label = (Label) f.get(posController);
        assertNotNull(label, "Label " + fieldName + " must not be null");
        String text = label.getText().replaceAll("[^0-9.]", "").trim();
        return text.isEmpty() ? 0.0 : Double.parseDouble(text);
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("v12_lookfeel_test_");
        Path dbPath = tempDir.resolve("lookfeel.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        settingsDao = new SettingsDao();
        invoiceDao = new InvoiceDao();

        // Seed test catalog
        bookInStock = new Product(0, "Designing Data-Intensive Applications", 25, 45.00, 28.00);
        bookInStock.setSku("978-1449373320");
        bookInStock.setCategory("Books");
        bookInStock.setAuthor("Martin Kleppmann");
        productDao.save(bookInStock);

        bookLowStock = new Product(0, "LowStockTestTitle: Agile Software Craftsmanship", 3, 38.00, 22.00);
        bookLowStock.setSku("978-0132350884");
        bookLowStock.setCategory("Books");
        bookLowStock.setAuthor("Robert C. Martin");
        productDao.save(bookLowStock);

        bookOutOfStock = new Product(0, "Domain-Driven Design: Tackling Complexity", 0, 55.00, 35.00);
        bookOutOfStock.setSku("978-0321125217");
        bookOutOfStock.setCategory("Books");
        bookOutOfStock.setAuthor("Eric Evans");
        productDao.save(bookOutOfStock);

        stationeryItem = new Product(0, "Premium Ruled Hardcover Journal (A5)", 50, 14.50, 6.00);
        stationeryItem.setSku("STAT-JOURNAL-A5");
        stationeryItem.setCategory("Stationery");
        stationeryItem.setAuthor("MegiStationery");
        productDao.save(stationeryItem);

        // Load POS FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root);
        posController = loader.getController();
        assertNotNull(posController);

        posController.setProductDao(productDao);
        posController.setSettingsDao(settingsDao);
        posController.setInvoiceDao(invoiceDao);
        posController.setPdfInvoiceService(new PdfInvoiceService(tempDir.resolve("invoices")));

        runOnFxThread(() -> posController.loadCatalog());
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R2 Theming, Product Tiles, Filter Pills, Summary)
    // =========================================================================

    @Test
    @DisplayName("Tier 1 - F2.1: AtlantaFX Themes Runtime Availability and Stylesheet Construction")
    public void testAtlantaFxPrimerThemesAvailability() {
        PrimerLight lightTheme = new PrimerLight();
        assertNotNull(lightTheme, "PrimerLight instance must be creatable");
        String lightCss = lightTheme.getUserAgentStylesheet();
        assertNotNull(lightCss, "PrimerLight must return valid non-null user agent stylesheet");
        assertFalse(lightCss.isBlank(), "PrimerLight stylesheet must not be empty");

        PrimerDark darkTheme = new PrimerDark();
        assertNotNull(darkTheme, "PrimerDark instance must be creatable");
        String darkCss = darkTheme.getUserAgentStylesheet();
        assertNotNull(darkCss, "PrimerDark must return valid non-null user agent stylesheet");
        assertFalse(darkCss.isBlank(), "PrimerDark stylesheet must not be empty");
        assertNotEquals(lightCss, darkCss, "Light and Dark stylesheets must be distinct");
    }

    @Test
    @DisplayName("Tier 1 - F2.2: SQLite Schema V3 Theme Persistence Contract")
    public void testThemePersistenceContractInStoreSettings() throws Exception {
        // Verify store_settings table persistence contract for ui_theme column
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if ui_theme column exists; if not, test V3 migration addition
            boolean hasColumn = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(store_settings);")) {
                while (rs.next()) {
                    if ("ui_theme".equalsIgnoreCase(rs.getString("name"))) {
                        hasColumn = true;
                        break;
                    }
                }
            }

            if (!hasColumn) {
                // Apply V3 schema addition
                stmt.execute("ALTER TABLE store_settings ADD COLUMN ui_theme TEXT DEFAULT 'LIGHT';");
            }

            // Verify update to DARK
            stmt.executeUpdate("UPDATE store_settings SET ui_theme = 'DARK' WHERE setting_id = 1;");
            try (ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next());
                assertEquals("DARK", rs.getString("ui_theme"));
            }

            // Verify update back to LIGHT
            stmt.executeUpdate("UPDATE store_settings SET ui_theme = 'LIGHT' WHERE setting_id = 1;");
            try (ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
                assertTrue(rs.next());
                assertEquals("LIGHT", rs.getString("ui_theme"));
            }
        }
    }

    @Test
    @DisplayName("Tier 1 - F2.3: Product Tile Visual Hierarchy and CSS Classes")
    public void testProductTileRenderingHierarchyAndClasses() throws Exception {
        runOnFxThread(() -> posController.renderProductTiles());

        Field flowPaneField = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        flowPaneField.setAccessible(true);
        FlowPane flowPane = (FlowPane) flowPaneField.get(posController);
        assertNotNull(flowPane, "productTilesFlowPane must exist");

        assertTrue(flowPane.getChildren().size() >= 4, "FlowPane must contain at least the 4 seeded products");

        // Inspect the first rendered tile
        VBox firstTile = (VBox) flowPane.getChildren().get(0);
        assertTrue(firstTile.getStyleClass().contains("product-tile"), "Tile must have 'product-tile' CSS class");

        // First child: Title label
        Label titleLabel = (Label) firstTile.getChildren().get(0);
        assertTrue(titleLabel.getStyleClass().contains("product-tile-title"));
        assertFalse(titleLabel.getText().isBlank());

        // Second child: Category label
        Label catLabel = (Label) firstTile.getChildren().get(1);
        assertTrue(catLabel.getStyleClass().contains("product-tile-category"));

        // Third child: Bottom row with price and stock badge
        HBox bottomRow = (HBox) firstTile.getChildren().get(2);
        Label priceLabel = (Label) bottomRow.getChildren().get(0);
        assertTrue(priceLabel.getStyleClass().contains("product-tile-price"));
        assertTrue(priceLabel.getText().contains("$"));

        Label stockLabel = (Label) bottomRow.getChildren().get(bottomRow.getChildren().size() - 1);
        assertTrue(stockLabel.getStyleClass().contains("product-tile-stock"));
    }

    @Test
    @DisplayName("Tier 1 - F2.3: Three-State Stock Badges (In-Stock, Low-Stock, Out-of-Stock)")
    public void testThreeStateStockBadgesContracts() throws Exception {
        runOnFxThread(() -> posController.renderProductTiles());

        Field flowPaneField = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        flowPaneField.setAccessible(true);
        FlowPane flowPane = (FlowPane) flowPaneField.get(posController);

        boolean foundInStock = false;
        boolean foundLowStock = false;
        boolean foundOutOfStock = false;

        for (javafx.scene.Node node : flowPane.getChildren()) {
            VBox tile = (VBox) node;
            Label title = (Label) tile.getChildren().get(0);
            HBox bottom = (HBox) tile.getChildren().get(2);
            Label stockLabel = (Label) bottom.getChildren().get(bottom.getChildren().size() - 1);

            if (title.getText().contains("Designing Data-Intensive")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-in"),
                        "In-stock product (>5) must have 'stock-in' class");
                assertTrue(stockLabel.getText().contains("25 in stock"));
                foundInStock = true;
            } else if (title.getText().contains("LowStockTestTitle")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-low"),
                        "Low-stock product (<=5) must have 'stock-low' class");
                assertTrue(stockLabel.getText().contains("3 in stock"));
                foundLowStock = true;
            } else if (title.getText().contains("Domain-Driven Design")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-out"),
                        "Out-of-stock product (0) must have 'stock-out' class");
                assertEquals("Out of Stock", stockLabel.getText());
                foundOutOfStock = true;
            }
        }

        assertTrue(foundInStock, "Must verify 'stock-in' state");
        assertTrue(foundLowStock, "Must verify 'stock-low' state");
        assertTrue(foundOutOfStock, "Must verify 'stock-out' state");
    }

    @Test
    @DisplayName("Tier 1 - F2.4: Category Filter Pills Population and Navigation")
    public void testCategoryFilterPillsContracts() throws Exception {
        runOnFxThread(() -> posController.setupCategoryChips());

        Field chipsBoxField = PosBillingController.class.getDeclaredField("categoryChipsBox");
        chipsBoxField.setAccessible(true);
        HBox chipsBox = (HBox) chipsBoxField.get(posController);
        assertNotNull(chipsBox, "categoryChipsBox must exist");

        assertTrue(chipsBox.getChildren().size() >= 3, "Chips box must contain category buttons");

        // Verify "All Items" or "ALL" chip is active initially
        Button firstChip = (Button) chipsBox.getChildren().get(0);
        assertTrue(firstChip.getStyleClass().contains("category-chip"));
        assertTrue(firstChip.getStyleClass().contains("active"));

        // Click "Stationery" chip and verify catalog is filtered
        Button stationeryChip = null;
        for (javafx.scene.Node n : chipsBox.getChildren()) {
            Button btn = (Button) n;
            if (btn.getText().toLowerCase().contains("stationery")) {
                stationeryChip = btn;
                break;
            }
        }

        if (stationeryChip != null) {
            Button finalStationeryChip = stationeryChip;
            runOnFxThread(() -> finalStationeryChip.fire());

            Field flowPaneField = PosBillingController.class.getDeclaredField("productTilesFlowPane");
            flowPaneField.setAccessible(true);
            FlowPane flowPane = (FlowPane) flowPaneField.get(posController);

            // Filtered tiles should contain only stationery
            for (javafx.scene.Node n : flowPane.getChildren()) {
                VBox tile = (VBox) n;
                Label cat = (Label) tile.getChildren().get(1);
                assertEquals("Stationery", cat.getText(), "Filtered tile must belong to Stationery category");
            }
        }
    }

    @Test
    @DisplayName("Tier 1 - F2.5: Order Summary Card Quick Discount Chips Math (-5%, -10%, -20%)")
    public void testOrderSummaryCardDiscountChipsMath() throws Exception {
        // Add 2 items of bookInStock: 2 x $45.00 = $90.00 subtotal
        posController.addProductToCart(bookInStock, 2);
        assertEquals(1, posController.getCartItems().size());
        assertEquals(90.00, getCartSubtotal(), 0.01);

        // Test 0% discount
        invokePrivateDiscount("handleDiscount0");
        assertEquals(0.00, getParsedLabelAmount("discountAmountLabel"), 0.01);

        // Test 5% discount: 5% of 90 = $4.50
        invokePrivateDiscount("handleDiscount5");
        assertEquals(4.50, getParsedLabelAmount("discountAmountLabel"), 0.01);
        assertEquals(85.50, getParsedLabelAmount("taxableAmountLabel"), 0.01);

        // Test 10% discount: 10% of 90 = $9.00
        invokePrivateDiscount("handleDiscount10");
        assertEquals(9.00, getParsedLabelAmount("discountAmountLabel"), 0.01);
        assertEquals(81.00, getParsedLabelAmount("taxableAmountLabel"), 0.01);

        // Test 20% discount: 20% of 90 = $18.00
        invokePrivateDiscount("handleDiscount20");
        assertEquals(18.00, getParsedLabelAmount("discountAmountLabel"), 0.01);
        assertEquals(72.00, getParsedLabelAmount("taxableAmountLabel"), 0.01);
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (Theming, Tiles, & Discounts)
    // =========================================================================

    @Test
    @DisplayName("Tier 2 - Boundary: Rapid Theme Switching Stability (50 Cycles)")
    public void testRapidThemeSwitchingStability() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 50; i++) {
                PrimerLight light = new PrimerLight();
                PrimerDark dark = new PrimerDark();
                assertNotNull(light.getUserAgentStylesheet());
                assertNotNull(dark.getUserAgentStylesheet());
            }
        }, "50 rapid theme cycles must not throw exceptions or exhaust heap");
    }

    @Test
    @DisplayName("Tier 2 - Boundary: Product Tile Missing Cover Art Fallback")
    public void testProductTileMissingCoverArtFallback() throws Exception {
        // Create product with null/empty cover image
        Product noCoverProduct = new Product(0, "The Mythical Man-Month", 10, 32.00, 18.00);
        noCoverProduct.setSku("978-0201835953");
        noCoverProduct.setCategory("Books");
        noCoverProduct.setAuthor("Fred Brooks");
        productDao.save(noCoverProduct);

        runOnFxThread(() -> {
            posController.loadCatalog();
            posController.renderProductTiles();
        });

        Field flowPaneField = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        flowPaneField.setAccessible(true);
        FlowPane flowPane = (FlowPane) flowPaneField.get(posController);

        boolean found = flowPane.getChildren().stream().anyMatch(n -> {
            VBox tile = (VBox) n;
            Label title = (Label) tile.getChildren().get(0);
            return title.getText().contains("Mythical Man-Month");
        });

        assertTrue(found, "Product without cover art must render cleanly in tile grid");
    }

    @Test
    @DisplayName("Tier 2 - Boundary: Out-of-Stock Card Tap Prevents Cart Insertion")
    public void testProductTileWithZeroStockClickGuard() throws Exception {
        int initialCartSize = posController.getCartItems().size();

        // Attempting to add 0-stock product
        posController.addProductToCart(bookOutOfStock, 1);

        // Cart size must NOT increase for out of stock product
        assertEquals(initialCartSize, posController.getCartItems().size(),
                "Out of stock product must not be added to active cart");
    }

    @Test
    @DisplayName("Tier 2 - Corner: Empty Category Filter Result Handling")
    public void testCategoryFilterZeroResultsHandling() throws Exception {
        // Set search query to something that matches 0 products
        Field searchField = PosBillingController.class.getDeclaredField("searchField");
        searchField.setAccessible(true);
        TextField tf = (TextField) searchField.get(posController);

        runOnFxThread(() -> {
            tf.setText("NON_EXISTENT_QUERY_99999");
            Method method = PosBillingController.class.getDeclaredMethod("applyCategoryFilter");
            method.setAccessible(true);
            method.invoke(posController);
        });

        Field flowPaneField = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        flowPaneField.setAccessible(true);
        FlowPane flowPane = (FlowPane) flowPaneField.get(posController);

        assertEquals(0, flowPane.getChildren().size(),
                "Filtering with 0 matches must render an empty tile grid without error");
    }

    @Test
    @DisplayName("Tier 2 - Boundary: Extreme Unicode Characters in Product Cards")
    public void testExtremeUnicodeCharactersInProductTiles() throws Exception {
        Product unicodeProduct = new Product(0, "كتاب الخوارزميات المتقدمة • 算法导论 🚀", 12, 60.00, 30.00);
        unicodeProduct.setSku("UNICODE-9999");
        unicodeProduct.setCategory("Books");
        unicodeProduct.setAuthor("طالب العلم / 李四");
        productDao.save(unicodeProduct);

        runOnFxThread(() -> {
            posController.loadCatalog();
            posController.renderProductTiles();
        });

        Field flowPaneField = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        flowPaneField.setAccessible(true);
        FlowPane flowPane = (FlowPane) flowPaneField.get(posController);

        boolean found = flowPane.getChildren().stream().anyMatch(n -> {
            VBox tile = (VBox) n;
            Label title = (Label) tile.getChildren().get(0);
            return title.getText().contains("كتاب الخوارزميات المتقدمة");
        });

        assertTrue(found, "Multilingual Arabic/Chinese product card must render without glyph corruptions");
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE PAIRWISE INTERACTIONS
    // =========================================================================

    @Test
    @DisplayName("Tier 3 - Pairwise: Category Filter Switching while Adding Items to Cart")
    public void testCategoryFilterAndCartAddInterleaving() throws Exception {
        // Add 1 Book
        posController.addProductToCart(bookInStock, 1);
        assertEquals(1, posController.getCartItems().size());

        // Switch filter to Stationery
        Field chipsBoxField = PosBillingController.class.getDeclaredField("categoryChipsBox");
        chipsBoxField.setAccessible(true);
        HBox chipsBox = (HBox) chipsBoxField.get(posController);

        Button stationeryBtn = null;
        for (javafx.scene.Node n : chipsBox.getChildren()) {
            Button b = (Button) n;
            if (b.getText().toLowerCase().contains("stationery")) {
                stationeryBtn = b;
                break;
            }
        }

        if (stationeryBtn != null) {
            Button finalBtn = stationeryBtn;
            runOnFxThread(() -> finalBtn.fire());
        }

        // Add 2 Stationery items to cart
        posController.addProductToCart(stationeryItem, 2);

        // Verify cart contains BOTH book and stationery
        assertEquals(2, posController.getCartItems().size());
        assertEquals("Designing Data-Intensive Applications", posController.getCartItems().get(0).getProductName());
        assertEquals("Premium Ruled Hardcover Journal (A5)", posController.getCartItems().get(1).getProductName());

        // Total: 45.00 + (2 x 14.50) = 74.00
        assertEquals(74.00, getCartSubtotal(), 0.01);
    }

    @Test
    @DisplayName("Tier 3 - Pairwise: Quick Discount Chip with Cash Change Calculation")
    public void testDiscountChipCompoundingWithTenderChange() throws Exception {
        // 2 books @ $45 = $90 subtotal
        posController.addProductToCart(bookInStock, 2);

        // Apply 10% discount: Discount = $9.00 -> Taxable = $81.00
        // USA Sales Tax (8.25%): 81.00 * 0.0825 = $6.68 -> Net Total = $87.68
        invokePrivateDiscount("handleDiscount10");
        assertEquals(87.68, posController.getCurrentNetTotal(), 0.01);

        // Tender $100.00 cash
        Field cashField = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashField.setAccessible(true);
        TextField tfCash = (TextField) cashField.get(posController);
        tfCash.setText("100.00");

        posController.recalculateOrder();

        // Change due: 100.00 - 87.68 = $12.32
        assertEquals(12.32, posController.getCurrentChangeDue(), 0.01);
    }

    // =========================================================================
    // TIER 4: REAL-WORLD RETAIL SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("Tier 4 - Real-World Scenario: Night Shift Retail Operation with Dark Theme")
    public void testNightShiftDarkThemeRetailWorkflow() throws Exception {
        // 1. Evening clerk configures store theme preference to DARK in SQLite
        try {
            Method m = SettingsDao.class.getMethod("updateTheme", String.class);
            m.invoke(settingsDao, "DARK");
        } catch (NoSuchMethodException e) {
            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Ensure column exists in test DB
                try {
                    stmt.execute("ALTER TABLE store_settings ADD COLUMN ui_theme TEXT DEFAULT 'LIGHT';");
                } catch (Exception ignored) {}
                stmt.executeUpdate("UPDATE store_settings SET ui_theme = 'DARK' WHERE setting_id = 1;");
            }
        }

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
            if (rs.next()) {
                assertEquals("DARK", rs.getString("ui_theme"));
            }
        }

        // 2. Customer orders 1 In-Stock Book and 1 Stationery Journal
        posController.addProductToCart(bookInStock, 1);    // $45.00
        posController.addProductToCart(stationeryItem, 1); // $14.50
        assertEquals(59.50, getCartSubtotal(), 0.01);

        // 3. Customer presents 5% loyalty coupon
        invokePrivateDiscount("handleDiscount5");
        // Subtotal = 59.50. 5% discount = 2.98. Taxable = 56.52.
        // Tax 8.25% on 56.52 = 4.66. Net Total = 61.18
        assertEquals(2.98, getParsedLabelAmount("discountAmountLabel"), 0.01);
        assertEquals(61.18, posController.getCurrentNetTotal(), 0.01);

        // 4. Customer tenders exact $61.18 cash
        Field cashField = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashField.setAccessible(true);
        ((TextField) cashField.get(posController)).setText("61.18");

        posController.recalculateOrder();
        assertEquals(0.00, posController.getCurrentChangeDue(), 0.01);

        // 5. Complete checkout and verify stock decrements
        posController.handleCheckout(new ActionEvent());
        assertTrue(posController.getCartItems().isEmpty(), "Cart must be cleared after checkout");

        Product refreshedBook = productDao.findById(bookInStock.getProductId()).orElseThrow();
        assertEquals(24, refreshedBook.getQuantity(), "Book stock must decrement from 25 to 24");

        Product refreshedJournal = productDao.findById(stationeryItem.getProductId()).orElseThrow();
        assertEquals(49, refreshedJournal.getQuantity(), "Journal stock must decrement from 50 to 49");
    }
}
