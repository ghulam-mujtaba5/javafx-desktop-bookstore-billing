package com.example.t;

import com.example.t.controller.StockManagementController;
import com.example.t.dao.CategoryDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.service.CsvSyncService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3 - Stock Management Controller & Inventory View Tests")
public class StockManagementControllerTest {

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
        tempDir = Files.createTempDirectory("m3_stock_test_");
        Path dbPath = tempDir.resolve("stock_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        categoryDao = new CategoryDao();
        settingsDao = new SettingsDao();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/StockManagement.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "StockManagement FXML root must load successfully");
        controller = loader.getController();
        assertNotNull(controller, "StockManagementController must be injected by FXMLLoader");

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

    @Test
    @DisplayName("M3.10: Controller initializes catalog products, categories, and KPI summaries")
    public void testInitialization() {
        assertFalse(controller.getMasterData().isEmpty(), "Master product data must be populated from seed data");
        assertTrue(controller.getMasterData().size() >= 6, "Expected at least 6 initial seeded catalog items");

        assertNotNull(controller.getCategoryFilterComboBox().getItems());
        assertTrue(controller.getCategoryFilterComboBox().getItems().contains("All Categories"));
        assertTrue(controller.getCategoryFilterComboBox().getItems().contains("Books"));
        assertTrue(controller.getCategoryFilterComboBox().getItems().contains("Stationery"));

        int totalUnits = controller.getMasterData().stream().mapToInt(Product::getQuantity).sum();
        assertEquals(String.valueOf(controller.getMasterData().size()), controller.getKpiTotalProducts().getText());
        assertEquals(String.valueOf(totalUnits), controller.getKpiTotalUnits().getText());
        assertNotNull(controller.getKpiLowStockCount().getText());
    }

    @Test
    @DisplayName("M3.11: Real-time search filter matches product name")
    public void testSearchByName() {
        controller.getSearchField().setText("Clean Code");
        controller.applyFilter();

        List<Product> results = controller.getFilteredData();
        assertEquals(1, results.size(), "Should match exactly 1 product for 'Clean Code'");
        assertTrue(results.get(0).getProductName().contains("Clean Code"));
    }

    @Test
    @DisplayName("M3.12: Real-time search filter matches product SKU / Barcode")
    public void testSearchBySku() {
        controller.getSearchField().setText("ST-001");
        controller.applyFilter();

        List<Product> results = controller.getFilteredData();
        assertEquals(1, results.size(), "Should match exactly 1 product for SKU 'ST-001'");
        assertEquals("ST-001", results.get(0).getSku());
    }

    @Test
    @DisplayName("M3.13: Real-time search filter matches book author")
    public void testSearchByAuthor() {
        controller.getSearchField().setText("Orwell");
        controller.applyFilter();

        List<Product> results = controller.getFilteredData();
        assertEquals(1, results.size(), "Should match exactly 1 book authored by Orwell");
        assertEquals("George Orwell", results.get(0).getAuthor());
    }

    @Test
    @DisplayName("M3.14: Category filter isolates Books and Stationery catalog sections")
    public void testCategoryFiltering() {
        // Filter by Books
        controller.getCategoryFilterComboBox().setValue("Books");
        controller.applyFilter();
        List<Product> books = controller.getFilteredData();
        assertFalse(books.isEmpty());
        for (Product p : books) {
            assertEquals("Books", p.getCategory(), "Every filtered product must belong to Books category");
        }

        // Filter by Stationery
        controller.getCategoryFilterComboBox().setValue("Stationery");
        controller.applyFilter();
        List<Product> stationery = controller.getFilteredData();
        assertFalse(stationery.isEmpty());
        for (Product p : stationery) {
            assertEquals("Stationery", p.getCategory(), "Every filtered product must belong to Stationery category");
        }

        // Filter by All Categories
        controller.getCategoryFilterComboBox().setValue("All Categories");
        controller.applyFilter();
        assertEquals(controller.getMasterData().size(), controller.getFilteredData().size());
    }

    @Test
    @DisplayName("M3.15: Low-Stock filter accurately displays products at or below threshold")
    public void testLowStockFilter() {
        controller.getLowStockFilterCheckBox().setSelected(true);
        controller.applyFilter();

        List<Product> lowStock = controller.getFilteredData();
        assertFalse(lowStock.isEmpty(), "Seed catalog should have at least 1 low-stock item (e.g. Sticky notes)");

        for (Product p : lowStock) {
            assertTrue(p.getQuantity() <= p.getMinStockThreshold(),
                    "Product " + p.getProductName() + " quantity (" + p.getQuantity() +
                            ") must be <= threshold (" + p.getMinStockThreshold() + ")");
        }

        // Uncheck low stock
        controller.getLowStockFilterCheckBox().setSelected(false);
        controller.applyFilter();
        assertEquals(controller.getMasterData().size(), controller.getFilteredData().size());
    }

    @Test
    @DisplayName("M3.16: Compound filter: Category + Low-Stock Filter combination")
    public void testCompoundCategoryAndLowStockFilter() {
        controller.getCategoryFilterComboBox().setValue("Stationery");
        controller.getLowStockFilterCheckBox().setSelected(true);
        controller.applyFilter();

        List<Product> filtered = controller.getFilteredData();
        for (Product p : filtered) {
            assertEquals("Stationery", p.getCategory());
            assertTrue(p.getQuantity() <= p.getMinStockThreshold());
        }
    }

    @Test
    @DisplayName("M3.17: Restocking product increments existing record without creating duplicate IDs")
    public void testRestockProductEliminatingDuplicateIdBug() throws SQLException {
        Product target = controller.getMasterData().get(0);
        int initialCount = controller.getMasterData().size();
        int initialStock = target.getQuantity();
        int targetId = target.getProductId();

        // Perform restock of 25 units
        boolean ok = productDao.restockProduct(targetId, 25);
        assertTrue(ok, "ProductDao restock must succeed");

        target.setQuantity(target.getQuantity() + 25);

        // Verify total catalog product count did NOT increase (no duplicate rows created)
        List<Product> allProducts = productDao.getAllProducts();
        assertEquals(initialCount, allProducts.size(), "Product count must remain identical (no duplicate rows)");

        // Verify stock in DB incremented
        int refreshedStock = productDao.getStock(targetId);
        assertEquals(initialStock + 25, refreshedStock);
    }

    @Test
    @DisplayName("M3.18: Update product details persists modifications cleanly to database")
    public void testUpdateProductDetails() throws SQLException {
        Product p = controller.getMasterData().get(0);
        p.setProductName("Updated Special Edition Title");
        p.setPrice(99.95);
        p.setPurchasePrice(60.00);
        p.setMinStockThreshold(12);

        boolean updated = productDao.update(p);
        assertTrue(updated, "ProductDao update must return true");

        Product refreshed = productDao.findById(p.getProductId()).orElse(null);
        assertNotNull(refreshed);
        assertEquals("Updated Special Edition Title", refreshed.getProductName());
        assertEquals(99.95, refreshed.getPrice(), 0.001);
        assertEquals(60.00, refreshed.getPurchasePrice(), 0.001);
        assertEquals(12, refreshed.getMinStockThreshold());
    }

    @Test
    @DisplayName("M3.19: Delete product permanently removes record from catalog")
    public void testDeleteProduct() throws SQLException {
        Product temp = new Product(0, "Temporary Disposable Item", "Stationery", "TEMP-DEL-1",
                null, "Brand", null, 1.0, 2.0, 5, 2);
        int newId = productDao.save(temp);
        assertTrue(newId > 0);

        boolean deleted = productDao.delete(newId);
        assertTrue(deleted, "ProductDao delete must return true");

        assertFalse(productDao.findById(newId).isPresent(), "Deleted item must no longer exist in database");
    }

    @Test
    @DisplayName("M3.20: CSV Export saves complete catalog data to RFC 4180 file")
    public void testCsvExport() throws IOException {
        Path exportPath = tempDir.resolve("catalog_export.csv");
        CsvSyncService.exportProductsToFile(controller.getMasterData(), exportPath);

        assertTrue(Files.exists(exportPath), "CSV export file must be written to disk");
        List<String> lines = Files.readAllLines(exportPath);
        assertTrue(lines.size() >= 7, "CSV file must contain header and at least 6 product lines");
        assertTrue(lines.get(0).contains("sku,name,price,stock_quantity,author"));
    }
}
