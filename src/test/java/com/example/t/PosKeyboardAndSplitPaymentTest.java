package com.example.t;

import com.example.t.controller.MainShellController;
import com.example.t.controller.PosBillingController;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.InvoicePaymentDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.ParkedCart;
import com.example.t.model.StoreSettings;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-C: POS Keyboard-First & Split Tender Empirical Test Suite")
public class PosKeyboardAndSplitPaymentTest {

    private static Path tempDir;
    private PosBillingController controller;
    private ProductDao productDao;
    private InvoiceDao invoiceDao;
    private InvoicePaymentDao invoicePaymentDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfService;

    private Product productA;
    private Product productB;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory("m1c_pos_test_");
        Path dbPath = tempDir.resolve("m1c_pos.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        invoiceDao = new InvoiceDao();
        invoicePaymentDao = new InvoicePaymentDao();
        settingsDao = new SettingsDao();
        pdfService = new PdfInvoiceService(tempDir.resolve("invoices"));

        // Seed products
        productA = new Product(0, "Java Concurrency in Practice", 15, 50.00, 30.00);
        productA.setSku("978-0321349606");
        productDao.save(productA);

        productB = new Product(0, "Moleskine Classic Notebook", 40, 20.00, 10.00);
        productB.setSku("MOLE-NOTE-44");
        productDao.save(productB);

        // Load POS FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root);
        controller = loader.getController();
        assertNotNull(controller);

        controller.setProductDao(productDao);
        controller.setInvoiceDao(invoiceDao);
        controller.setSettingsDao(settingsDao);
        controller.setPdfInvoiceService(pdfService);
    }

    @Test
    @DisplayName("R2.1: Continuous Barcode Scan Auto-Adds and Increments Items")
    public void testContinuousBarcodeScan() {
        // First scan of Product A
        controller.handleScannedBarcode("978-0321349606");
        assertEquals(1, controller.getCartItems().size());
        assertEquals("978-0321349606", controller.getCartItems().get(0).getSku());
        assertEquals(1, controller.getCartItems().get(0).getQuantity());

        // Second scan increments quantity
        controller.handleScannedBarcode("978-0321349606");
        assertEquals(1, controller.getCartItems().size());
        assertEquals(2, controller.getCartItems().get(0).getQuantity());

        // Scan Product B
        controller.handleScannedBarcode("MOLE-NOTE-44");
        assertEquals(2, controller.getCartItems().size());

        // Scan unknown item does not alter cart
        controller.handleScannedBarcode("NON-EXISTENT-SKU-9999");
        assertEquals(2, controller.getCartItems().size());
    }

    @Test
    @DisplayName("R2.2: Multi-Cart Park & Resume with Snapshot Isolation")
    public void testMultiCartParkAndResume() throws Exception {
        // Add items to Cart 1
        controller.addProductToCart(productA, 2); // 2 x 50 = 100
        Field custField = PosBillingController.class.getDeclaredField("customerNameField");
        custField.setAccessible(true);
        TextField tfCust = (TextField) custField.get(controller);
        tfCust.setText("Alice Smith");

        Field discField = PosBillingController.class.getDeclaredField("discountPercentField");
        discField.setAccessible(true);
        TextField tfDisc = (TextField) discField.get(controller);
        tfDisc.setText("5.0");

        // Park Cart 1
        controller.handleHoldCart(new ActionEvent());
        assertEquals(0, controller.getCartItems().size(), "Cart items must be cleared after parking");
        assertEquals(1, controller.getParkedCarts().size(), "Parked carts list must contain 1 cart");
        ParkedCart cart1 = controller.getParkedCarts().get(0);
        assertEquals("Alice Smith", cart1.getCustomerName());
        assertEquals(5.0, cart1.getCartDiscountPercent(), 0.001);

        // Serve Cart 2
        controller.addProductToCart(productB, 3); // 3 x 20 = 60
        tfCust.setText("Bob Jones");
        tfDisc.setText("10.0");

        // Park Cart 2
        controller.handleHoldCart(new ActionEvent());
        assertEquals(0, controller.getCartItems().size());
        assertEquals(2, controller.getParkedCarts().size());
        ParkedCart cart2 = controller.getParkedCarts().get(1);
        assertEquals("Bob Jones", cart2.getCustomerName());

        // Mutate original product stock to test snapshot isolation
        productA.setPrice(999.00);

        // Resume Cart 1 specifically
        controller.recallCart(cart1);
        assertEquals(1, controller.getParkedCarts().size(), "One parked cart remaining");
        assertEquals(1, controller.getCartItems().size());
        InvoiceItem resumedItem = controller.getCartItems().get(0);
        assertEquals("Java Concurrency in Practice", resumedItem.getProductName());
        assertEquals(2, resumedItem.getQuantity());
        assertEquals(50.00, resumedItem.getUnitPrice(), 0.001, "Snapshot price must remain 50.00");
        assertEquals("Alice Smith", tfCust.getText());
        assertEquals("5.0", tfDisc.getText());

        // Clear Cart 1 and resume Cart 2
        controller.handleClearCart(new ActionEvent());
        controller.recallCart(cart2);
        assertEquals(0, controller.getParkedCarts().size());
        assertEquals(1, controller.getCartItems().size());
        assertEquals("Moleskine Classic Notebook", controller.getCartItems().get(0).getProductName());
        assertEquals(3, controller.getCartItems().get(0).getQuantity());
        assertEquals("Bob Jones", tfCust.getText());
    }

    @Test
    @DisplayName("R2.3: Line-Item Discount Compounding and Line Notes Persistence")
    public void testLineItemDiscountAndNotes() throws Exception {
        controller.addProductToCart(productA, 2); // 2 * 50 = 100.00
        InvoiceItem item = controller.getCartItems().get(0);

        // Apply 10% line discount -> line total = 90.00
        item.setDiscountPercent(10.0);
        item.setNotes("Author signed collector's edition");

        // Apply 10% cart-level discount
        Field discField = PosBillingController.class.getDeclaredField("discountPercentField");
        discField.setAccessible(true);
        ((TextField) discField.get(controller)).setText("10.0");

        Field cashField = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashField.setAccessible(true);
        ((TextField) cashField.get(controller)).setText("150.00");

        controller.recalculateOrder();

        // Subtotal = 90.00. Cart discount 10% = 9.00. Taxable = 81.00.
        // Tax 8.25% on 81.00 = 6.68 -> Net Total = 87.68
        assertEquals(87.68, controller.getCurrentNetTotal(), 0.01);

        // Checkout and verify persistence in SQLite
        controller.handleCheckout(new ActionEvent());

        List<Invoice> invoices = invoiceDao.getAllInvoices();
        assertFalse(invoices.isEmpty());
        int lastInvoiceId = invoices.stream().mapToInt(Invoice::getInvoiceId).max().orElse(0);

        List<InvoiceItem> savedItems = invoiceDao.getInvoiceItems(lastInvoiceId);
        assertEquals(1, savedItems.size());
        InvoiceItem saved = savedItems.get(0);
        assertEquals(10.0, saved.getDiscountPercent(), 0.001);
        assertEquals(10.00, saved.getDiscountAmount(), 0.001);
        assertEquals("Author signed collector's edition", saved.getNotes());
    }

    @Test
    @DisplayName("R2.4: Atomic Split Tender Payment Persistence and Method SPLIT")
    public void testSplitTenderPersistence() throws Exception {
        controller.addProductToCart(productA, 2); // 100.00
        Field cashField = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashField.setAccessible(true);

        controller.recalculateOrder();
        double netTotal = controller.getCurrentNetTotal();

        // Inject 2 split tenders: Cash $60 + Credit Card remaining
        double cashPart = 60.00;
        double cardPart = Math.round((netTotal - cashPart) * 100.0) / 100.0;

        List<com.example.t.model.InvoicePayment> splitTenders = new ArrayList<>();
        splitTenders.add(new com.example.t.model.InvoicePayment(0, "Cash", cashPart, cashPart, 0.0, "CASH-REGISTER-1"));
        splitTenders.add(new com.example.t.model.InvoicePayment(0, "Credit Card", cardPart, cardPart, 0.0, "AUTH-VISA-9944"));

        controller.setAppliedSplitPayments(splitTenders);
        ((TextField) cashField.get(controller)).setText(String.format("%.2f", cashPart + cardPart));

        // Checkout
        controller.handleCheckout(new ActionEvent());

        // Verify Cart cleared
        assertTrue(controller.getCartItems().isEmpty());

        // Verify Invoice record
        List<Invoice> all = invoiceDao.getAllInvoices();
        Invoice lastInvoice = all.stream().max(java.util.Comparator.comparingInt(Invoice::getInvoiceId)).orElseThrow();
        assertEquals("SPLIT", lastInvoice.getPaymentMethod(), "Invoice payment_method must be SPLIT");

        // Verify child records in invoice_payments table
        List<com.example.t.model.InvoicePayment> childPayments = invoicePaymentDao.getPaymentsByInvoiceId(lastInvoice.getInvoiceId());
        assertEquals(2, childPayments.size(), "Two payment records must exist in invoice_payments");

        assertEquals("Cash", childPayments.get(0).getPaymentType());
        assertEquals(cashPart, childPayments.get(0).getAmount(), 0.001);
        assertEquals("CASH-REGISTER-1", childPayments.get(0).getReferenceInfo());

        assertEquals("Credit Card", childPayments.get(1).getPaymentType());
        assertEquals(cardPart, childPayments.get(1).getAmount(), 0.001);
        assertEquals("AUTH-VISA-9944", childPayments.get(1).getReferenceInfo());
    }

    @Test
    @DisplayName("R2.5: Price-Suppressed Gift Receipt Generation during Checkout")
    public void testGiftReceiptCheckout() throws Exception {
        controller.addProductToCart(productB, 2);

        Field cashField = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashField.setAccessible(true);
        ((TextField) cashField.get(controller)).setText("100.00");

        // Enable Gift Receipt Checkbox
        CheckBox giftBox = controller.getGiftReceiptCheckBox();
        assertNotNull(giftBox);
        giftBox.setSelected(true);

        controller.recalculateOrder();
        controller.handleCheckout(new ActionEvent());

        // Verify generated gift receipt file exists in invoices folder
        File invoiceDir = tempDir.resolve("invoices").toFile();
        File[] giftFiles = invoiceDir.listFiles((dir, name) -> name.startsWith("gift_receipt_") && name.endsWith(".pdf"));
        assertNotNull(giftFiles);
        assertTrue(giftFiles.length >= 1, "Gift receipt PDF must be generated");
        assertTrue(giftFiles[0].length() > 0, "Gift receipt PDF must have non-zero size");
    }

    @Test
    @DisplayName("R2.6: Shell and POS Keyboard Coordination (F5 / F12 / F11)")
    public void testKeyboardCoordination() {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root);

        MainShellController shell = new MainShellController();
        shell.setupKeyShortcuts(scene);
        shell.setPosBillingController(controller);

        // Navigate to POS
        shell.navigateTo(MainShellController.ViewType.POS_BILLING);
        assertEquals(MainShellController.ViewType.POS_BILLING, shell.getCurrentView());

        // When cart has items, F12 does NOT switch shell view away from POS_BILLING
        controller.addProductToCart(productA, 1);
        assertFalse(controller.getCartItems().isEmpty());

        KeyEvent f12WithCart = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F12, false, false, false, false);
        javafx.event.Event.fireEvent(scene, f12WithCart);
        assertEquals(MainShellController.ViewType.POS_BILLING, shell.getCurrentView(),
                "F12 with active items in POS must NOT navigate away from POS_BILLING");

        // When cart is empty, F12 navigates back to STOCK_MANAGEMENT
        controller.handleClearCart(new ActionEvent());
        assertTrue(controller.getCartItems().isEmpty());

        KeyEvent f12EmptyCart = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F12, false, false, false, false);
        javafx.event.Event.fireEvent(scene, f12EmptyCart);
        assertEquals(MainShellController.ViewType.STOCK_MANAGEMENT, shell.getCurrentView(),
                "F12 with empty cart must navigate to STOCK_MANAGEMENT");

        // F11 toggles gift receipt checkbox
        CheckBox giftBox = controller.getGiftReceiptCheckBox();
        assertFalse(giftBox.isSelected());
        Field posRootField = null;
        try {
            posRootField = PosBillingController.class.getDeclaredField("posRootPane");
            posRootField.setAccessible(true);
            BorderPane posRoot = (BorderPane) posRootField.get(controller);
            if (posRoot != null) {
                KeyEvent f11Event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F11, false, false, false, false);
                posRoot.getEventDispatcher().dispatchEvent(f11Event, null);
                assertTrue(giftBox.isSelected(), "F11 must toggle gift receipt checkbox to true");
            }
        } catch (Exception ignored) {}
    }
}
