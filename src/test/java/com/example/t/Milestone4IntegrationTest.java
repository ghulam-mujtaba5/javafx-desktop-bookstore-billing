package com.example.t;

import com.example.t.controller.InvoiceHistoryController;
import com.example.t.controller.MainShellController;
import com.example.t.controller.PosBillingController;
import com.example.t.controller.SettingsController;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4 - Full POS Billing, Regional Settings & PDF Invoicing Integration Tests")
public class Milestone4IntegrationTest {

    private static Path tempDir;
    private ProductDao productDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfService;

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
        tempDir = Files.createTempDirectory("m4_integration_test_");
        Path dbPath = tempDir.resolve("m4_integration.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
        pdfService = new PdfInvoiceService(tempDir.resolve("invoices"));
    }

    @Test
    @DisplayName("M4.INT.1: End-to-End POS checkout -> SQLite persistence -> PDF receipt -> Invoice History inspection")
    public void testEndToEndCheckoutToHistoryFlow() throws Exception {
        // 1. Add product to catalog
        Product book = new Product(0, "JavaFX Desktop Architecture", 15, 50.00, 30.00);
        book.setSku("JFX-ARCH-01");
        productDao.save(book);

        // 2. Load POS Billing controller
        FXMLLoader posLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent posRoot = posLoader.load();
        PosBillingController posController = posLoader.getController();
        posController.setProductDao(productDao);
        posController.setInvoiceDao(invoiceDao);
        posController.setSettingsDao(settingsDao);
        posController.setPdfInvoiceService(pdfService);

        // 3. Scan/Add to cart
        posController.addProductToCart(book, 2); // 2 * 50 = 100.00 subtotal
        assertEquals(1, posController.getCartItems().size());

        // 4. Enter cash tendered
        Field cashProp = PosBillingController.class.getDeclaredField("cashTenderedField");
        cashProp.setAccessible(true);
        TextField cashField = (TextField) cashProp.get(posController);
        cashField.setText("120.00");

        posController.recalculateOrder();
        double netTotal = posController.getCurrentNetTotal();
        double changeDue = posController.getCurrentChangeDue();

        assertTrue(netTotal > 100.00, "Net total must include tax");
        assertTrue(changeDue > 0.00, "Change due must be positive");

        // 5. Complete checkout
        posController.handleCheckout(new ActionEvent());

        // 6. Verify inventory decremented in database
        int stockRemaining = productDao.findById(book.getProductId()).get().getQuantity();
        assertEquals(13, stockRemaining, "Stock must decrement from 15 to 13");

        // 7. Verify invoice in database
        List<Invoice> allInvoices = invoiceDao.getAllInvoices();
        assertFalse(allInvoices.isEmpty(), "Invoice record must exist in SQLite");
        Invoice createdInvoice = allInvoices.get(0);
        assertEquals(netTotal, createdInvoice.getNetPrice(), 0.001);

        // 8. Verify PDF generated
        Path invoicesDir = pdfService.getInvoicesDirectory();
        assertTrue(Files.list(invoicesDir).count() > 0, "PDF receipt must be generated in invoices directory");

        // 9. Open Invoice History and verify invoice is visible
        FXMLLoader invLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/InvoiceHistory.fxml"));
        Parent invRoot = invLoader.load();
        InvoiceHistoryController invController = invLoader.getController();
        invController.setInvoiceDao(invoiceDao);
        invController.setSettingsDao(settingsDao);
        invController.setPdfInvoiceService(pdfService);

        assertEquals(allInvoices.size(), invController.getMasterInvoices().size());
        assertFalse(invController.getSelectedItems().isEmpty(), "Detail view must show line items for invoice");
    }

    @Test
    @DisplayName("M4.INT.2: Regional preset switch (USA -> UAE) reflects in Settings, POS, and MainShell header")
    public void testRegionalPresetSwitchAcrossModules() throws Exception {
        // 1. Load Settings Controller
        FXMLLoader setLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/Settings.fxml"));
        Parent setRoot = setLoader.load();
        SettingsController setController = setLoader.getController();
        setController.setSettingsDao(settingsDao);

        // 2. Unlock settings
        Field passProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        passProp.setAccessible(true);
        ((PasswordField) passProp.get(setController)).setText("admin");
        setController.handleUnlock(new ActionEvent());
        assertTrue(setController.isUnlocked());

        // 3. Switch to UAE preset
        Field presetProp = SettingsController.class.getDeclaredField("presetComboBox");
        presetProp.setAccessible(true);
        ComboBox<String> presetCombo = (ComboBox<String>) presetProp.get(setController);
        presetCombo.setValue("UAE (AED د.إ, 5.0% VAT, Bilingual)");
        setController.handlePresetChange(new ActionEvent());

        setController.handleSaveSettings(new ActionEvent());

        // 4. Verify SQLite database reflects UAE
        StoreSettings updated = settingsDao.getSettings();
        assertEquals("GULF_UAE", updated.getRegionPreset());
        assertEquals("AED", updated.getCurrencyCode());
        assertEquals("د.إ", updated.getCurrencySymbol());
        assertEquals(5.00, updated.getTaxRate(), 0.001);

        // 5. Verify MainShellController header updates
        FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        Parent shellRoot = shellLoader.load();
        MainShellController shellController = shellLoader.getController();
        shellController.setSettingsDao(settingsDao);
        shellController.refreshHeader();

        assertTrue(shellController.getRegionPresetLabel().getText().contains("AED"));
        assertTrue(shellController.getRegionPresetLabel().getText().contains("د.إ"));
        assertTrue(shellController.getStatusBadgeLabel().getText().contains("5.0%"));
    }

    @Test
    @DisplayName("M4.INT.3: Seamless Master Shell keyboard navigation across all 4 screens")
    public void testShellKeyboardNavigation() throws Exception {
        FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        Parent shellRoot = shellLoader.load();
        MainShellController shellController = shellLoader.getController();
        shellController.setSettingsDao(settingsDao);

        // Navigate to POS
        shellController.navigateTo(MainShellController.ViewType.POS_BILLING);
        assertEquals(MainShellController.ViewType.POS_BILLING, shellController.getCurrentView());
        assertTrue(shellController.getNavPosBtn().getStyleClass().contains("active"));

        // Navigate to Invoice History
        shellController.navigateTo(MainShellController.ViewType.INVOICE_HISTORY);
        assertEquals(MainShellController.ViewType.INVOICE_HISTORY, shellController.getCurrentView());
        assertTrue(shellController.getNavInvoicesBtn().getStyleClass().contains("active"));

        // Navigate to Store Settings
        shellController.navigateTo(MainShellController.ViewType.STORE_SETTINGS);
        assertEquals(MainShellController.ViewType.STORE_SETTINGS, shellController.getCurrentView());
        assertTrue(shellController.getNavSettingsBtn().getStyleClass().contains("active"));

        // Navigate back to Stock Management
        shellController.navigateTo(MainShellController.ViewType.STOCK_MANAGEMENT);
        assertEquals(MainShellController.ViewType.STOCK_MANAGEMENT, shellController.getCurrentView());
        assertTrue(shellController.getNavStockBtn().getStyleClass().contains("active"));
    }
}
