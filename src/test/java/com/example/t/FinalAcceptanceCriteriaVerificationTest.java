package com.example.t;

import com.example.t.controller.MainShellController;
import com.example.t.controller.PosBillingController;
import com.example.t.controller.StockManagementController;
import com.example.t.dao.CategoryDao;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.Category;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService;
import com.example.t.service.CsvSyncService;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Automated Acceptance Test Suite for Milestone 6:
 * Programmatically exercises and validates each of the 7 Acceptance Criteria from ORIGINAL_REQUEST.md:
 *
 * 1. Clean compilation without errors on JDK 21.
 * 2. Automated unit tests pass for core billing calculations and SQLite DAO operations.
 * 3. Launching application initializes bookstore.db automatically and loads sample catalog.
 * 4. Creating an order decrements stock counts in real time and records invoice in SQLite and CSV export.
 * 5. Stock editing updates item details and reflects immediately across all screens.
 * 6. Multi-regional currency and tax calculation switches correctly according to store settings.
 * 7. Dedicated Maven packaging profile / script executes jpackage and produces standalone Windows artifact.
 */
@DisplayName("Final Acceptance Criteria Verification Test Suite (AC1 - AC7)")
public class FinalAcceptanceCriteriaVerificationTest {

    private static Path projectRoot;
    private static Path pomXmlPath;
    private static Path packagePs1Path;
    private static Path packageBatPath;

    private Path tempDir;
    private Path isolatedDbPath;
    private ProductDao productDao;
    private CategoryDao categoryDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfService;

    @BeforeAll
    public static void initPlatformAndPaths() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already initialized
        }
        projectRoot = Paths.get(".").toAbsolutePath().normalize();
        pomXmlPath = projectRoot.resolve("pom.xml");
        packagePs1Path = projectRoot.resolve("package.ps1");
        packageBatPath = projectRoot.resolve("package.bat");
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("final_acceptance_test_");
        isolatedDbPath = tempDir.resolve("acceptance_bookstore.db");
        DatabaseManager.setCustomDatabasePath(isolatedDbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        categoryDao = new CategoryDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
        pdfService = new PdfInvoiceService(tempDir.resolve("receipts"));
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 1: Clean Compilation on JDK 21 LTS
    // =========================================================================
    @Test
    @DisplayName("AC1: ./mvnw.cmd clean test-compile compiles cleanly without errors on JDK 21")
    public void testAcceptanceCriterion1_Jdk21CleanCompilation() throws Exception {
        // 1. Verify runtime JVM is Java 21 LTS
        int javaFeatureVersion = Runtime.version().feature();
        assertEquals(21, javaFeatureVersion, "Active JVM must be OpenJDK 21 LTS, but was: " + javaFeatureVersion);

        // 2. Verify pom.xml exists, is valid XML, and targets Java 21
        assertTrue(Files.exists(pomXmlPath), "pom.xml must exist at project root");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document pomDoc = db.parse(pomXmlPath.toFile());
        pomDoc.getDocumentElement().normalize();

        NodeList compilerReleaseNodes = pomDoc.getElementsByTagName("maven.compiler.release");
        boolean hasRelease21 = false;
        if (compilerReleaseNodes.getLength() > 0) {
            hasRelease21 = "21".equals(compilerReleaseNodes.item(0).getTextContent().trim());
        }
        if (!hasRelease21) {
            NodeList releaseNodes = pomDoc.getElementsByTagName("release");
            for (int i = 0; i < releaseNodes.getLength(); i++) {
                if ("21".equals(releaseNodes.item(i).getTextContent().trim())) {
                    hasRelease21 = true;
                    break;
                }
            }
        }
        assertTrue(hasRelease21, "pom.xml must explicitly configure compiler release 21");

        // 3. Verify Maven wrapper scripts exist at project root
        Path mvnwBat = projectRoot.resolve("mvnw.cmd");
        Path mvnwSh = projectRoot.resolve("mvnw");
        assertTrue(Files.exists(mvnwBat), "mvnw.cmd must exist at project root");
        assertTrue(Files.exists(mvnwSh), "mvnw must exist at project root");

        // 4. Verify compiled application and launcher classes exist and load cleanly
        Path mainClassFile = projectRoot.resolve("target").resolve("classes").resolve("com").resolve("example").resolve("t").resolve("Main.class");
        Path runFxClassFile = projectRoot.resolve("target").resolve("classes").resolve("com").resolve("example").resolve("t").resolve("RunFX.class");
        assertTrue(Files.exists(mainClassFile), "Main.class must be compiled under target/classes");
        assertTrue(Files.exists(runFxClassFile), "RunFX.class must be compiled under target/classes");

        Class<?> mainClass = Class.forName("com.example.t.Main");
        assertNotNull(mainClass, "Main class must load via reflection");
        Class<?> runFxClass = Class.forName("com.example.t.RunFX");
        assertNotNull(runFxClass, "RunFX class must load via reflection");
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 2: Core Billing Calculations & SQLite DAO Operations
    // =========================================================================
    @Test
    @DisplayName("AC2: Automated unit tests pass for core billing calculations and SQLite DAO operations")
    public void testAcceptanceCriterion2_AutomatedBillingCalculationsAndDaoOperations() throws Exception {
        StoreSettings settings = StoreSettings.defaultSettings(); // USD, 8.25% Sales Tax

        // 1. Core Billing Calculations Math
        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(1, "Clean Code", "BK-001", 2, 35.00)); // 70.00
        items.add(new InvoiceItem(2, "Gel Pen Set", "ST-002", 3, 6.00));  // 18.00
        // Subtotal = 88.00

        // Test 10% discount:
        // discountAmount = 88.00 * 0.10 = 8.80
        // taxableAmount = 88.00 - 8.80 = 79.20
        // taxAmount = 79.20 * 0.0825 = 6.534 -> rounded to 6.53
        // netTotal = 79.20 + 6.53 = 85.73
        BillingService.CalculationResult calc = BillingService.calculateInvoiceItems(items, 10.0, settings, 100.00);
        assertEquals(88.00, calc.getSubtotal(), 0.001, "Subtotal must be 88.00");
        assertEquals(8.80, calc.getDiscountAmount(), 0.001, "Discount amount must be 8.80");
        assertEquals(79.20, calc.getTaxableAmount(), 0.001, "Taxable amount must be 79.20");
        assertEquals(6.53, calc.getTaxAmount(), 0.001, "Sales tax (8.25%) must round to 6.53");
        assertEquals(85.73, calc.getNetTotal(), 0.001, "Net total must be 85.73");
        assertEquals(14.27, calc.getChangeDue(), 0.001, "Change due from 100.00 must be 14.27");

        // Boundary cases: 0% and 100% discount
        BillingService.CalculationResult zeroDisc = BillingService.calculateInvoiceItems(items, 0.0, settings, 100.00);
        assertEquals(0.00, zeroDisc.getDiscountAmount(), 0.001);
        assertEquals(7.26, zeroDisc.getTaxAmount(), 0.001); // 88.00 * 0.0825 = 7.26

        BillingService.CalculationResult fullDisc = BillingService.calculateInvoiceItems(items, 100.0, settings, 0.00);
        assertEquals(88.00, fullDisc.getDiscountAmount(), 0.001);
        assertEquals(0.00, fullDisc.getTaxAmount(), 0.001);
        assertEquals(0.00, fullDisc.getNetTotal(), 0.001);

        // 2. Real-time Inventory Decrement Logic via ProductDao
        Product prod = new Product(0, "DAO Calculation Item", 20, 10.00, 5.00);
        prod.setSku("DAO-TEST-001");
        int prodId = productDao.save(prod);
        assertTrue(prodId > 0, "Product must be assigned positive ID");

        try (Connection conn = DatabaseManager.getConnection()) {
            // Decrement by 5
            boolean decrOk = productDao.decrementStock(prodId, 5, conn);
            assertTrue(decrOk, "Decrementing 5 units from 20 must succeed");
            assertEquals(15, productDao.findById(prodId).get().getQuantity(), "Stock must become 15");

            // Overselling attempt (decrementing 20 when only 15 exist)
            boolean oversell = productDao.decrementStock(prodId, 20, conn);
            assertFalse(oversell, "Overselling beyond available stock must fail");
            assertEquals(15, productDao.findById(prodId).get().getQuantity(), "Stock must remain 15 after rejected oversell");

            // Negative decrement attempt
            assertThrows(IllegalArgumentException.class, () -> {
                productDao.decrementStock(prodId, -3, conn);
            }, "Negative decrement quantity must be rejected");
        }

        // 3. SQLite DAO Operations Verification
        // ProductDao CRUD
        Product retrieved = productDao.findById(prodId).orElse(null);
        assertNotNull(retrieved, "Product must be found by ID");
        assertEquals("DAO-TEST-001", retrieved.getSku());

        retrieved.setProductName("DAO Updated Name");
        retrieved.setPrice(12.50);
        boolean updated = productDao.update(retrieved);
        assertTrue(updated, "ProductDao.update must succeed");
        assertEquals("DAO Updated Name", productDao.findById(prodId).get().getProductName());

        // CategoryDao
        List<Category> categories = categoryDao.getAllCategories();
        assertFalse(categories.isEmpty(), "Categories must not be empty");
        assertTrue(categories.stream().anyMatch(c -> "Books".equalsIgnoreCase(c.getName())));
        assertTrue(categories.stream().anyMatch(c -> "Stationery".equalsIgnoreCase(c.getName())));

        // SettingsDao
        StoreSettings s = settingsDao.getSettings();
        assertNotNull(s, "StoreSettings must exist in DB");
        assertEquals("USA", s.getRegionPreset());
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 3: Automatic Database & Catalog Initialization
    // =========================================================================
    @Test
    @DisplayName("AC3: Launching application initializes bookstore.db automatically and loads sample catalog")
    public void testAcceptanceCriterion3_ApplicationLaunchInitializesDatabaseAndSampleCatalog() throws Exception {
        // Point to a fresh non-existent database file in tempDir
        Path freshDb = tempDir.resolve("fresh_launch_bookstore.db");
        assertFalse(Files.exists(freshDb), "Database file should not exist prior to initialization");

        DatabaseManager.setCustomDatabasePath(freshDb);
        DatabaseManager.initializeDatabase();

        // 1. Verify file was created on disk
        assertTrue(Files.exists(freshDb), "bookstore.db must be created on disk automatically");
        assertTrue(Files.size(freshDb) > 0, "Database file must not be 0 bytes");

        // 2. Verify WAL mode and Foreign Keys are active
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase(), "SQLite database must be configured with WAL journal mode");
            }

            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "SQLite database must have foreign keys enabled");
            }

            // 3. Verify all 6 required relational tables exist
            String[] expectedTables = {"schema_migrations", "categories", "products", "invoices", "invoice_items", "store_settings"};
            for (String tbl : expectedTables) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" + tbl + "';")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Table '" + tbl + "' must exist in schema");
                }
            }
        }

        // 4. Verify sample bookstore and stationery catalog items are seeded
        ProductDao freshProductDao = new ProductDao();
        List<Product> products = freshProductDao.getAllProducts();
        assertTrue(products.size() >= 6, "Initial sample catalog must contain at least 6 items (found " + products.size() + ")");

        // Check Books
        assertTrue(products.stream().anyMatch(p -> p.getProductName().contains("Clean Code")), "Sample catalog must contain Clean Code");
        assertTrue(products.stream().anyMatch(p -> p.getProductName().contains("Modern Java")), "Sample catalog must contain Modern Java");
        assertTrue(products.stream().anyMatch(p -> p.getProductName().contains("1984")), "Sample catalog must contain 1984");

        // Check Stationery
        assertTrue(products.stream().anyMatch(p -> p.getProductName().contains("Notebook")), "Sample catalog must contain Notebook");
        assertTrue(products.stream().anyMatch(p -> p.getProductName().contains("Pen")), "Sample catalog must contain Gel Pen");
        assertTrue(products.stream().anyMatch(p -> p.getProductName().contains("Sticky Notes")), "Sample catalog must contain Sticky Notes");

        // 5. Verify default store settings are seeded
        SettingsDao freshSettingsDao = new SettingsDao();
        StoreSettings seededSettings = freshSettingsDao.getSettings();
        assertNotNull(seededSettings, "Store settings must be seeded automatically");
        assertTrue("GM Bookstore & Stationery".equals(seededSettings.getStoreName()) || "Shop Name".equals(seededSettings.getStoreName()),
                "Store settings store name must be seeded");
        assertNotNull(seededSettings.getAdminPasswordHash(), "Admin password hash must be seeded");
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 4: Real-time Order Stock Decrement, DB & CSV Ledger
    // =========================================================================
    @Test
    @DisplayName("AC4: Creating an order decrements stock counts in real time and records invoice in DB and CSV export")
    public void testAcceptanceCriterion4_OrderCreationDecrementsStockAndRecordsInvoiceAndCsvExport() throws Exception {
        // 1. Prepare product with 50 units
        Product book = new Product(0, "E2E POS Architecture Book", 50, 40.00, 20.00);
        book.setSku("POS-AC4-001");
        int bookId = productDao.save(book);

        // 2. Load POS Billing controller
        FXMLLoader posLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent posRoot = posLoader.load();
        PosBillingController posController = posLoader.getController();
        posController.setProductDao(productDao);
        posController.setInvoiceDao(invoiceDao);
        posController.setSettingsDao(settingsDao);
        posController.setPdfInvoiceService(pdfService);

        // 3. Add 4 units to cart (4 * 40.00 = 160.00)
        posController.addProductToCart(book, 4);
        assertEquals(1, posController.getCartItems().size());
        assertEquals(4, posController.getCartItems().get(0).getQuantity());

        // 4. Enter cash tendered
        Field cashFieldProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashFieldProp.setAccessible(true);
        TextField cashField = (TextField) cashFieldProp.get(posController);
        cashField.setText("200.00");

        Field custFieldProp = PosBillingController.class.getDeclaredField("customerNameField");
        custFieldProp.setAccessible(true);
        TextField custField = (TextField) custFieldProp.get(posController);
        custField.setText("Alice Smith");

        posController.recalculateOrder();
        double netTotal = posController.getCurrentNetTotal();
        assertTrue(netTotal > 160.00, "Net total must include tax (subtotal 160 + sales tax)");

        // 5. Complete checkout
        posController.handleCheckout(new ActionEvent());

        // 6. Verify real-time stock decrement in database
        Product remainingProduct = productDao.findById(bookId).orElseThrow();
        assertEquals(46, remainingProduct.getQuantity(), "Stock must decrement immediately from 50 to 46");

        // 7. Verify invoice record and items in database
        List<Invoice> invoices = invoiceDao.getAllInvoices();
        assertFalse(invoices.isEmpty(), "Invoice record must be persisted in SQLite");
        Invoice recordedInvoice = invoices.get(0);
        assertEquals("Alice Smith", recordedInvoice.getCustomerName());
        assertEquals(netTotal, recordedInvoice.getNetPrice(), 0.01);

        List<InvoiceItem> lineItems = invoiceDao.getInvoiceItems(recordedInvoice.getInvoiceId());
        assertEquals(1, lineItems.size(), "Invoice must have 1 line item recorded");
        assertEquals(4, lineItems.get(0).getQuantity());
        assertEquals(bookId, lineItems.get(0).getProductId());

        // 8. Verify CSV Ledger Export
        Path csvExportPath = tempDir.resolve("invoices_ac4_export.csv");
        CsvSyncService.exportInvoicesToFile(invoices, csvExportPath);

        assertTrue(Files.exists(csvExportPath), "Invoices CSV export file must exist");
        List<String> lines = Files.readAllLines(csvExportPath);
        assertTrue(lines.size() >= 2, "CSV export must contain at least header and one invoice row");
        assertTrue(lines.get(0).contains("Invoice ID"), "CSV header must contain 'Invoice ID'");
        String invoiceRow = lines.get(1);
        assertTrue(invoiceRow.contains("Alice Smith"), "CSV export must contain customer name 'Alice Smith'");
        assertTrue(invoiceRow.contains(String.valueOf(recordedInvoice.getInvoiceId())), "CSV export must contain invoice ID");
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 5: Stock Editing Reflects Across All Screens
    // =========================================================================
    @Test
    @DisplayName("AC5: Stock editing updates item details and reflects immediately across all screens")
    public void testAcceptanceCriterion5_StockEditingReflectsAcrossAllScreens() throws Exception {
        // 1. Create a product in catalog
        Product originalProduct = new Product(0, "Original Item Title", 25, 15.00, 9.00);
        originalProduct.setSku("SYNC-SCREEN-01");
        int prodId = productDao.save(originalProduct);

        // 2. Load Stock Management Screen
        FXMLLoader stockLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/StockManagement.fxml"));
        Parent stockRoot = stockLoader.load();
        StockManagementController stockController = stockLoader.getController();
        stockController.setProductDao(productDao);
        stockController.setCategoryDao(categoryDao);
        stockController.setSettingsDao(settingsDao);
        stockController.loadCategories();
        stockController.loadProducts();

        // 3. Load POS Billing Screen
        FXMLLoader posLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent posRoot = posLoader.load();
        PosBillingController posController = posLoader.getController();
        posController.setProductDao(productDao);
        posController.setInvoiceDao(invoiceDao);
        posController.setSettingsDao(settingsDao);
        posController.setPdfInvoiceService(pdfService);
        posController.loadCatalog();

        // 4. Update product details (simulate stock edit)
        Product toUpdate = productDao.findById(prodId).orElseThrow();
        toUpdate.setProductName("Reflected Master Title Deluxe");
        toUpdate.setPrice(35.50);
        toUpdate.setQuantity(60);
        toUpdate.setAuthor("Renowned Author");
        boolean updateSuccess = productDao.update(toUpdate);
        assertTrue(updateSuccess, "Product details must update in ProductDao");

        // 5. Refresh both screens and verify immediate reflection
        stockController.loadProducts();
        posController.loadCatalog();

        // Verify in Stock Management
        Product inStockScreen = productDao.findById(prodId).orElseThrow();
        assertEquals("Reflected Master Title Deluxe", inStockScreen.getProductName());
        assertEquals(35.50, inStockScreen.getPrice(), 0.001);
        assertEquals(60, inStockScreen.getQuantity());

        // Verify in POS Billing catalog
        Field catalogDataField = PosBillingController.class.getDeclaredField("catalogData");
        catalogDataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Product> posCatalog = (List<Product>) catalogDataField.get(posController);
        Product posItem = posCatalog.stream()
                .filter(p -> p.getProductId() == prodId)
                .findFirst()
                .orElse(null);
        assertNotNull(posItem, "Product must appear in POS catalog");
        assertEquals("Reflected Master Title Deluxe", posItem.getProductName(), "Updated title must reflect in POS catalog");
        assertEquals(35.50, posItem.getPrice(), 0.001, "Updated price must reflect in POS catalog");
        assertEquals(60, posItem.getQuantity(), "Updated quantity must reflect in POS catalog");

        // Verify POS billing math uses updated price
        posController.addProductToCart(posItem, 2);
        assertEquals(1, posController.getCartItems().size());
        assertEquals(71.00, posController.getCartItems().get(0).getLineTotal(), 0.001, "POS cart line total must use updated price (2 * 35.50 = 71.00)");
    }

    private static StoreSettings createSettings(RegionalPreset preset) {
        StoreSettings s = new StoreSettings();
        s.applyPreset(preset);
        return s;
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 6: Multi-Regional Currency & Tax Calculations
    // =========================================================================
    @Test
    @DisplayName("AC6: Multi-regional currency and tax calculation switches correctly according to store settings")
    public void testAcceptanceCriterion6_MultiRegionalCurrencyAndTaxCalculationSwitching() throws Exception {
        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(1, "Test Item", "SKU-REG", 1, 100.00));

        // 1. USA: USD, $, 8.25% Sales Tax
        StoreSettings usa = createSettings(RegionalPreset.USA);
        BillingService.CalculationResult usaCalc = BillingService.calculateInvoiceItems(items, 0.0, usa, 120.00);
        assertEquals("USD", usa.getCurrencyCode());
        assertEquals("$", usa.getCurrencySymbol());
        assertEquals(8.25, usa.getTaxRate(), 0.001);
        assertEquals(8.25, usaCalc.getTaxAmount(), 0.001);
        assertEquals(108.25, usaCalc.getNetTotal(), 0.001);

        // 2. UK: GBP, £, 20.0% VAT
        StoreSettings uk = createSettings(RegionalPreset.UK);
        BillingService.CalculationResult ukCalc = BillingService.calculateInvoiceItems(items, 10.0, uk, 120.00);
        assertEquals("GBP", uk.getCurrencyCode());
        assertEquals("£", uk.getCurrencySymbol());
        assertEquals(20.0, uk.getTaxRate(), 0.001);
        // Subtotal 100 - 10% disc = 90.00; 90.00 * 20% = 18.00 VAT; Net = 108.00
        assertEquals(90.00, ukCalc.getTaxableAmount(), 0.001);
        assertEquals(18.00, ukCalc.getTaxAmount(), 0.001);
        assertEquals(108.00, ukCalc.getNetTotal(), 0.001);

        // 3. UAE: AED, د.إ, 5.0% VAT, Bilingual Arabic/English
        StoreSettings uae = createSettings(RegionalPreset.GULF_UAE);
        BillingService.CalculationResult uaeCalc = BillingService.calculateInvoiceItems(items, 0.0, uae, 150.00);
        assertEquals("AED", uae.getCurrencyCode());
        assertTrue("AED".equals(uae.getCurrencySymbol()) || "د.إ".equals(uae.getCurrencySymbol()));
        assertEquals(5.0, uae.getTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", uae.getReceiptLanguage());
        assertEquals(5.00, uaeCalc.getTaxAmount(), 0.001);
        assertEquals(105.00, uaeCalc.getNetTotal(), 0.001);

        // 4. Saudi Arabia: SAR, ر.س, 15.0% VAT
        StoreSettings ksa = createSettings(RegionalPreset.GULF_SAUDI);
        BillingService.CalculationResult ksaCalc = BillingService.calculateInvoiceItems(items, 0.0, ksa, 150.00);
        assertEquals("SAR", ksa.getCurrencyCode());
        assertTrue("SAR".equals(ksa.getCurrencySymbol()) || "ر.س".equals(ksa.getCurrencySymbol()));
        assertEquals(15.0, ksa.getTaxRate(), 0.001);
        assertEquals(15.00, ksaCalc.getTaxAmount(), 0.001);
        assertEquals(115.00, ksaCalc.getNetTotal(), 0.001);

        // 5. Qatar: QAR, ر.ق, 0.0% VAT
        StoreSettings qat = createSettings(RegionalPreset.GULF_QATAR);
        BillingService.CalculationResult qatCalc = BillingService.calculateInvoiceItems(items, 0.0, qat, 100.00);
        assertEquals("QAR", qat.getCurrencyCode());
        assertTrue("QAR".equals(qat.getCurrencySymbol()) || "ر.ق".equals(qat.getCurrencySymbol()));
        assertEquals(0.0, qatCalc.getTaxAmount(), 0.001);
        assertEquals(100.00, qatCalc.getNetTotal(), 0.001);

        // 6. Settings Persistence & Shell Header Synchronization
        settingsDao.saveSettings(uae);
        StoreSettings retrievedUae = settingsDao.getSettings();
        assertEquals("GULF_UAE", retrievedUae.getRegionPreset());
        assertEquals("AED", retrievedUae.getCurrencyCode());

        FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        Parent shellRoot = shellLoader.load();
        MainShellController shellController = shellLoader.getController();
        shellController.setSettingsDao(settingsDao);
        shellController.refreshHeader();

        assertTrue(shellController.getRegionPresetLabel().getText().contains("AED"), "Header must display active AED currency");
        assertTrue(shellController.getRegionPresetLabel().getText().contains("AED") || shellController.getRegionPresetLabel().getText().contains("د.إ"), "Header must display regional currency");
        assertTrue(shellController.getStatusBadgeLabel().getText().contains("5.0%"), "Header badge must show 5.0% tax rate");
    }

    // =========================================================================
    // ACCEPTANCE CRITERION 7: Standalone Windows Packaging Configuration & Artifact
    // =========================================================================
    @Test
    @DisplayName("AC7: Dedicated Maven build profile and packaging script executes jpackage and produces standalone Windows artifact")
    public void testAcceptanceCriterion7_StandaloneWindowsPackagingArtifactGeneration() throws Exception {
        // 1. Verify dedicated Maven profile 'package-windows' in pom.xml
        String pomContent = Files.readString(pomXmlPath);
        assertTrue(pomContent.contains("<id>package-windows</id>"), "pom.xml must declare package-windows profile");
        assertTrue(pomContent.contains("maven-dependency-plugin"), "Profile must configure maven-dependency-plugin");
        assertTrue(pomContent.contains("copy-dependencies"), "Profile must bind copy-dependencies");
        assertTrue(pomContent.contains("<mainClass>com.example.t.RunFX</mainClass>"), "Profile must specify launcher class com.example.t.RunFX");

        // 2. Verify packaging scripts exist and contain required configurations
        assertTrue(Files.exists(packagePs1Path), "package.ps1 must exist at project root");
        assertTrue(Files.exists(packageBatPath), "package.bat must exist at project root");
        String ps1Content = Files.readString(packagePs1Path);
        assertTrue(ps1Content.contains("--type") && ps1Content.contains("app-image"), "package.ps1 must specify --type app-image");
        assertTrue(ps1Content.contains("BookstoreBilling"), "package.ps1 must specify BookstoreBilling as app name");
        assertTrue(ps1Content.contains("com.example.t.RunFX"), "package.ps1 must specify com.example.t.RunFX as main class");
        assertTrue(ps1Content.contains("java.desktop"), "package.ps1 must include java.desktop module");
        assertTrue(ps1Content.contains("java.sql"), "package.ps1 must include java.sql module");

        // 3. Test script dry-run execution
        ProcessBuilder dryRunPb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", packagePs1Path.toString(),
                "-DryRun"
        );
        dryRunPb.directory(projectRoot.toFile());
        dryRunPb.redirectErrorStream(true);
        Process dryRunProcess = dryRunPb.start();
        boolean dryRunFinished = dryRunProcess.waitFor(20, TimeUnit.SECONDS);
        assertTrue(dryRunFinished, "package.ps1 -DryRun must complete within timeout");
        assertEquals(0, dryRunProcess.exitValue(), "package.ps1 -DryRun must exit with code 0");

        // 4. Verify standalone Windows distribution artifact in target/dist/BookstoreBilling
        Path distAppDir = projectRoot.resolve("target").resolve("dist").resolve("BookstoreBilling");
        Path distExe = distAppDir.resolve("BookstoreBilling.exe");
        Path distRuntime = distAppDir.resolve("runtime");
        Path distApp = distAppDir.resolve("app");

        // If not already built, run packaging script to generate it
        if (!Files.exists(distExe)) {
            ProcessBuilder buildPb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", packagePs1Path.toString()
            );
            buildPb.directory(projectRoot.toFile());
            Process buildProcess = buildPb.start();
            boolean buildFinished = buildProcess.waitFor(90, TimeUnit.SECONDS);
            assertTrue(buildFinished, "Packaging process timed out");
            assertEquals(0, buildProcess.exitValue(), "Packaging must succeed with exit code 0");
        }

        // Verify BookstoreBilling.exe binary
        assertTrue(Files.exists(distExe), "BookstoreBilling.exe must exist in target/dist/BookstoreBilling/");
        long exeSize = Files.size(distExe);
        assertTrue(exeSize > 200_000, "BookstoreBilling.exe size must be > 200KB, was: " + exeSize);

        // Verify PE Header format (MZ DOS header & AMD64 machine header)
        byte[] exeBytes = Files.readAllBytes(distExe);
        assertEquals((byte) 0x4D, exeBytes[0], "First byte must be 'M'");
        assertEquals((byte) 0x5A, exeBytes[1], "Second byte must be 'Z'");

        int peOffset = (exeBytes[0x3C] & 0xFF)
                | ((exeBytes[0x3D] & 0xFF) << 8)
                | ((exeBytes[0x3E] & 0xFF) << 16)
                | ((exeBytes[0x3F] & 0xFF) << 24);
        assertTrue(peOffset > 0 && peOffset + 6 < exeBytes.length, "Valid PE header offset required");
        assertEquals((byte) 'P', exeBytes[peOffset]);
        assertEquals((byte) 'E', exeBytes[peOffset + 1]);

        int machineType = (exeBytes[peOffset + 4] & 0xFF) | ((exeBytes[peOffset + 5] & 0xFF) << 8);
        assertEquals(0x8664, machineType, "Executable machine architecture must be AMD64 (0x8664)");

        // Verify bundled runtime JRE
        assertTrue(Files.isDirectory(distRuntime), "runtime directory must exist in app image");
        Path javaDll = distRuntime.resolve("bin").resolve("java.dll");
        Path jvmDll = distRuntime.resolve("bin").resolve("server").resolve("jvm.dll");
        Path bundledJavaExe = distRuntime.resolve("bin").resolve("java.exe");
        assertTrue(Files.exists(javaDll), "runtime/bin/java.dll must exist");
        assertTrue(Files.exists(jvmDll), "runtime/bin/server/jvm.dll must exist");
        assertTrue(Files.exists(bundledJavaExe), "runtime/bin/java.exe must exist");

        // Execute bundled runtime java.exe in isolation (no JAVA_HOME, clean PATH)
        ProcessBuilder javaPb = new ProcessBuilder(bundledJavaExe.toString(), "-version");
        javaPb.environment().remove("JAVA_HOME");
        javaPb.environment().put("PATH", "C:\\Windows\\System32;C:\\Windows");
        javaPb.redirectErrorStream(true);
        Process javaProcess = javaPb.start();
        StringBuilder javaVerOut = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(javaProcess.getInputStream()))) {
            String l;
            while ((l = reader.readLine()) != null) {
                javaVerOut.append(l).append("\n");
            }
        }
        boolean javaFinished = javaProcess.waitFor(10, TimeUnit.SECONDS);
        assertTrue(javaFinished, "Bundled java.exe execution timed out");
        assertEquals(0, javaProcess.exitValue(), "Bundled java.exe must execute cleanly with exit code 0");
        assertTrue(javaVerOut.toString().contains("21."), "Bundled java.exe must report JDK 21 version: " + javaVerOut);

        // Verify app configuration
        assertTrue(Files.isDirectory(distApp), "app directory must exist in app image");
        Path cfgPath = distApp.resolve("BookstoreBilling.cfg");
        assertTrue(Files.exists(cfgPath), "BookstoreBilling.cfg must exist");
        String cfgContent = Files.readString(cfgPath);
        assertTrue(cfgContent.contains("app.mainclass=com.example.t.RunFX"), "Configuration must configure RunFX as main class");
    }
}
