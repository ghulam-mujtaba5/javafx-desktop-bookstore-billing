package com.example.t;

import com.example.t.controller.PosBillingController;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4 - POS Billing Controller Tests")
public class PosBillingControllerTest {

    private static Path tempDir;
    private PosBillingController controller;
    private ProductDao productDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfService;

    private Product testBook;
    private Product testPen;

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
        tempDir = Files.createTempDirectory("m4_pos_test_");
        Path dbPath = tempDir.resolve("pos_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
        pdfService = new PdfInvoiceService(tempDir.resolve("invoices"));

        // Seed test catalog
        testBook = new Product(0, "Test Kotlin Book", 20, 30.00, 20.00);
        testBook.setSku("KT-BOOK-99");
        productDao.save(testBook);

        testPen = new Product(0, "Test Marker Pen", 50, 4.00, 2.00);
        testPen.setSku("MARKER-10");
        productDao.save(testPen);

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "PosBilling root node must load from FXML");
        controller = loader.getController();
        assertNotNull(controller, "PosBillingController must be injected by FXMLLoader");

        controller.setProductDao(productDao);
        controller.setInvoiceDao(invoiceDao);
        controller.setSettingsDao(settingsDao);
        controller.setPdfInvoiceService(pdfService);
    }

    @Test
    @DisplayName("M4.POS.1: Adding items to cart calculates correct line totals")
    public void testAddToCartAndLineTotal() {
        controller.addProductToCart(testBook, 2);

        assertEquals(1, controller.getCartItems().size());
        InvoiceItem item = controller.getCartItems().get(0);
        assertEquals("Test Kotlin Book", item.getProductName());
        assertEquals("KT-BOOK-99", item.getSku());
        assertEquals(2, item.getQuantity());
        assertEquals(30.00, item.getUnitPrice(), 0.001);
        assertEquals(60.00, item.getLineTotal(), 0.001);
    }

    @Test
    @DisplayName("M4.POS.2: Adding existing item increments quantity without duplicating row")
    public void testAddDuplicateIncrementsQuantity() {
        controller.addProductToCart(testBook, 2);
        controller.addProductToCart(testBook, 3);

        assertEquals(1, controller.getCartItems().size());
        assertEquals(5, controller.getCartItems().get(0).getQuantity());
        assertEquals(150.00, controller.getCartItems().get(0).getLineTotal(), 0.001);
    }

    @Test
    @DisplayName("M4.POS.3: Fast Barcode/SKU scan auto-adds item to cart on Enter")
    public void testBarcodeEnterAutoAdd() throws Exception {
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        searchField.setText("KT-BOOK-99");
        controller.handleSearchEnter(new ActionEvent());

        assertEquals(1, controller.getCartItems().size());
        assertEquals("KT-BOOK-99", controller.getCartItems().get(0).getSku());
        assertTrue(searchField.getText().isEmpty(), "Search field must be cleared after scan add");
    }

    @Test
    @DisplayName("M4.POS.4: Cart quantity adjustments and item removal")
    public void testCartItemQuantityAdjustments() {
        controller.addProductToCart(testBook, 2);
        InvoiceItem item = controller.getCartItems().get(0);

        // Increment
        item.setQuantity(item.getQuantity() + 1);
        controller.recalculateOrder();
        assertEquals(3, item.getQuantity());
        assertEquals(90.00, item.getLineTotal(), 0.001);

        // Decrement
        item.setQuantity(item.getQuantity() - 1);
        controller.recalculateOrder();
        assertEquals(2, item.getQuantity());
        assertEquals(60.00, item.getLineTotal(), 0.001);

        // Clear cart
        controller.handleClearCart(new ActionEvent());
        assertTrue(controller.getCartItems().isEmpty());
    }

    @Test
    @DisplayName("M4.POS.5: Pure math order calculation with discount, tax, and change due")
    public void testOrderCalculations() throws Exception {
        controller.addProductToCart(testBook, 2); // 2 * 30 = 60.00
        controller.addProductToCart(testPen, 5);  // 5 * 4 = 20.00
        // Subtotal = 80.00

        // Set 10% discount -> 8.00 discount, 72.00 taxable
        Field discFieldProp = PosBillingController.class.getDeclaredField("discountPercentField");
        discFieldProp.setAccessible(true);
        TextField discField = (TextField) discFieldProp.get(controller);
        discField.setText("10.0");

        // Tax 8.25% on 72.00 = 5.94 -> Net Total = 77.94
        Field cashFieldProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashFieldProp.setAccessible(true);
        TextField cashField = (TextField) cashFieldProp.get(controller);
        cashField.setText("100.00");

        controller.recalculateOrder();

        assertEquals(77.94, controller.getCurrentNetTotal(), 0.001);
        assertEquals(22.06, controller.getCurrentChangeDue(), 0.001);
    }

    @Test
    @DisplayName("M4.POS.6: Atomic Checkout decrements stock in SQLite and clears cart")
    public void testAtomicCheckout() throws Exception {
        int initialStockBook = productDao.findById(testBook.getProductId()).get().getQuantity();
        int initialStockPen = productDao.findById(testPen.getProductId()).get().getQuantity();

        controller.addProductToCart(testBook, 3);
        controller.addProductToCart(testPen, 10);

        // Tender cash
        Field cashFieldProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashFieldProp.setAccessible(true);
        TextField cashField = (TextField) cashFieldProp.get(controller);
        cashField.setText("200.00");

        controller.recalculateOrder();
        controller.handleCheckout(new ActionEvent());

        // Cart must be cleared
        assertTrue(controller.getCartItems().isEmpty(), "Cart must be cleared after successful checkout");

        // Stock in database must be decremented
        int postStockBook = productDao.findById(testBook.getProductId()).get().getQuantity();
        int postStockPen = productDao.findById(testPen.getProductId()).get().getQuantity();

        assertEquals(initialStockBook - 3, postStockBook, "Book stock must decrement by 3");
        assertEquals(initialStockPen - 10, postStockPen, "Pen stock must decrement by 10");

        // Invoice must be recorded in SQLite
        List<Invoice> invoices = invoiceDao.getAllInvoices();
        assertFalse(invoices.isEmpty(), "Invoice record must exist in SQLite");
    }

    @Test
    @DisplayName("M4.POS.7: Checkout fails and rolls back if quantity exceeds available stock")
    public void testCheckoutRollbackOnInsufficientStock() throws Exception {
        // testBook has stock 20. Try to order 25.
        // Direct inject items into cart bypass limit to test DAO atomic rollback
        InvoiceItem item = new InvoiceItem(testBook.getProductId(), testBook.getProductName(), testBook.getSku(), 25, testBook.getPrice());
        controller.getCartItems().add(item);

        Field cashFieldProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashFieldProp.setAccessible(true);
        TextField cashField = (TextField) cashFieldProp.get(controller);
        cashField.setText("1000.00");

        controller.recalculateOrder();
        controller.handleCheckout(new ActionEvent());

        // Stock must remain unchanged (still 20)
        int stockAfter = productDao.findById(testBook.getProductId()).get().getQuantity();
        assertEquals(20, stockAfter, "Stock must remain intact on rollback");
    }
}
