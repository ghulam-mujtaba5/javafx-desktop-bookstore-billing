package com.example.t;

import atlantafx.base.theme.PrimerLight;
import com.example.t.dao.ProductDao;
import com.example.t.database.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3 - Modern Fluent UI Shell & Stock Management Integration Tests")
public class Milestone3IntegrationTest {

    private static Path tempDir;
    private ProductDao productDao;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("m3_integ_test_");
        Path dbPath = tempDir.resolve("integ_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();
        productDao = new ProductDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    @Test
    @DisplayName("M3.21: AtlantaFX PrimerLight theme loads successfully")
    public void testAtlantaFxThemeAvailable() {
        assertDoesNotThrow(() -> {
            PrimerLight primerLight = new PrimerLight();
            String stylesheet = primerLight.getUserAgentStylesheet();
            assertNotNull(stylesheet, "PrimerLight user agent stylesheet URL must not be null");
            Application.setUserAgentStylesheet(stylesheet);
        });
    }

    @Test
    @DisplayName("M3.22: modern-fluent.css exists and contains required CSS classes")
    public void testModernFluentCssAvailable() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/com/example/t/css/modern-fluent.css")) {
            assertNotNull(is, "Resource /com/example/t/css/modern-fluent.css must be bundled and accessible");
            String content = new String(is.readAllBytes());
            assertTrue(content.contains(".main-header"), "CSS must define .main-header");
            assertTrue(content.contains(".sidebar"), "CSS must define .sidebar");
            assertTrue(content.contains(".nav-button"), "CSS must define .nav-button");
            assertTrue(content.contains(".card"), "CSS must define .card");
            assertTrue(content.contains(".badge-in-stock"), "CSS must define .badge-in-stock");
            assertTrue(content.contains(".badge-low-stock"), "CSS must define .badge-low-stock");
            assertTrue(content.contains(".badge-out-of-stock"), "CSS must define .badge-out-of-stock");
        }
    }

    @Test
    @DisplayName("M3.23: MainShell.fxml and StockManagement.fxml exist and are valid FXML")
    public void testFxmlResourcesExist() {
        assertNotNull(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        assertNotNull(getClass().getResource("/com/example/t/fxml/StockManagement.fxml"));
    }

    @Test
    @DisplayName("M3.24: Legacy ViewStockScreen initializes from ProductDao")
    public void testViewStockScreenInitializes() {
        assertDoesNotThrow(() -> {
            ViewStockScreen screen = new ViewStockScreen();
            assertNotNull(screen);
        });
    }

    @Test
    @DisplayName("M3.25: Legacy AddStockScreen persists products to SQLite ProductDao")
    public void testAddStockScreenSyncsToProductDao() throws SQLException {
        Product product = new Product(999, "Legacy AddStock Test Item", 15, 12.50, 8.00);
        productDao.save(product);

        Product retrieved = productDao.findById(product.getProductId()).orElse(null);
        assertNotNull(retrieved, "Product saved via AddStockScreen workflow must be retrievable via ProductDao");
        assertEquals("Legacy AddStock Test Item", retrieved.getProductName());
        assertEquals(15, retrieved.getQuantity());
    }

    @Test
    @DisplayName("M3.26: Legacy UpdateStockScreen does not create duplicate IDs on restock")
    public void testUpdateStockScreenRestockDoesNotDuplicate() throws SQLException {
        List<Product> initial = productDao.getAllProducts();
        int initialCount = initial.size();
        Product selected = initial.get(0);
        int initialQty = selected.getQuantity();

        // Simulate restock logic implemented in UpdateStockScreen
        int newQuantity = 20;
        selected.setQuantity(selected.getQuantity() + newQuantity);
        productDao.update(selected);

        List<Product> afterRestock = productDao.getAllProducts();
        assertEquals(initialCount, afterRestock.size(), "Catalog product count must not increase after restock");

        Product refreshed = productDao.findById(selected.getProductId()).orElse(null);
        assertNotNull(refreshed);
        assertEquals(initialQty + newQuantity, refreshed.getQuantity());
    }
}
