package com.example.t;

import com.example.t.controller.InvoiceHistoryController;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4 - Invoice History & Reporting Controller Tests")
public class InvoiceHistoryControllerTest {

    private static Path tempDir;
    private InvoiceHistoryController controller;
    private InvoiceDao invoiceDao;
    private ProductDao productDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfService;

    private int invId1;
    private int invId2;

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
        tempDir = Files.createTempDirectory("m4_invoice_hist_test_");
        Path dbPath = tempDir.resolve("invoice_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        invoiceDao = new InvoiceDao();
        productDao = new ProductDao();
        settingsDao = new SettingsDao();
        pdfService = new PdfInvoiceService(tempDir.resolve("invoices"));

        // Create sample product
        Product book = new Product(0, "History of Science", 30, 45.00, 30.00);
        book.setSku("HOS-001");
        productDao.save(book);

        // Seed 2 invoices
        Invoice inv1 = new Invoice(0, 0, "Alice Walker", LocalDate.now().minusDays(2),
                Collections.emptyList(), 45.00, 0.0, 0.0, 48.71);
        InvoiceItem item1 = new InvoiceItem(book.getProductId(), book.getProductName(), book.getSku(), 1, 45.00);
        invId1 = invoiceDao.createInvoice(inv1, Collections.singletonList(item1));

        Invoice inv2 = new Invoice(0, 0, "Bob Dylan", LocalDate.now(),
                Collections.emptyList(), 90.00, 10.0, 9.00, 87.68);
        InvoiceItem item2 = new InvoiceItem(book.getProductId(), book.getProductName(), book.getSku(), 2, 45.00);
        invId2 = invoiceDao.createInvoice(inv2, Collections.singletonList(item2));

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/InvoiceHistory.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "InvoiceHistory root node must load from FXML");
        controller = loader.getController();
        assertNotNull(controller);

        controller.setInvoiceDao(invoiceDao);
        controller.setSettingsDao(settingsDao);
        controller.setPdfInvoiceService(pdfService);
    }

    @Test
    @DisplayName("M4.INV.1: Master invoice list loads all historical invoices")
    public void testMasterListLoadsInvoices() {
        assertTrue(controller.getMasterInvoices().size() >= 2);
    }

    @Test
    @DisplayName("M4.INV.2: Search filter filters by customer name")
    public void testFilterByCustomerName() throws Exception {
        Field searchFieldProp = InvoiceHistoryController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        searchField.setText("Alice");
        Field tableProp = InvoiceHistoryController.class.getDeclaredField("invoiceTable");
        tableProp.setAccessible(true);
        TableView<Invoice> table = (TableView<Invoice>) tableProp.get(controller);

        assertEquals(1, table.getItems().size());
        assertEquals("Alice Walker", table.getItems().get(0).getCustomerName());
    }

    @Test
    @DisplayName("M4.INV.3: Search filter filters by invoice ID")
    public void testFilterByInvoiceId() throws Exception {
        Field searchFieldProp = InvoiceHistoryController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(controller);

        searchField.setText(String.valueOf(invId2));
        Field tableProp = InvoiceHistoryController.class.getDeclaredField("invoiceTable");
        tableProp.setAccessible(true);
        TableView<Invoice> table = (TableView<Invoice>) tableProp.get(controller);

        assertEquals(1, table.getItems().size());
        assertEquals("Bob Dylan", table.getItems().get(0).getCustomerName());
    }

    @Test
    @DisplayName("M4.INV.4: Date range filter filters invoices accurately")
    public void testDateRangeFilter() throws Exception {
        Field fromProp = InvoiceHistoryController.class.getDeclaredField("fromDatePicker");
        fromProp.setAccessible(true);
        DatePicker fromPicker = (DatePicker) fromProp.get(controller);

        Field toProp = InvoiceHistoryController.class.getDeclaredField("toDatePicker");
        toProp.setAccessible(true);
        DatePicker toPicker = (DatePicker) toProp.get(controller);

        // Filter only today's invoices
        fromPicker.setValue(LocalDate.now());
        toPicker.setValue(LocalDate.now());
        controller.handleApplyDateFilter(new ActionEvent());

        Field tableProp = InvoiceHistoryController.class.getDeclaredField("invoiceTable");
        tableProp.setAccessible(true);
        TableView<Invoice> table = (TableView<Invoice>) tableProp.get(controller);

        assertEquals(1, table.getItems().size());
        assertEquals("Bob Dylan", table.getItems().get(0).getCustomerName());
    }

    @Test
    @DisplayName("M4.INV.5: Selecting an invoice loads line items in detail table")
    public void testSelectInvoiceLoadsDetails() throws Exception {
        Field tableProp = InvoiceHistoryController.class.getDeclaredField("invoiceTable");
        tableProp.setAccessible(true);
        TableView<Invoice> table = (TableView<Invoice>) tableProp.get(controller);

        // Select first invoice
        table.getSelectionModel().selectFirst();

        assertFalse(controller.getSelectedItems().isEmpty(), "Detail items must be loaded");
        assertEquals("History of Science", controller.getSelectedItems().get(0).getProductName());
    }

    @Test
    @DisplayName("M4.INV.6: Reprint PDF creates a new receipt file")
    public void testReprintPdf() throws Exception {
        Field tableProp = InvoiceHistoryController.class.getDeclaredField("invoiceTable");
        tableProp.setAccessible(true);
        TableView<Invoice> table = (TableView<Invoice>) tableProp.get(controller);

        table.getSelectionModel().selectFirst();
        controller.handleReprintPdf(new ActionEvent());

        // An invoice PDF should exist in the invoices dir
        Path invoicesDir = pdfService.getInvoicesDirectory();
        assertTrue(Files.list(invoicesDir).count() > 0, "Reprinted PDF must exist in invoices directory");
    }

    @Test
    @DisplayName("M4.INV.7: Export CSV produces valid RFC 4180 CSV export file")
    public void testExportCsv() throws IOException {
        controller.handleExportCsv(new ActionEvent());
        Path csvFile = DatabaseManager.getExportsDirectory().resolve("invoices_export.csv");
        assertTrue(Files.exists(csvFile));
        String content = Files.readString(csvFile);
        assertTrue(content.contains("Invoice ID,Invoice Date,Customer Name"));
        assertTrue(content.contains("Alice Walker"));
        assertTrue(content.contains("Bob Dylan"));
    }
}
