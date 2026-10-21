package com.example.t;

import com.example.t.controller.StockManagementController;
import com.example.t.dao.CategoryDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Harness for Milestone 3 Stock Management:
 * 1. Dynamic search filter resilience: Regex meta-characters, SQL wildcards, Unicode, Arabic, nulls.
 * 2. Extreme restock quantities (1,000,000 units), zero/negative restock validation.
 * 3. Database uniqueness: Verification that restocking never introduces duplicate product IDs.
 * 4. Low-stock badge boundary transitions: quantity at threshold + 1, threshold, 1, 0, and negative.
 */
@DisplayName("Milestone 3 - Stock Management Empirical Challenge & Adversarial Test Suite")
public class StockManagementEmpiricalChallengeTest {

    private static Path tempDir;
    private StockManagementController controller;
    private ProductDao productDao;
    private CategoryDao categoryDao;
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
        tempDir = Files.createTempDirectory("m3_challenge_stock_");
        Path dbPath = tempDir.resolve("challenge_stock.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        categoryDao = new CategoryDao();
        settingsDao = new SettingsDao();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/StockManagement.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "StockManagement FXML must load cleanly");
        controller = loader.getController();
        assertNotNull(controller, "Controller must be instantiated");

        controller.setProductDao(productDao);
        controller.setCategoryDao(categoryDao);
        controller.setSettingsDao(settingsDao);

        controller.loadCategories();
        controller.loadProducts();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // SECTION 1: Dynamic Search Filter Resilience (Special Chars, Regex, Unicode, Arabic, Null)
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 1.1: Regex meta-characters search does not throw PatternSyntaxException")
    public void testSearchFilterWithRegexMetaCharacters() {
        // Feed various individual and combined regex metacharacters
        String[] regexInputs = {
                "[.*+?^${}()|]",
                ".*",
                "(",
                ")",
                "[",
                "]",
                "{",
                "}",
                "\\",
                "^",
                "$",
                "|",
                "+",
                "?",
                "*",
                "[a-z]+",
                "^(?=.*[A-Z]).+$"
        };

        for (String regexInput : regexInputs) {
            assertDoesNotThrow(() -> {
                controller.getSearchField().setText(regexInput);
                controller.applyFilter();
            }, "Search filter must handle regex string '" + regexInput + "' as literal text without throwing PatternSyntaxException");
        }
    }

    @Test
    @DisplayName("CHALLENGE 1.2: Literal matching of products containing brackets, braces, and regex chars")
    public void testSearchFilterMatchesProductsWithRegexCharactersInTitle() throws SQLException {
        Product specialProduct = new Product(0, "C++ Primer (5th Edition) [Hardcover] {2024} *Special* + Bonus?",
                "Books", "CPP-ADV-001", "Stanley B. Lippman", "Addison-Wesley", "978-0321714114",
                30.0, 59.99, 20, 5);
        int newId = productDao.save(specialProduct);
        assertTrue(newId > 0);
        controller.loadProducts();

        // Search specifically for bracketed substring
        controller.getSearchField().setText("[Hardcover]");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
        assertEquals("CPP-ADV-001", controller.getFilteredData().get(0).getSku());

        // Search specifically for parentheses substring
        controller.getSearchField().setText("(5th Edition)");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());

        // Search specifically for question mark / plus
        controller.getSearchField().setText("+ Bonus?");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());

        // Search specifically for braces
        controller.getSearchField().setText("{2024}");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
    }

    @Test
    @DisplayName("CHALLENGE 1.3: Special SQL characters and SQL injection strings do not escape or crash")
    public void testSearchFilterWithSqlSpecialCharacters() throws SQLException {
        String[] sqlPayloads = {
                "' OR '1'='1",
                "'; DROP TABLE products; --",
                "\" OR \"\"=\"",
                "%_%%",
                "\\",
                "''",
                "<!--xml-->",
                "<script>alert(1)</script>",
                "admin' --",
                "1' UNION SELECT * FROM users --"
        };

        for (String payload : sqlPayloads) {
            assertDoesNotThrow(() -> {
                controller.getSearchField().setText(payload);
                controller.applyFilter();
            }, "In-memory filter must not crash on SQL payload: " + payload);

            // Also test ProductDao.searchByName directly against SQLite PreparedStatement
            assertDoesNotThrow(() -> {
                List<Product> results = productDao.searchByName(payload);
                assertNotNull(results);
            }, "ProductDao.searchByName must safely execute PreparedStatement without SQL injection for: " + payload);
        }
    }

    @Test
    @DisplayName("CHALLENGE 1.4: Arabic script search filter correctly matches Arabic titles, authors and publishers")
    public void testArabicScriptCatalogAndSearch() throws SQLException {
        // Insert Arabic Book
        Product arabicBook = new Product(0, "كتاب الأصول في التجارة والإدارة", "Books", "AR-BK-001",
                "نجيب محفوظ", "دار الشروق", "978-9770912345", 15.0, 30.0, 40, 5);
        int id1 = productDao.save(arabicBook);
        assertTrue(id1 > 0);

        // Insert Arabic Stationery
        Product arabicStat = new Product(0, "دفتر ملاحظات مدرسي فاخر مسطر", "Stationery", "AR-ST-001",
                null, "مكتبة الشرق الأوسط", "BAR-AR-999", 2.0, 5.0, 100, 10);
        int id2 = productDao.save(arabicStat);
        assertTrue(id2 > 0);

        controller.loadProducts();

        // 1. Search by Arabic title substring
        controller.getSearchField().setText("الأصول");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size(), "Search for 'الأصول' should match exactly 1 Arabic book");
        assertEquals("AR-BK-001", controller.getFilteredData().get(0).getSku());

        // 2. Search by Arabic author
        controller.getSearchField().setText("نجيب محفوظ");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size(), "Search for 'نجيب محفوظ' should match Arabic author");
        assertEquals("AR-BK-001", controller.getFilteredData().get(0).getSku());

        // 3. Search by Arabic publisher
        controller.getSearchField().setText("مكتبة الشرق");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size(), "Search for 'مكتبة الشرق' should match Arabic publisher");
        assertEquals("AR-ST-001", controller.getFilteredData().get(0).getSku());

        // 4. Test database direct search via ProductDao
        List<Product> daoResults = productDao.searchByName("ملاحظات");
        assertFalse(daoResults.isEmpty(), "ProductDao LIKE search should find Arabic text");
        assertEquals("AR-ST-001", daoResults.get(0).getSku());
    }

    @Test
    @DisplayName("CHALLENGE 1.5: Unicode symbols, umlauts, accented characters, and emojis in search filter")
    public void testUnicodeAndEmojiSearchFiltering() throws SQLException {
        Product unicodeProduct = new Product(0, "Einführung in die Informatik: Über das Programmieren 📚",
                "Books", "DE-BK-001", "Gerd Müller", "Springer Verlag", "978-3642123456",
                25.0, 49.50, 15, 3);
        productDao.save(unicodeProduct);

        Product frenchProduct = new Product(0, "Le Petit Prince — Édition Illustrée ✏️",
                "Books", "FR-BK-001", "Antoine de Saint-Exupéry", "Gallimard", "978-2070612758",
                8.0, 18.00, 25, 5);
        productDao.save(frenchProduct);

        Product japaneseProduct = new Product(0, "Java言語プログラミングレッスン",
                "Books", "JP-BK-001", "結城 浩", "SoftBank Creative", "978-4797371253",
                20.0, 38.00, 10, 2);
        productDao.save(japaneseProduct);

        controller.loadProducts();

        // Test German umlaut: "Über" (case-insensitive "über")
        controller.getSearchField().setText("über");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
        assertEquals("DE-BK-001", controller.getFilteredData().get(0).getSku());

        // Test French accent: "Édition" (case-insensitive "édition")
        controller.getSearchField().setText("édition");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
        assertEquals("FR-BK-001", controller.getFilteredData().get(0).getSku());

        // Test Japanese Kanji/Kana: "プログラミング"
        controller.getSearchField().setText("プログラミング");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
        assertEquals("JP-BK-001", controller.getFilteredData().get(0).getSku());

        // Test Emoji search: "📚"
        controller.getSearchField().setText("📚");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
        assertEquals("DE-BK-001", controller.getFilteredData().get(0).getSku());
    }

    @Test
    @DisplayName("CHALLENGE 1.6: Null search field, empty strings, whitespace, and null entity properties")
    public void testNullAndEmptySearchResilience() throws SQLException {
        // Insert product with null/empty nullable fields
        Product sparseProduct = new Product(0, "Minimalist Item", "General", "MIN-001",
                null, null, null, 1.0, 2.0, 5, 2);
        productDao.save(sparseProduct);
        controller.loadProducts();

        int totalCount = controller.getMasterData().size();

        // 1. null text in search field
        controller.getSearchField().setText(null);
        controller.applyFilter();
        assertEquals(totalCount, controller.getFilteredData().size(), "Null search string must display all items");

        // 2. empty text
        controller.getSearchField().setText("");
        controller.applyFilter();
        assertEquals(totalCount, controller.getFilteredData().size(), "Empty search string must display all items");

        // 3. whitespace text
        controller.getSearchField().setText("    \t  \n  ");
        controller.applyFilter();
        assertEquals(totalCount, controller.getFilteredData().size(), "Whitespace search string must trim to empty and display all items");

        // 4. search for something that doesn't match sparseProduct; ensure no NPE is thrown on null author/publisher
        controller.getSearchField().setText("NonExistentKeywordXYZ");
        controller.applyFilter();
        assertEquals(0, controller.getFilteredData().size());

        // 5. search for sparseProduct by name
        controller.getSearchField().setText("Minimalist");
        controller.applyFilter();
        assertEquals(1, controller.getFilteredData().size());
        assertEquals("MIN-001", controller.getFilteredData().get(0).getSku());
    }

    // =========================================================================
    // SECTION 2: Extreme Restock Quantities & Zero/Negative Restock Validation
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 2.1: Extreme restock quantity of 1,000,000 units succeeds without overflow")
    public void testExtremeRestockOneMillionUnits() throws SQLException {
        Product target = controller.getMasterData().get(0);
        int targetId = target.getProductId();
        int initialStock = productDao.getStock(targetId);
        assertTrue(initialStock >= 0);

        int restockAmount = 1_000_000;
        boolean ok = productDao.restockProduct(targetId, restockAmount);
        assertTrue(ok, "ProductDao must return true for restock of 1,000,000 units");

        int newStock = productDao.getStock(targetId);
        assertEquals(initialStock + restockAmount, newStock, "Database stock must increment by exactly 1,000,000 units");

        // Further restock 500,000 units to stress high cumulative counts
        boolean ok2 = productDao.restockProduct(targetId, 500_000);
        assertTrue(ok2);
        assertEquals(initialStock + 1_500_000, productDao.getStock(targetId));

        // Verify model and controller summary handle high stock numbers correctly
        target.setQuantity(newStock + 500_000);
        controller.applyFilter();
        assertDoesNotThrow(() -> {
            long totalUnits = Long.parseLong(controller.getKpiTotalUnits().getText());
            assertTrue(totalUnits >= 1_500_000, "KPI Total Units should reflect high inventory");
        });
    }

    @Test
    @DisplayName("CHALLENGE 2.2: Zero restock quantity is strictly rejected with IllegalArgumentException")
    public void testZeroRestockQuantityRejected() throws SQLException {
        Product target = controller.getMasterData().get(0);
        int targetId = target.getProductId();
        int initialStock = productDao.getStock(targetId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            productDao.restockProduct(targetId, 0);
        }, "Restocking with quantity 0 must throw IllegalArgumentException");

        assertTrue(ex.getMessage().contains("must be positive"));

        // Verify database stock was completely unaffected
        int unchangedStock = productDao.getStock(targetId);
        assertEquals(initialStock, unchangedStock, "Database stock must remain unmodified after rejected restock");
    }

    @Test
    @DisplayName("CHALLENGE 2.3: Negative restock quantities are strictly rejected with IllegalArgumentException")
    public void testNegativeRestockQuantitiesRejected() throws SQLException {
        Product target = controller.getMasterData().get(0);
        int targetId = target.getProductId();
        int initialStock = productDao.getStock(targetId);

        int[] negativeQuantities = {-1, -10, -500, -100_000, Integer.MIN_VALUE};
        for (int negativeQty : negativeQuantities) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                productDao.restockProduct(targetId, negativeQty);
            }, "Restocking with negative quantity " + negativeQty + " must throw IllegalArgumentException");

            assertTrue(ex.getMessage().contains("must be positive"));
        }

        // Verify database stock remained unaltered
        int unchangedStock = productDao.getStock(targetId);
        assertEquals(initialStock, unchangedStock, "Database stock must not decrement on negative restock");
    }

    @Test
    @DisplayName("CHALLENGE 2.4: Non-existent product ID restock returns false without creating ghost records")
    public void testRestockNonExistentProductId() throws SQLException {
        int nonExistentId = 999_999;
        boolean result = productDao.restockProduct(nonExistentId, 50);
        assertFalse(result, "Restocking a non-existent product ID must return false");
        assertEquals(-1, productDao.getStock(nonExistentId), "Non-existent product must not exist in database");
    }

    // =========================================================================
    // SECTION 3: Verification That Restocking Never Introduces Duplicate Product IDs
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 3.1: Sequential repeated restocks strictly preserve product primary key uniqueness")
    public void testRepeatedRestockPreservesDatabaseUniqueness() throws SQLException {
        Product target = controller.getMasterData().get(0);
        int targetId = target.getProductId();
        int initialCatalogSize = productDao.getAllProducts().size();

        // Perform 50 sequential restock operations on the same product
        for (int i = 1; i <= 50; i++) {
            boolean ok = productDao.restockProduct(targetId, 10);
            assertTrue(ok, "Restock iteration " + i + " must succeed");
        }

        // 1. Empirical verification via SQLite GROUP BY HAVING COUNT(*) > 1
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            String checkDuplicatesSql = "SELECT product_id, COUNT(*) AS cnt FROM products GROUP BY product_id HAVING cnt > 1;";
            try (ResultSet rs = stmt.executeQuery(checkDuplicatesSql)) {
                assertFalse(rs.next(), "Database must NEVER contain duplicate product_id rows after repeated restocks!");
            }

            // 2. Empirical verification that total count equals distinct count
            String checkCountSql = "SELECT COUNT(*) AS total_rows, COUNT(DISTINCT product_id) AS distinct_ids, " +
                    "COUNT(DISTINCT sku) AS distinct_skus FROM products;";
            try (ResultSet rs = stmt.executeQuery(checkCountSql)) {
                assertTrue(rs.next());
                int totalRows = rs.getInt("total_rows");
                int distinctIds = rs.getInt("distinct_ids");
                int distinctSkus = rs.getInt("distinct_skus");

                assertEquals(totalRows, distinctIds, "Total rows in products table must exactly match distinct product_ids");
                assertEquals(totalRows, distinctSkus, "Total rows must exactly match distinct SKUs");
                assertEquals(initialCatalogSize, totalRows, "Total catalog size must remain invariant after restocking");
            }
        }
    }

    @Test
    @DisplayName("CHALLENGE 3.2: Restocking all catalog products in batch preserves UI masterData and TableView counts")
    public void testBatchRestockingAcrossAllProductsPreservesUiItemCount() throws SQLException {
        List<Product> initialList = productDao.getAllProducts();
        int expectedSize = initialList.size();
        assertEquals(expectedSize, controller.getMasterData().size());

        // Restock every product in the catalog
        for (Product p : initialList) {
            int oldQty = p.getQuantity();
            boolean ok = productDao.restockProduct(p.getProductId(), 15);
            assertTrue(ok);
            p.setQuantity(oldQty + 15);
        }

        controller.loadProducts();

        assertEquals(expectedSize, controller.getMasterData().size(), "MasterData size must remain identical");
        assertEquals(expectedSize, controller.getProductTable().getItems().size(), "TableView items size must remain identical");

        // Verify distinct IDs in masterData
        long distinctIds = controller.getMasterData().stream().map(Product::getProductId).distinct().count();
        assertEquals(expectedSize, distinctIds, "UI masterData must contain 100% unique product IDs");
    }

    // =========================================================================
    // SECTION 4: Low-Stock Badge Boundary Transitions (Threshold, Threshold + 1, 0, Negative)
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 4.1: Low-stock badge boundary transitions (threshold + 1, threshold, 1, 0, negative)")
    @SuppressWarnings("unchecked")
    public void testLowStockBadgeBoundaryTransitions() {
        TableColumn<Product, String> statusCol = (TableColumn<Product, String>) controller.getProductTable().getColumns().get(11);
        assertEquals("Stock Status", statusCol.getText());

        int threshold = 10;
        Product testProduct = new Product(999, "Boundary Test Book", "Books", "BND-001",
                "Author", "Publisher", "ISBN", 10.0, 20.0, 20, threshold);

        // 1. Boundary: quantity = threshold + 1 (11) -> "IN STOCK"
        testProduct.setQuantity(threshold + 1);
        ObservableValue<String> val1 = statusCol.getCellObservableValue(testProduct);
        assertNotNull(val1);
        assertEquals("IN STOCK", val1.getValue(), "quantity = threshold + 1 (11 > 10) must be IN STOCK");

        // 2. Boundary: quantity = threshold (10) -> "LOW STOCK"
        testProduct.setQuantity(threshold);
        ObservableValue<String> val2 = statusCol.getCellObservableValue(testProduct);
        assertEquals("LOW STOCK", val2.getValue(), "quantity = threshold (10 == 10) must transition to LOW STOCK");

        // 3. Boundary: quantity = threshold - 1 (9) -> "LOW STOCK"
        testProduct.setQuantity(threshold - 1);
        ObservableValue<String> val3 = statusCol.getCellObservableValue(testProduct);
        assertEquals("LOW STOCK", val3.getValue(), "quantity = threshold - 1 (9 < 10) must be LOW STOCK");

        // 4. Boundary: quantity = 1 -> "LOW STOCK"
        testProduct.setQuantity(1);
        ObservableValue<String> val4 = statusCol.getCellObservableValue(testProduct);
        assertEquals("LOW STOCK", val4.getValue(), "quantity = 1 (1 <= 10) must be LOW STOCK");

        // 5. Boundary: quantity = 0 -> "OUT OF STOCK"
        testProduct.setQuantity(0);
        ObservableValue<String> val5 = statusCol.getCellObservableValue(testProduct);
        assertEquals("OUT OF STOCK", val5.getValue(), "quantity = 0 must transition to OUT OF STOCK");

        // 6. Boundary: quantity = -5 -> "OUT OF STOCK"
        testProduct.setQuantity(-5);
        ObservableValue<String> val6 = statusCol.getCellObservableValue(testProduct);
        assertEquals("OUT OF STOCK", val6.getValue(), "quantity < 0 must be OUT OF STOCK");
    }

    @Test
    @DisplayName("CHALLENGE 4.2: Low-stock checkbox filter boundary inclusion/exclusion")
    public void testLowStockFilterCheckboxBoundary() {
        int threshold = 5;
        Product inStock = new Product(101, "Item Above Threshold", 6, 10.0, 5.0); // qty 6 > 5
        inStock.setMinStockThreshold(threshold);

        Product atThreshold = new Product(102, "Item Exactly At Threshold", 5, 10.0, 5.0); // qty 5 == 5
        atThreshold.setMinStockThreshold(threshold);

        Product belowThreshold = new Product(103, "Item Below Threshold", 3, 10.0, 5.0); // qty 3 < 5
        belowThreshold.setMinStockThreshold(threshold);

        Product outOfStock = new Product(104, "Item Out of Stock", 0, 10.0, 5.0); // qty 0
        outOfStock.setMinStockThreshold(threshold);

        controller.getMasterData().setAll(inStock, atThreshold, belowThreshold, outOfStock);

        // Turn on Low Stock Only Filter
        controller.getLowStockFilterCheckBox().setSelected(true);
        controller.applyFilter();

        List<Product> filtered = controller.getFilteredData();

        // Must exclude qty 6
        assertFalse(filtered.contains(inStock), "Item with qty = threshold + 1 must be excluded from low-stock filter");

        // Must include qty 5, 3, 0
        assertTrue(filtered.contains(atThreshold), "Item with qty = threshold must be included in low-stock filter");
        assertTrue(filtered.contains(belowThreshold), "Item with qty < threshold must be included in low-stock filter");
        assertTrue(filtered.contains(outOfStock), "Item with qty = 0 must be included in low-stock filter");
        assertEquals(3, filtered.size());
    }

    @Test
    @DisplayName("CHALLENGE 4.3: Edge case where min_stock_threshold is 0")
    @SuppressWarnings("unchecked")
    public void testZeroMinStockThresholdBoundary() {
        TableColumn<Product, String> statusCol = (TableColumn<Product, String>) controller.getProductTable().getColumns().get(11);

        Product zeroThresholdProduct = new Product(201, "Zero Threshold Book", 1, 10.0, 5.0);
        zeroThresholdProduct.setMinStockThreshold(0);

        // qty = 1 > 0 -> IN STOCK
        assertEquals("IN STOCK", statusCol.getCellObservableValue(zeroThresholdProduct).getValue());

        // qty = 0 <= 0 -> OUT OF STOCK
        zeroThresholdProduct.setQuantity(0);
        assertEquals("OUT OF STOCK", statusCol.getCellObservableValue(zeroThresholdProduct).getValue());

        // Low stock filter with threshold = 0
        controller.getMasterData().setAll(zeroThresholdProduct);
        controller.getLowStockFilterCheckBox().setSelected(true);
        controller.applyFilter();

        // 0 <= 0 so it should be included
        assertEquals(1, controller.getFilteredData().size());
        assertEquals(201, controller.getFilteredData().get(0).getProductId());
    }

    @Test
    @DisplayName("CHALLENGE 4.4: Cell factory UI styling badge classes verification")
    @SuppressWarnings("unchecked")
    public void testCellFactoryBadgeStyleClasses() {
        TableColumn<Product, String> statusCol = (TableColumn<Product, String>) controller.getProductTable().getColumns().get(11);
        TableCell<Product, String> cell = (TableCell<Product, String>) statusCol.getCellFactory().call(statusCol);
        assertNotNull(cell, "Cell factory must produce TableCell instance");

        // Test OUT OF STOCK badge style
        // Call protected updateItem via subclass or reflection or direct simulation
        // In JavaFX TableCell, updateItem can be called by setting up item
        // Alternatively, invoke updateItem method directly
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method updateItemMethod = null;
            for (java.lang.reflect.Method m : cell.getClass().getDeclaredMethods()) {
                if ("updateItem".equals(m.getName()) && m.getParameterCount() == 2) {
                    updateItemMethod = m;
                    if (m.getParameterTypes()[0] == String.class) {
                        break;
                    }
                }
            }
            assertNotNull(updateItemMethod, "updateItem method must exist on cell subclass");
            updateItemMethod.setAccessible(true);

            // 1. OUT OF STOCK
            updateItemMethod.invoke(cell, "OUT OF STOCK", false);
            assertNotNull(cell.getGraphic());
            assertTrue(cell.getGraphic() instanceof Label);
            Label badge1 = (Label) cell.getGraphic();
            assertEquals("OUT OF STOCK", badge1.getText());
            assertTrue(badge1.getStyleClass().contains("badge-out-of-stock"));

            // 2. LOW STOCK
            updateItemMethod.invoke(cell, "LOW STOCK", false);
            Label badge2 = (Label) cell.getGraphic();
            assertEquals("LOW STOCK", badge2.getText());
            assertTrue(badge2.getStyleClass().contains("badge-low-stock"));

            // 3. IN STOCK
            updateItemMethod.invoke(cell, "IN STOCK", false);
            Label badge3 = (Label) cell.getGraphic();
            assertEquals("IN STOCK", badge3.getText());
            assertTrue(badge3.getStyleClass().contains("badge-in-stock"));

            // 4. Empty / null
            updateItemMethod.invoke(cell, null, true);
            assertNull(cell.getGraphic(), "Empty cell must have null graphic");
            assertNull(cell.getText(), "Empty cell must have null text");
        });
    }

    @Test
    @DisplayName("CHALLENGE 4.5: Dynamic transition of KPI low-stock count after restocking")
    public void testDynamicKpiTransitionOnRestock() throws SQLException {
        // Find a low stock product in seed catalog (e.g., ST-003 Sticky Notes with qty 4 <= threshold 5)
        Product lowStockItem = controller.getMasterData().stream()
                .filter(p -> p.getQuantity() <= p.getMinStockThreshold())
                .findFirst()
                .orElse(null);
        assertNotNull(lowStockItem, "Must have at least 1 low-stock item in seed catalog");

        long initialAlertCount = Long.parseLong(controller.getKpiLowStockCount().getText());
        assertTrue(initialAlertCount >= 1);

        // Restock this item by 50 units so it becomes IN STOCK (qty 54 > threshold 5)
        int targetId = lowStockItem.getProductId();
        boolean ok = productDao.restockProduct(targetId, 50);
        assertTrue(ok);

        lowStockItem.setQuantity(lowStockItem.getQuantity() + 50);
        controller.applyFilter();

        long updatedAlertCount = Long.parseLong(controller.getKpiLowStockCount().getText());
        assertEquals(initialAlertCount - 1, updatedAlertCount, "Low-stock KPI alert count must decrement by 1 after restocking low-stock item");
    }
}
