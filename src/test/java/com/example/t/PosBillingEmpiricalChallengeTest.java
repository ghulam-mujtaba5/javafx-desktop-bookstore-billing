package com.example.t;

import com.example.t.controller.PosBillingController;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Harness for Milestone 4 POS Billing & Checkout:
 * 1. Edge cases in cart math and BillingService (zero quantity, max integer quantity, fractional amounts,
 *    100% discount, zero tax rate, invalid inputs).
 * 2. Barcode lookup resilience (empty/whitespace, non-existent, SQL injection, regex/special chars, whitespace trimming).
 * 3. Atomic stock decrement & database rollback verification (multi-item partial failure rollback,
 *    exact boundary decrement to zero, non-existent product IDs, insufficient cash tender barrier).
 * 4. Multi-regional calculation matrix and persisted invoice item line totals integrity.
 */
@DisplayName("Milestone 4 - POS Billing & Checkout Empirical Challenge & Adversarial Test Suite")
public class PosBillingEmpiricalChallengeTest {

    private static Path tempDir;
    private PosBillingController controller;
    private ProductDao productDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfService;

    private Product testItemA;
    private Product testItemB;
    private Product testItemC;

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
        tempDir = Files.createTempDirectory("m4_empirical_pos_");
        Path dbPath = tempDir.resolve("pos_challenge.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
        pdfService = new PdfInvoiceService(tempDir.resolve("invoices"));

        // Seed distinct test inventory
        testItemA = new Product(0, "Challenge Book Alpha", 10, 25.00, 15.00);
        testItemA.setSku("CHAL-ALPHA-01");
        productDao.save(testItemA);

        testItemB = new Product(0, "Challenge Pen Beta", 2, 5.00, 2.50);
        testItemB.setSku("CHAL-BETA-02");
        productDao.save(testItemB);

        testItemC = new Product(0, "Challenge Notebook Gamma", 3, 10.00, 6.00);
        testItemC.setSku("CHAL-GAMMA-03");
        productDao.save(testItemC);

        // Load POS Billing FXML & Controller
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "PosBilling FXML must load cleanly");
        controller = loader.getController();
        assertNotNull(controller, "PosBillingController must be instantiated");

        controller.setProductDao(productDao);
        controller.setInvoiceDao(invoiceDao);
        controller.setSettingsDao(settingsDao);
        controller.setPdfInvoiceService(pdfService);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    private int countTableRows(String tableName) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    // =========================================================================
    // SECTION 1: Cart Math & BillingService Edge Cases
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 1.1: Zero quantity in OrderItem yields 0.00 line subtotal without errors")
    public void testOrderItemZeroQuantity() {
        BillingService.OrderItem item = new BillingService.OrderItem(
                testItemA.getProductId(), testItemA.getProductName(), "Books", 0, testItemA.getPrice(), testItemA.getSku()
        );
        assertEquals(0, item.getQuantity());
        assertEquals(0.00, item.getLineSubtotal(), 0.001);

        BillingService.CalculationResult res = BillingService.calculate(
                List.of(item), 0.0, 10.0, 50.0
        );
        assertEquals(0.00, res.getSubtotal(), 0.001);
        assertEquals(0.00, res.getDiscountAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
        assertEquals(50.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("CHALLENGE 1.2: Extreme quantity (Integer.MAX_VALUE) calculates without overflow crash")
    public void testBillingServiceMaxIntegerQuantity() {
        BillingService.OrderItem extremeItem = new BillingService.OrderItem(
                1, "Extreme Item", "General", Integer.MAX_VALUE, 1.00, "EXTREME-01"
        );
        assertEquals(Integer.MAX_VALUE, extremeItem.getQuantity());

        // Subtotal = 2147483647.00
        BillingService.CalculationResult res = BillingService.calculate(
                List.of(extremeItem), 10.0, 5.0, 2500000000.00
        );

        assertEquals(2147483647.00, res.getSubtotal(), 0.01);
        // 10% discount = 214748364.70
        assertEquals(214748364.70, res.getDiscountAmount(), 0.01);
        // Taxable = 1932735282.30
        assertEquals(1932735282.30, res.getTaxableAmount(), 0.01);
        // Tax 5% = 96636764.12
        assertEquals(96636764.12, res.getTaxAmount(), 0.01);
        // Net total = 2029372046.42
        assertEquals(2029372046.42, res.getNetTotal(), 0.01);
        assertTrue(res.isCashSufficient());
    }

    @Test
    @DisplayName("CHALLENGE 1.3: Fractional amounts with Half-Up Rounding across multi-item calculations")
    public void testBillingServiceFractionalAmountsAndHalfUpRounding() {
        // Test roundMoney boundary cases
        assertEquals(0.01, BillingService.roundMoney(0.005), 0.0001, "0.005 must round up to 0.01");
        assertEquals(0.00, BillingService.roundMoney(0.004), 0.0001, "0.004 must round down to 0.00");
        assertEquals(10.56, BillingService.roundMoney(10.555), 0.0001, "10.555 must round up to 10.56");
        assertEquals(19.99, BillingService.roundMoney(19.994), 0.0001, "19.994 must round down to 19.99");

        // Multi-item order with fractional cents
        BillingService.OrderItem item1 = new BillingService.OrderItem(1, "Item 1", "", 3, 0.3333, "SKU1"); // 0.9999 -> raw sum
        BillingService.OrderItem item2 = new BillingService.OrderItem(2, "Item 2", "", 1, 10.555, "SKU2"); // 10.555
        // Raw subtotal = 0.9999 + 10.555 = 11.5549 -> rounded subtotal = 11.55
        // Discount 15.5% = 11.55 * 0.155 = 1.79025 -> rounded = 1.79
        // Taxable = 11.55 - 1.79 = 9.76
        // Tax 8.25% = 9.76 * 0.0825 = 0.8052 -> rounded = 0.81
        // Net Total = 9.76 + 0.81 = 10.57
        BillingService.CalculationResult res = BillingService.calculate(
                List.of(item1, item2), 15.5, 8.25, 20.00
        );

        assertEquals(11.55, res.getSubtotal(), 0.001);
        assertEquals(1.79, res.getDiscountAmount(), 0.001);
        assertEquals(9.76, res.getTaxableAmount(), 0.001);
        assertEquals(0.81, res.getTaxAmount(), 0.001);
        assertEquals(10.57, res.getNetTotal(), 0.001);
        assertEquals(9.43, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("CHALLENGE 1.4: 100% discount reduces net total and tax to zero")
    public void testBillingService100PercentDiscount() {
        BillingService.OrderItem item = new BillingService.OrderItem(
                testItemA.getProductId(), testItemA.getProductName(), "Books", 2, 25.00, testItemA.getSku()
        ); // Subtotal = 50.00

        BillingService.CalculationResult res = BillingService.calculate(
                List.of(item), 100.0, 15.0, 0.00
        );

        assertEquals(50.00, res.getSubtotal(), 0.001);
        assertEquals(50.00, res.getDiscountAmount(), 0.001);
        assertEquals(0.00, res.getTaxableAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(0.00, res.getNetTotal(), 0.001);
        assertEquals(0.00, res.getChangeDue(), 0.001);
        assertTrue(res.isCashSufficient(), "Zero net total is covered by zero cash tendered");
    }

    @Test
    @DisplayName("CHALLENGE 1.5: Zero tax rate (e.g. Qatar preset) yields 0.00 tax amount")
    public void testBillingServiceZeroTaxRate() {
        BillingService.OrderItem item = new BillingService.OrderItem(
                testItemA.getProductId(), testItemA.getProductName(), "Books", 4, 25.00, testItemA.getSku()
        ); // Subtotal = 100.00

        BillingService.CalculationResult res = BillingService.calculate(
                List.of(item), 10.0, 0.0, 100.00
        );

        assertEquals(100.00, res.getSubtotal(), 0.001);
        assertEquals(10.00, res.getDiscountAmount(), 0.001);
        assertEquals(90.00, res.getTaxableAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001, "Tax amount must be zero when tax rate is 0%");
        assertEquals(90.00, res.getNetTotal(), 0.001, "Net total must equal taxable amount when tax rate is 0%");
        assertEquals(10.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("CHALLENGE 1.6: BillingService rejects invalid/negative inputs with IllegalArgumentException")
    public void testBillingServiceValidationRejections() {
        List<BillingService.OrderItem> items = List.of(
                new BillingService.OrderItem(1, "Item", "", 1, 10.00, "SKU")
        );

        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(items, -1.0, 5.0, 20.0), "Negative discount must throw");

        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(items, 100.01, 5.0, 20.0), "Discount > 100 must throw");

        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(items, 10.0, -0.5, 20.0), "Negative tax rate must throw");

        assertThrows(IllegalArgumentException.class, () ->
                BillingService.calculate(items, 10.0, 5.0, -5.0), "Negative cash tendered must throw");

        assertThrows(IllegalArgumentException.class, () ->
                new BillingService.OrderItem(1, "Item", "", -1, 10.00, "SKU"), "Negative quantity must throw");

        assertThrows(IllegalArgumentException.class, () ->
                new BillingService.OrderItem(1, "Item", "", 1, -10.00, "SKU"), "Negative price must throw");
    }

    @Test
    @DisplayName("CHALLENGE 1.7: Adding 0 or negative quantity to POS cart is safely ignored")
    public void testPosControllerCartZeroOrNegativeQuantityAddRejected() {
        controller.addProductToCart(testItemA, 0);
        assertTrue(controller.getCartItems().isEmpty(), "Adding 0 quantity must not alter cart");

        controller.addProductToCart(testItemA, -5);
        assertTrue(controller.getCartItems().isEmpty(), "Adding negative quantity must not alter cart");
    }

    @Test
    @DisplayName("CHALLENGE 1.8: Malformed user input in POS discount and cash fields is clamped/handled safely")
    public void testPosControllerMalformedInputResilience() throws Exception {
        controller.addProductToCart(testItemA, 1); // 25.00

        Field discFieldProp = PosBillingController.class.getDeclaredField("discountPercentField");
        discFieldProp.setAccessible(true);
        TextField discField = (TextField) discFieldProp.get(controller);

        Field cashFieldProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashFieldProp.setAccessible(true);
        TextField cashField = (TextField) cashFieldProp.get(controller);

        // Feed invalid text strings
        discField.setText("INVALID_TEXT");
        cashField.setText("ANOTHER_BAD_INPUT");
        assertDoesNotThrow(() -> controller.recalculateOrder(), "Malformed input should not crash recalculateOrder");
        assertTrue(controller.getCurrentNetTotal() > 0.0, "Net total should calculate using default values");

        // Feed out-of-range discount
        discField.setText("-20.0");
        controller.recalculateOrder();
        // Negative discount should be clamped to 0.0
        assertTrue(controller.getCurrentNetTotal() >= 25.0, "Negative discount should be clamped to 0%");

        discField.setText("999.0");
        controller.recalculateOrder();
        // Discount > 100 should be clamped to 100%
        assertEquals(0.0, controller.getCurrentNetTotal(), 0.001, "Discount > 100% should be clamped to 100%");
    }

    // =========================================================================
    // SECTION 2: Barcode Lookup & Search Resilience
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 2.1: Barcode lookup with empty, whitespace, or null text does not alter cart")
    public void testBarcodeLookupEmptyAndWhitespaceIgnored() throws Exception {
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        searchField.setText("");
        controller.handleSearchEnter(new ActionEvent());
        assertTrue(controller.getCartItems().isEmpty());

        searchField.setText("     ");
        controller.handleSearchEnter(new ActionEvent());
        assertTrue(controller.getCartItems().isEmpty());

        searchField.setText(null);
        controller.handleSearchEnter(new ActionEvent());
        assertTrue(controller.getCartItems().isEmpty());
    }

    @Test
    @DisplayName("CHALLENGE 2.2: Barcode lookup with non-existent barcode does not alter cart or crash")
    public void testBarcodeLookupNonExistentSkuDoesNotAdd() throws Exception {
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        searchField.setText("NON_EXISTENT_BARCODE_888888");
        controller.handleSearchEnter(new ActionEvent());

        assertTrue(controller.getCartItems().isEmpty(), "Non-existent barcode must not add any item to cart");
    }

    @Test
    @DisplayName("CHALLENGE 2.3: SQL Injection payloads in barcode input are safely neutralized")
    public void testBarcodeLookupSqlInjectionSafe() throws Exception {
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        String[] payloads = {
                "' OR '1'='1' --",
                "'; DROP TABLE products; --",
                "1' UNION SELECT null, null, null, null, null, null, null, null, null, null, null --",
                "admin' --",
                "' OR 1=1#"
        };

        for (String payload : payloads) {
            searchField.setText(payload);
            assertDoesNotThrow(() -> controller.handleSearchEnter(new ActionEvent()),
                    "SQL injection payload must execute safely via prepared statements: " + payload);
            assertTrue(controller.getCartItems().isEmpty(), "SQL injection payload must not add any cart items");
        }

        // Verify database products table is fully intact
        List<Product> products = productDao.getAllProducts();
        assertFalse(products.isEmpty(), "Products table must remain intact after SQL injection attempts");
    }

    @Test
    @DisplayName("CHALLENGE 2.4: Regex meta-characters and HTML in barcode search do not crash")
    public void testBarcodeLookupRegexAndSpecialCharactersSafe() throws Exception {
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        String[] specialInputs = {
                ".*",
                "[a-z]+",
                "(",
                ")",
                "[",
                "]",
                "{",
                "}",
                "<script>alert(1)</script>",
                "!@#$%^&*()_+=-~`",
                "العربية باركود"
        };

        for (String input : specialInputs) {
            searchField.setText(input);
            assertDoesNotThrow(() -> controller.handleSearchEnter(new ActionEvent()),
                    "Special characters must not throw PatternSyntaxException: " + input);
        }
    }

    @Test
    @DisplayName("CHALLENGE 2.5: Barcode lookup trims leading/trailing whitespace and matches exact SKU")
    public void testBarcodeLookupWhitespaceTrimming() throws Exception {
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        searchField.setText("   CHAL-ALPHA-01   ");
        controller.handleSearchEnter(new ActionEvent());

        assertEquals(1, controller.getCartItems().size(), "Trimmed barcode must match testItemA");
        assertEquals("CHAL-ALPHA-01", controller.getCartItems().get(0).getSku());
        assertTrue(searchField.getText().isEmpty(), "Search field must be cleared after successful match");
    }

    // =========================================================================
    // SECTION 3: Atomic Stock Decrement & Transaction Rollback Verification
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 3.1: Atomic checkout rollbacks all decrements when second item has insufficient stock")
    public void testAtomicCheckoutRollbackWhenSecondItemExceedsStock() throws Exception {
        // testItemA has stock 10; we order 4 (valid)
        // testItemB has stock 2; we order 5 (invalid - exceeds stock by 3)
        int initialStockA = productDao.findById(testItemA.getProductId()).get().getQuantity();
        int initialStockB = productDao.findById(testItemB.getProductId()).get().getQuantity();
        int initialInvoiceCount = countTableRows("invoices");
        int initialItemCount = countTableRows("invoice_items");

        assertEquals(10, initialStockA);
        assertEquals(2, initialStockB);

        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(testItemA.getProductId(), testItemA.getProductName(), testItemA.getSku(), 4, testItemA.getPrice()));
        items.add(new InvoiceItem(testItemB.getProductId(), testItemB.getProductName(), testItemB.getSku(), 5, testItemB.getPrice()));

        com.example.t.Invoice invoice = new com.example.t.Invoice(
                0, 0, "Adversarial Customer", java.time.LocalDate.now(), new ArrayList<>(),
                125.00, 0.0, 0.0, 125.00
        );

        // Must throw SQLException due to insufficient stock for testItemB
        SQLException thrown = assertThrows(SQLException.class, () -> invoiceDao.createInvoice(invoice, items));
        assertTrue(thrown.getMessage().contains("Insufficient stock"), "Exception message must state insufficient stock");

        // EMPIRICAL VERIFICATION IN SQLITE:
        // 1. Item A stock must NOT be decremented to 6; must remain 10!
        int postStockA = productDao.findById(testItemA.getProductId()).get().getQuantity();
        assertEquals(initialStockA, postStockA, "Item A stock MUST rollback to original count (10)");

        // 2. Item B stock must remain 2
        int postStockB = productDao.findById(testItemB.getProductId()).get().getQuantity();
        assertEquals(initialStockB, postStockB, "Item B stock MUST remain unchanged (2)");

        // 3. No partial invoice header inserted
        int postInvoiceCount = countTableRows("invoices");
        assertEquals(initialInvoiceCount, postInvoiceCount, "No invoice record must be committed to SQLite on rollback");

        // 4. No line items inserted
        int postItemCount = countTableRows("invoice_items");
        assertEquals(initialItemCount, postItemCount, "No invoice items must be committed to SQLite on rollback");
    }

    @Test
    @DisplayName("CHALLENGE 3.2: Exact stock boundary decrement to 0 succeeds; subsequent decrement rolls back")
    public void testExactStockBoundaryDecrementToZeroAndSubsequentRejection() throws Exception {
        // testItemC has stock 3; order exactly 3
        int initialStockC = productDao.findById(testItemC.getProductId()).get().getQuantity();
        assertEquals(3, initialStockC);

        List<InvoiceItem> itemsOrder1 = List.of(
                new InvoiceItem(testItemC.getProductId(), testItemC.getProductName(), testItemC.getSku(), 3, testItemC.getPrice())
        );
        com.example.t.Invoice invoice1 = new com.example.t.Invoice(
                0, 0, "Boundary Customer", java.time.LocalDate.now(), new ArrayList<>(),
                30.00, 0.0, 0.0, 30.00
        );

        int invoiceId1 = invoiceDao.createInvoice(invoice1, itemsOrder1);
        assertTrue(invoiceId1 > 0, "Checkout of exact available stock must succeed");

        // Stock must now be exactly 0
        int stockAfterOrder1 = productDao.findById(testItemC.getProductId()).get().getQuantity();
        assertEquals(0, stockAfterOrder1, "Stock must decrement to exactly 0");

        // Attempt second order of 1 item when stock is 0
        List<InvoiceItem> itemsOrder2 = List.of(
                new InvoiceItem(testItemC.getProductId(), testItemC.getProductName(), testItemC.getSku(), 1, testItemC.getPrice())
        );
        com.example.t.Invoice invoice2 = new com.example.t.Invoice(
                0, 0, "Second Customer", java.time.LocalDate.now(), new ArrayList<>(),
                10.00, 0.0, 0.0, 10.00
        );

        assertThrows(SQLException.class, () -> invoiceDao.createInvoice(invoice2, itemsOrder2),
                "Ordering when stock is 0 must fail and rollback");

        // Stock must remain 0
        int stockAfterOrder2 = productDao.findById(testItemC.getProductId()).get().getQuantity();
        assertEquals(0, stockAfterOrder2, "Stock must remain 0 without negative underflow");
    }

    @Test
    @DisplayName("CHALLENGE 3.3: Ordering non-existent product ID causes transaction rollback")
    public void testAtomicCheckoutRollbackOnNonExistentProductId() throws Exception {
        int initialInvoices = countTableRows("invoices");

        List<InvoiceItem> items = List.of(
                new InvoiceItem(999999, "Ghost Product", "SKU-GHOST", 1, 50.00)
        );
        com.example.t.Invoice invoice = new com.example.t.Invoice(
                0, 0, "Ghost Buyer", java.time.LocalDate.now(), new ArrayList<>(),
                50.00, 0.0, 0.0, 50.00
        );

        assertThrows(SQLException.class, () -> invoiceDao.createInvoice(invoice, items));
        assertEquals(initialInvoices, countTableRows("invoices"), "No invoice record created for non-existent product");
    }

    @Test
    @DisplayName("CHALLENGE 3.4: ProductDao.decrementStock rejects zero or negative quantity with IllegalArgumentException")
    public void testProductDaoDecrementStockNegativeOrZeroThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                productDao.decrementStock(testItemA.getProductId(), 0));

        assertThrows(IllegalArgumentException.class, () ->
                productDao.decrementStock(testItemA.getProductId(), -3));
    }

    @Test
    @DisplayName("CHALLENGE 3.5: Cash tender less than grand total blocks checkout and preserves cart")
    public void testPosControllerCashTenderInsufficientBlocksCheckout() throws Exception {
        controller.addProductToCart(testItemA, 2); // 2 * 25.00 = 50.00

        Field cashProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashProp.setAccessible(true);
        TextField cashField = (TextField) cashProp.get(controller);
        cashField.setText("10.00"); // Tender $10 for >$50 bill

        controller.recalculateOrder();
        int initialInvoices = countTableRows("invoices");

        // Attempt checkout
        controller.handleCheckout(new ActionEvent());

        // Verification:
        // Cart must NOT be cleared
        assertEquals(1, controller.getCartItems().size(), "Cart items must remain in cart when cash is insufficient");
        // No invoice created
        assertEquals(initialInvoices, countTableRows("invoices"), "No invoice must be created when cash is insufficient");
        // Stock must not be decremented
        int currentStock = productDao.findById(testItemA.getProductId()).get().getQuantity();
        assertEquals(10, currentStock, "Stock must remain untouched");
    }

    @Test
    @DisplayName("CHALLENGE 3.6: Controller handles stock rollback gracefully without crashing UI state")
    public void testPosControllerStockRollbackGracefulHandling() throws Exception {
        // Manually place item in cart whose quantity exceeds stock
        InvoiceItem item = new InvoiceItem(testItemB.getProductId(), testItemB.getProductName(), testItemB.getSku(), 10, testItemB.getPrice());
        controller.getCartItems().add(item);

        Field cashProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashProp.setAccessible(true);
        TextField cashField = (TextField) cashProp.get(controller);
        cashField.setText("500.00");

        controller.recalculateOrder();
        assertDoesNotThrow(() -> controller.handleCheckout(new ActionEvent()),
                "Controller checkout error handling must not throw unhandled runtime exceptions");

        // Stock in DB should remain intact
        assertEquals(2, productDao.findById(testItemB.getProductId()).get().getQuantity());
    }

    // =========================================================================
    // SECTION 4: Multi-Regional Presets & Invoice Line Item Integrity
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 4.1: Multi-Regional tax and currency calculations adhere to regional matrices")
    public void testMultiRegionalPresetFinancialMatrix() {
        List<BillingService.OrderItem> items = List.of(
                new BillingService.OrderItem(1, "Item A", "Stationery", 2, 45.50, "A1"), // 91.00
                new BillingService.OrderItem(2, "Item B", "Books", 1, 9.00, "B1")        // 9.00
        ); // Subtotal = 100.00

        // 1. USA: 8.25% Sales Tax, 10% discount -> taxable = 90.00, tax = 7.43, total = 97.43
        BillingService.CalculationResult usa = BillingService.calculateForRegion(
                items, 10.0, RegionalPreset.USA, 100.00
        );
        assertEquals(100.00, usa.getSubtotal(), 0.001);
        assertEquals(10.00, usa.getDiscountAmount(), 0.001);
        assertEquals(90.00, usa.getTaxableAmount(), 0.001);
        assertEquals(7.43, usa.getTaxAmount(), 0.001);
        assertEquals(97.43, usa.getNetTotal(), 0.001);
        assertEquals(2.57, usa.getChangeDue(), 0.001);

        // 2. UK: 20.0% VAT, 10% discount -> taxable = 90.00, tax = 18.00, total = 108.00
        BillingService.CalculationResult uk = BillingService.calculateForRegion(
                items, 10.0, RegionalPreset.UK, 120.00
        );
        assertEquals(18.00, uk.getTaxAmount(), 0.001);
        assertEquals(108.00, uk.getNetTotal(), 0.001);
        assertEquals(12.00, uk.getChangeDue(), 0.001);

        // 3. UAE: 5.0% VAT, 10% discount -> taxable = 90.00, tax = 4.50, total = 94.50
        BillingService.CalculationResult uae = BillingService.calculateForRegion(
                items, 10.0, RegionalPreset.GULF_UAE, 100.00
        );
        assertEquals(4.50, uae.getTaxAmount(), 0.001);
        assertEquals(94.50, uae.getNetTotal(), 0.001);
        assertEquals(5.50, uae.getChangeDue(), 0.001);

        // 4. Saudi: 15.0% VAT, 10% discount -> taxable = 90.00, tax = 13.50, total = 103.50
        BillingService.CalculationResult saudi = BillingService.calculateForRegion(
                items, 10.0, RegionalPreset.GULF_SAUDI, 110.00
        );
        assertEquals(13.50, saudi.getTaxAmount(), 0.001);
        assertEquals(103.50, saudi.getNetTotal(), 0.001);
        assertEquals(6.50, saudi.getChangeDue(), 0.001);

        // 5. Qatar: 0.0% VAT, 10% discount -> taxable = 90.00, tax = 0.00, total = 90.00
        BillingService.CalculationResult qatar = BillingService.calculateForRegion(
                items, 10.0, RegionalPreset.GULF_QATAR, 100.00
        );
        assertEquals(0.00, qatar.getTaxAmount(), 0.001);
        assertEquals(90.00, qatar.getNetTotal(), 0.001);
        assertEquals(10.00, qatar.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("CHALLENGE 4.2: Persisted invoice line items reflect exact quantities, prices, and line total sums")
    public void testInvoiceDetailPersistedIntegrity() throws Exception {
        List<InvoiceItem> cart = List.of(
                new InvoiceItem(testItemA.getProductId(), testItemA.getProductName(), testItemA.getSku(), 2, testItemA.getPrice()), // 50.00
                new InvoiceItem(testItemB.getProductId(), testItemB.getProductName(), testItemB.getSku(), 1, testItemB.getPrice()), // 5.00
                new InvoiceItem(testItemC.getProductId(), testItemC.getProductName(), testItemC.getSku(), 2, testItemC.getPrice())  // 20.00
        );
        double expectedSubtotal = 75.00;

        com.example.t.Invoice invoice = new com.example.t.Invoice(
                0, 0, "Integrity Auditor", java.time.LocalDate.now(), new ArrayList<>(),
                expectedSubtotal, 0.0, 0.0, expectedSubtotal
        );

        int invoiceId = invoiceDao.createInvoice(invoice, cart);
        assertTrue(invoiceId > 0);

        List<InvoiceItem> persistedItems = invoiceDao.getInvoiceItems(invoiceId);
        assertEquals(3, persistedItems.size(), "All 3 line items must be persisted");

        double sumOfLineTotals = 0.0;
        for (InvoiceItem item : persistedItems) {
            sumOfLineTotals += item.getLineTotal();
            assertTrue(item.getQuantity() > 0);
            assertTrue(item.getUnitPrice() > 0);
            assertNotNull(item.getProductName());
            assertNotNull(item.getSku());
        }

        assertEquals(expectedSubtotal, sumOfLineTotals, 0.001, "Sum of persisted line totals must equal order subtotal");
    }
}
