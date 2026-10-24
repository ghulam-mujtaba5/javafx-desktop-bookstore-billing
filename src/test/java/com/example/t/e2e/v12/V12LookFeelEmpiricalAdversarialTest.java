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
import com.example.t.util.ThemeManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Test Harness for Milestone M1.2-B (Requirement R2):
 * Commercial Look & Feel, Category Filter Pills, Search Combination, High-Volume Catalog Rendering,
 * Stock Badges, Theme Persistence, and Cart Preservation.
 */
@DisplayName("v1.2.0 Empirical Adversarial Challenge: Commercial Look & Feel (R2)")
public class V12LookFeelEmpiricalAdversarialTest {

    private static Path tempDir;
    private PosBillingController posController;
    private ProductDao productDao;
    private SettingsDao settingsDao;
    private InvoiceDao invoiceDao;

    private Product fictionBook;
    private Product academicBook;
    private Product stationeryItem;
    private Product magazineItem;
    private Product lowStockItem;
    private Product outOfStockItem;

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
        future.get(10, TimeUnit.SECONDS);
    }

    private void invokeDiscount(String methodName) throws Exception {
        Method m = PosBillingController.class.getDeclaredMethod(methodName, ActionEvent.class);
        m.setAccessible(true);
        m.invoke(posController, new ActionEvent());
    }

    private TextField getSearchField() throws Exception {
        Field f = PosBillingController.class.getDeclaredField("searchField");
        f.setAccessible(true);
        return (TextField) f.get(posController);
    }

    private FlowPane getProductTilesFlowPane() throws Exception {
        Field f = PosBillingController.class.getDeclaredField("productTilesFlowPane");
        f.setAccessible(true);
        return (FlowPane) f.get(posController);
    }

    private HBox getCategoryChipsBox() throws Exception {
        Field f = PosBillingController.class.getDeclaredField("categoryChipsBox");
        f.setAccessible(true);
        return (HBox) f.get(posController);
    }

    private Button findCategoryChip(String categoryName) throws Exception {
        HBox box = getCategoryChipsBox();
        for (javafx.scene.Node n : box.getChildren()) {
            if (n instanceof Button) {
                Button btn = (Button) n;
                if (btn.getText().equalsIgnoreCase(categoryName) || btn.getText().toLowerCase().contains(categoryName.toLowerCase())) {
                    return btn;
                }
            }
        }
        return null;
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("v12_adversarial_challenge_");
        Path dbPath = tempDir.resolve("pos_adv.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        settingsDao = new SettingsDao();
        invoiceDao = new InvoiceDao();

        // 1. Fiction Book
        fictionBook = new Product(0, "The Great Gatsby", 15, 24.50, 14.00);
        fictionBook.setSku("978-0743273565");
        fictionBook.setCategory("Fiction");
        fictionBook.setAuthor("F. Scott Fitzgerald");
        productDao.save(fictionBook);

        // 2. Academic Book
        academicBook = new Product(0, "Introduction to Algorithms (CLRS)", 10, 85.00, 55.00);
        academicBook.setSku("978-0262033848");
        academicBook.setCategory("Academic");
        academicBook.setAuthor("Thomas H. Cormen");
        productDao.save(academicBook);

        // 3. Stationery Item
        stationeryItem = new Product(0, "Pilot G2 Premium Gel Pens (Pack of 5)", 40, 9.99, 4.50);
        stationeryItem.setSku("PILOT-G2-BLK");
        stationeryItem.setCategory("Stationery");
        stationeryItem.setAuthor("Pilot Corp");
        productDao.save(stationeryItem);

        // 4. Magazine
        magazineItem = new Product(0, "National Geographic Explorer Edition", 8, 7.50, 3.20);
        magazineItem.setSku("NATGEO-2026-10");
        magazineItem.setCategory("Magazines");
        magazineItem.setAuthor("NatGeo Society");
        productDao.save(magazineItem);

        // 5. Low Stock Item (Exactly 3 units)
        lowStockItem = new Product(0, "Rare Signed First Edition", 3, 120.00, 80.00);
        lowStockItem.setSku("RARE-SIGNED-01");
        lowStockItem.setCategory("Fiction");
        lowStockItem.setAuthor("Arthur Conan Doyle");
        productDao.save(lowStockItem);

        // 6. Out of Stock Item (0 units)
        outOfStockItem = new Product(0, "Out of Print Anthology", 0, 45.00, 25.00);
        outOfStockItem.setSku("OOP-ANTHOLOGY");
        outOfStockItem.setCategory("Fiction");
        outOfStockItem.setAuthor("Anonymous");
        productDao.save(outOfStockItem);

        // Load Controller and FXML
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
        });
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // CHALLENGE 1: Category Filter Pills Switching with Active Cart Items
    // =========================================================================

    @Test
    @DisplayName("Challenge 1: Active Cart Items Strictly Preserved Across Repeated Category Filter Switching")
    public void testCategorySwitchingPreservesActiveCart() throws Exception {
        // Step 1: Add Fiction book to cart
        posController.addProductToCart(fictionBook, 2); // 2 x 24.50 = 49.00
        assertEquals(1, posController.getCartItems().size());

        // Step 2: Switch to Stationery category
        Button stationeryChip = findCategoryChip("Stationery");
        assertNotNull(stationeryChip, "Stationery category chip must exist");
        runOnFxThread(() -> stationeryChip.fire());

        // Step 3: Add Stationery item to cart
        posController.addProductToCart(stationeryItem, 3); // 3 x 9.99 = 29.97
        assertEquals(2, posController.getCartItems().size());

        // Step 4: Rapidly switch across all categories
        String[] categoriesToTest = {"Academic", "Magazines", "Best Sellers", "Fiction", "All Items"};
        for (String catName : categoriesToTest) {
            Button chip = findCategoryChip(catName);
            if (chip != null) {
                runOnFxThread(() -> chip.fire());
            }

            // Invariant: Cart items, line totals, and ordering MUST remain intact at every step
            assertEquals(2, posController.getCartItems().size(), "Cart item count must not mutate on category switch to: " + catName);
            assertEquals("The Great Gatsby", posController.getCartItems().get(0).getProductName());
            assertEquals(2, posController.getCartItems().get(0).getQuantity());
            assertEquals(49.00, posController.getCartItems().get(0).getLineTotal(), 0.001);

            assertEquals("Pilot G2 Premium Gel Pens (Pack of 5)", posController.getCartItems().get(1).getProductName());
            assertEquals(3, posController.getCartItems().get(1).getQuantity());
            assertEquals(29.97, posController.getCartItems().get(1).getLineTotal(), 0.001);
        }

        // Step 5: Add Academic book to cart from "All Items" view
        posController.addProductToCart(academicBook, 1); // 1 x 85.00 = 85.00
        assertEquals(3, posController.getCartItems().size());

        // Total subtotal: 49.00 + 29.97 + 85.00 = 163.97
        double expectedSubtotal = 49.00 + 29.97 + 85.00;
        double actualSubtotal = posController.getCartItems().stream().mapToDouble(InvoiceItem::getLineTotal).sum();
        assertEquals(expectedSubtotal, actualSubtotal, 0.01);

        // Step 6: Checkout successfully and verify stock decrements across all 3 categories
        runOnFxThread(() -> {
            posController.recalculateOrder();
            try {
                Field cashField = PosBillingController.class.getDeclaredField("cashTenderedField");
                cashField.setAccessible(true);
                TextField tf = (TextField) cashField.get(posController);
                tf.setText(String.format(java.util.Locale.US, "%.2f", posController.getCurrentNetTotal()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            posController.recalculateOrder();
            posController.handleCheckout(new ActionEvent());
        });

        assertTrue(posController.getCartItems().isEmpty(), "Cart must be cleared following successful checkout");

        Product refreshedFiction = productDao.findById(fictionBook.getProductId()).orElseThrow();
        assertEquals(13, refreshedFiction.getQuantity(), "Fiction stock must decrement from 15 to 13");

        Product refreshedStationery = productDao.findById(stationeryItem.getProductId()).orElseThrow();
        assertEquals(37, refreshedStationery.getQuantity(), "Stationery stock must decrement from 40 to 37");

        Product refreshedAcademic = productDao.findById(academicBook.getProductId()).orElseThrow();
        assertEquals(9, refreshedAcademic.getQuantity(), "Academic stock must decrement from 10 to 9");
    }

    // =========================================================================
    // CHALLENGE 2: Search Query Combined with Category Filter
    // =========================================================================

    @Test
    @DisplayName("Challenge 2: Multi-Dimensional Matrix Filtering (Search Query + Category Pill)")
    public void testSearchQueryCombinedWithCategoryFilter() throws Exception {
        TextField searchField = getSearchField();
        FlowPane flowPane = getProductTilesFlowPane();

        // 1. Category = "Stationery", Search = "Pilot" -> Exactly 1 match (Stationery Item)
        Button statChip = findCategoryChip("Stationery");
        assertNotNull(statChip);
        runOnFxThread(() -> {
            statChip.fire();
            searchField.setText("Pilot");
        });
        assertEquals(1, flowPane.getChildren().size(), "Only Pilot stationery should match");

        // 2. Category = "Fiction", Search = "Pilot" -> 0 matches (Pilot is in Stationery, not Fiction)
        Button fictionChip = findCategoryChip("Fiction");
        assertNotNull(fictionChip);
        runOnFxThread(() -> {
            fictionChip.fire();
        });
        assertEquals(0, flowPane.getChildren().size(), "Fiction category should have 0 matches for 'Pilot'");

        // 3. Category = "All Items", Search = "Pilot" -> 1 match
        Button allChip = findCategoryChip("All Items");
        assertNotNull(allChip);
        runOnFxThread(() -> {
            allChip.fire();
        });
        assertEquals(1, flowPane.getChildren().size(), "'All Items' should match Pilot");

        // 4. Search by SKU: Category = "All Items", Search = "978-0262033848" (CLRS ISBN) -> 1 match
        runOnFxThread(() -> searchField.setText("978-0262033848"));
        assertEquals(1, flowPane.getChildren().size());
        VBox clrsTile = (VBox) flowPane.getChildren().get(0);
        Label clrsTitle = (Label) clrsTile.getChildren().get(0);
        assertTrue(clrsTitle.getText().contains("Introduction to Algorithms"));

        // 5. Search by Author: Category = "Fiction", Search = "Fitzgerald" -> 1 match
        runOnFxThread(() -> {
            fictionChip.fire();
            searchField.setText("fitzgerald");
        });
        assertEquals(1, flowPane.getChildren().size());

        // 6. Adversarial search queries: regex characters and punctuation
        runOnFxThread(() -> searchField.setText("[*.*] ? ("));
        assertEquals(0, flowPane.getChildren().size(), "Regex meta-characters must not crash filter and should return 0 results");

        // 7. Clear search query -> Category "Fiction" should restore all fiction items
        runOnFxThread(() -> searchField.setText(""));
        assertTrue(flowPane.getChildren().size() >= 3, "Clearing search field should restore all fiction items");
    }

    // =========================================================================
    // CHALLENGE 3: High-Volume Catalog Rendering and Memory Consumption
    // =========================================================================

    @Test
    @DisplayName("Challenge 3: High-Volume Catalog Stress Test (1,000 Products & Memory Boundedness)")
    public void testHighVolumeCatalogRenderingAndMemory() throws Exception {
        FlowPane flowPane = getProductTilesFlowPane();

        // 1. Generate 1,000 distinct catalog products
        List<Product> largeCatalog = new ArrayList<>(1000);
        for (int i = 1; i <= 1000; i++) {
            String cat = (i % 3 == 0) ? "Fiction" : (i % 3 == 1) ? "Academic" : "Stationery";
            Product p = new Product(1000 + i, "Stress Test Volume Book #" + i, i % 20, 19.99 + (i % 50), 10.00);
            p.setSku("SKU-STRESS-" + String.format("%04d", i));
            p.setCategory(cat);
            p.setAuthor("Author Stress " + i);
            largeCatalog.add(p);
        }

        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        // Load into controller
        runOnFxThread(() -> {
            try {
                Field catDataField = PosBillingController.class.getDeclaredField("catalogData");
                catDataField.setAccessible(true);
                javafx.collections.ObservableList<Product> catData = (javafx.collections.ObservableList<Product>) catDataField.get(posController);
                catData.setAll(largeCatalog);
                posController.renderProductTiles();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long elapsedRenderTime = System.currentTimeMillis() - startTime;
        System.gc();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memDeltaMb = (memAfter - memBefore) / (1024 * 1024);

        // Verification assertions
        assertEquals(1000, flowPane.getChildren().size(), "FlowPane must render exactly 1,000 tiles");
        assertTrue(elapsedRenderTime < 2500, "Rendering 1,000 tiles must complete in under 2.5s (took " + elapsedRenderTime + " ms)");
        assertTrue(memDeltaMb < 60, "Memory delta for 1,000 tiles must be bounded under 60MB (was " + memDeltaMb + " MB)");

        // 2. Rapid Category Filtering Stress (20 iterations)
        long filterStartTime = System.currentTimeMillis();
        for (int cycle = 0; cycle < 20; cycle++) {
            Button chip = findCategoryChip(cycle % 2 == 0 ? "Fiction" : "Academic");
            if (chip != null) {
                runOnFxThread(() -> chip.fire());
            }
        }
        long filterElapsed = System.currentTimeMillis() - filterStartTime;
        assertTrue(filterElapsed < 3000, "20 rapid category switches over 1,000 products must complete in < 3s (took " + filterElapsed + " ms)");
    }

    // =========================================================================
    // CHALLENGE 4: 3-State Stock Badges Boundary & Negative Stock Handling
    // =========================================================================

    @Test
    @DisplayName("Challenge 4: Stock Badges Boundary Contract (6 In-Stock, 5 Low-Stock, 1 Low-Stock, 0 Out-of-Stock, -1 Out-of-Stock)")
    public void testStockBadgeBoundaryContracts() throws Exception {
        Product stock6 = new Product(0, "Stock 6 Book", 6, 20.00, 10.00);
        stock6.setCategory("Books");
        productDao.save(stock6);

        Product stock5 = new Product(0, "Stock 5 Book", 5, 20.00, 10.00);
        stock5.setCategory("Books");
        productDao.save(stock5);

        Product stock1 = new Product(0, "Stock 1 Book", 1, 20.00, 10.00);
        stock1.setCategory("Books");
        productDao.save(stock1);

        Product stockNeg = new Product(0, "Negative Stock Error Book", -1, 20.00, 10.00);
        stockNeg.setCategory("Books");
        productDao.save(stockNeg);

        runOnFxThread(() -> {
            posController.loadCatalog();
            posController.renderProductTiles();
        });

        FlowPane flowPane = getProductTilesFlowPane();
        for (javafx.scene.Node node : flowPane.getChildren()) {
            VBox tile = (VBox) node;
            Label title = (Label) tile.getChildren().get(0);
            HBox bottom = (HBox) tile.getChildren().get(2);
            Label stockLabel = (Label) bottom.getChildren().get(bottom.getChildren().size() - 1);

            if (title.getText().equals("Stock 6 Book")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-in"), "Stock=6 must have 'stock-in'");
                assertTrue(stockLabel.getText().contains("6 in stock"));
            } else if (title.getText().equals("Stock 5 Book")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-low"), "Stock=5 must have 'stock-low'");
                assertTrue(stockLabel.getText().contains("5 in stock"));
            } else if (title.getText().equals("Stock 1 Book")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-low"), "Stock=1 must have 'stock-low'");
                assertTrue(stockLabel.getText().contains("1 in stock"));
            } else if (title.getText().equals("Negative Stock Error Book")) {
                assertTrue(stockLabel.getStyleClass().contains("stock-out"), "Negative stock must defensively have 'stock-out'");
                assertEquals("Out of Stock", stockLabel.getText());
            }
        }
    }

    // =========================================================================
    // CHALLENGE 5: Click Guard and Stock Ceiling Protection
    // =========================================================================

    @Test
    @DisplayName("Challenge 5: Card Click and addProductToCart Stock Ceiling Protection")
    public void testCardClickStockCeilingProtection() throws Exception {
        // lowStockItem has stock = 3
        assertEquals(3, lowStockItem.getQuantity());

        // Click 1: Add 1 unit
        posController.addProductToCart(lowStockItem, 1);
        assertEquals(1, posController.getCartItems().get(0).getQuantity());

        // Click 2: Add 1 unit (cumulative 2)
        posController.addProductToCart(lowStockItem, 1);
        assertEquals(2, posController.getCartItems().get(0).getQuantity());

        // Click 3: Add 1 unit (cumulative 3, reaches maximum available stock)
        posController.addProductToCart(lowStockItem, 1);
        assertEquals(3, posController.getCartItems().get(0).getQuantity());

        // Click 4: Attempting to exceed stock ceiling (requested 4 > stock 3)
        posController.addProductToCart(lowStockItem, 1);
        assertEquals(3, posController.getCartItems().get(0).getQuantity(), "Quantity must remain capped at stock ceiling (3)");

        // Out of stock item (stock = 0)
        int cartSizeBefore = posController.getCartItems().size();
        posController.addProductToCart(outOfStockItem, 1);
        assertEquals(cartSizeBefore, posController.getCartItems().size(), "Out of stock item must never enter cart");
    }

    // =========================================================================
    // CHALLENGE 6: ThemeManager Synchronization & SQLite Persistence
    // =========================================================================

    @Test
    @DisplayName("Challenge 6: ThemeManager Synchronization & SQLite Persistence Cycle")
    public void testThemeManagerPersistenceAndSceneSync() throws Exception {
        StackPane testRoot = new StackPane();
        Scene testScene = new Scene(testRoot, 800, 600);

        // 1. Apply DARK theme
        runOnFxThread(() -> ThemeManager.applyTheme(testScene, ThemeManager.THEME_DARK, settingsDao));
        assertEquals(ThemeManager.THEME_DARK, ThemeManager.getCurrentTheme());
        assertTrue(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_DARK));
        assertFalse(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_LIGHT));

        // Verify SQLite store_settings table
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
            assertTrue(rs.next());
            assertEquals("DARK", rs.getString("ui_theme"));
        }

        // 2. Toggle Theme -> should switch to LIGHT
        runOnFxThread(() -> ThemeManager.toggleTheme(testScene, settingsDao));
        assertEquals(ThemeManager.THEME_LIGHT, ThemeManager.getCurrentTheme());
        assertTrue(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_LIGHT));
        assertFalse(testRoot.getStyleClass().contains(ThemeManager.CLASS_THEME_DARK));

        // Verify SQLite store_settings table updated
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT ui_theme FROM store_settings WHERE setting_id = 1;")) {
            assertTrue(rs.next());
            assertEquals("LIGHT", rs.getString("ui_theme"));
        }
    }

    // =========================================================================
    // CHALLENGE 7: Order Summary Card Quick Discount Chips Math & Boundary Safety
    // =========================================================================

    @Test
    @DisplayName("Challenge 7: Order Summary Card Quick Discount Chips Safety (Empty Cart & Calculations)")
    public void testOrderSummaryCardQuickDiscountsSafety() throws Exception {
        // Test with empty cart: quick discount clicks must be safe and not throw divide-by-zero
        assertDoesNotThrow(() -> {
            invokeDiscount("handleDiscount0");
            invokeDiscount("handleDiscount5");
            invokeDiscount("handleDiscount10");
            invokeDiscount("handleDiscount15");
            invokeDiscount("handleDiscount20");
        }, "Discounts with 0 subtotal cart must be safe");

        // Add 1 academic book: $85.00 subtotal
        posController.addProductToCart(academicBook, 1);

        // -15% discount: 15% of 85.00 = 12.75
        invokeDiscount("handleDiscount15");
        Field discField = PosBillingController.class.getDeclaredField("discountPercentField");
        discField.setAccessible(true);
        TextField tfDisc = (TextField) discField.get(posController);
        assertEquals(15.0, Double.parseDouble(tfDisc.getText()), 0.01);

        Field discLabelField = PosBillingController.class.getDeclaredField("discountAmountLabel");
        discLabelField.setAccessible(true);
        Label lblDisc = (Label) discLabelField.get(posController);
        assertTrue(lblDisc.getText().contains("12.75"));

        // Taxable amount: 85.00 - 12.75 = 72.25
        Field taxableField = PosBillingController.class.getDeclaredField("taxableAmountLabel");
        taxableField.setAccessible(true);
        Label lblTaxable = (Label) taxableField.get(posController);
        assertTrue(lblTaxable.getText().contains("72.25"));
    }
}
