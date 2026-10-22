package com.example.t.dao;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.InvoicePayment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class InvoiceSplitPaymentDaoTest {

    @TempDir
    Path tempDir;

    private InvoiceDao invoiceDao;
    private InvoicePaymentDao paymentDao;
    private ProductDao productDao;

    @BeforeEach
    public void setUp() {
        Path dbPath = tempDir.resolve("test_split_dao.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();
        invoiceDao = new InvoiceDao();
        paymentDao = new InvoicePaymentDao();
        productDao = new ProductDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    @Test
    @DisplayName("Atomic creation of invoice with split tenders saves child records and updates header")
    public void testCreateInvoiceWithSplitPayments() throws SQLException {
        // Create 2 products with stock
        int prod1 = productDao.save(new Product(0, "Book Alpha", 10, 30.00, 30.00));
        int prod2 = productDao.save(new Product(0, "Notebook Beta", 20, 10.00, 10.00));

        // Line items with discounts and notes
        InvoiceItem item1 = new InvoiceItem(0, 0, prod1, "Book Alpha", "SKU-A", 1, 30.00, 27.00, 10.0, 3.00, "10% VIP author discount");
        InvoiceItem item2 = new InvoiceItem(0, 0, prod2, "Notebook Beta", "SKU-B", 2, 10.00, 20.00, 0.0, 0.0, "Gift wrapped");

        Invoice invoice = new Invoice(0, 0, "Split Customer", LocalDate.now(), List.of(),
                50.00, 0.0, 0.0, 47.00, 0.0, 0.0, 30.00, 3.00, "SPLIT", "USD", "USA");

        // Split payments: $20 Card + $30 Cash ($27 applied, $3 change)
        InvoicePayment p1 = new InvoicePayment(0, "CARD", 20.00, 20.00, 0.00, "AUTH-999");
        InvoicePayment p2 = new InvoicePayment(0, "CASH", 27.00, 30.00, 3.00, null);

        int invoiceId = invoiceDao.createInvoice(invoice, List.of(item1, item2), List.of(p1, p2));
        assertTrue(invoiceId > 0, "Invoice ID should be generated");

        // Verify stock decremented
        Product updatedP1 = productDao.findById(prod1).orElseThrow();
        Product updatedP2 = productDao.findById(prod2).orElseThrow();
        assertEquals(9, updatedP1.getQuantity(), "Stock for prod1 should be decremented from 10 to 9");
        assertEquals(18, updatedP2.getQuantity(), "Stock for prod2 should be decremented from 20 to 18");

        // Verify invoice header
        Optional<Invoice> fetched = invoiceDao.findInvoiceById(invoiceId);
        assertTrue(fetched.isPresent());
        Invoice inv = fetched.get();
        assertEquals("SPLIT", inv.getPaymentMethod());
        assertEquals(30.00, inv.getCashTendered(), 0.001);
        assertEquals(3.00, inv.getChangeDue(), 0.001);
        assertEquals(47.00, inv.getNetPrice(), 0.001);

        // Verify invoice items with discount and notes
        List<InvoiceItem> items = invoiceDao.getInvoiceItems(invoiceId);
        assertEquals(2, items.size());
        InvoiceItem fetchedItem1 = items.get(0);
        assertEquals(10.0, fetchedItem1.getDiscountPercent(), 0.001);
        assertEquals(3.00, fetchedItem1.getDiscountAmount(), 0.001);
        assertEquals("10% VIP author discount", fetchedItem1.getNotes());

        InvoiceItem fetchedItem2 = items.get(1);
        assertEquals(0.0, fetchedItem2.getDiscountPercent(), 0.001);
        assertEquals("Gift wrapped", fetchedItem2.getNotes());

        // Verify invoice payments child records
        try (Connection conn = DatabaseManager.getConnection()) {
            List<InvoicePayment> payments = paymentDao.getPaymentsByInvoiceId(invoiceId, conn);
            assertEquals(2, payments.size());
            assertEquals("CARD", payments.get(0).getPaymentType());
            assertEquals(20.00, payments.get(0).getAmount(), 0.001);
            assertEquals("AUTH-999", payments.get(0).getReferenceInfo());

            assertEquals("CASH", payments.get(1).getPaymentType());
            assertEquals(27.00, payments.get(1).getAmount(), 0.001);
            assertEquals(30.00, payments.get(1).getTenderedAmount(), 0.001);
            assertEquals(3.00, payments.get(1).getChangeDue(), 0.001);
        }
    }

    @Test
    @DisplayName("Legacy createInvoice without payments automatically persists default single payment")
    public void testCreateInvoiceLegacyAutoCreatesDefaultPayment() throws SQLException {
        int prodId = productDao.save(new Product(0, "Single Book", 5, 25.00, 25.00));
        InvoiceItem item = new InvoiceItem(0, 0, prodId, "Single Book", "SKU-S", 1, 25.00, 25.00);

        Invoice invoice = new Invoice(0, 0, "Cash Customer", LocalDate.now(), List.of(),
                25.00, 0.0, 0.0, 25.00, 0.0, 0.0, 30.00, 5.00, "CASH", "USD", "USA");

        int invoiceId = invoiceDao.createInvoice(invoice, List.of(item));

        try (Connection conn = DatabaseManager.getConnection()) {
            List<InvoicePayment> payments = paymentDao.getPaymentsByInvoiceId(invoiceId, conn);
            assertEquals(1, payments.size(), "Default payment should be automatically persisted");
            assertEquals("CASH", payments.get(0).getPaymentType());
            assertEquals(25.00, payments.get(0).getAmount(), 0.001);
            assertEquals(30.00, payments.get(0).getTenderedAmount(), 0.001);
            assertEquals(5.00, payments.get(0).getChangeDue(), 0.001);
        }
    }

    @Test
    @DisplayName("Transaction rollbacks on stock failure with zero orphaned invoices or payments")
    public void testRollbackOnInsufficientStock() throws SQLException {
        int prodId = productDao.save(new Product(0, "Low Stock Book", 1, 20.00, 20.00));

        // Request 5 units when only 1 is available
        InvoiceItem item = new InvoiceItem(0, 0, prodId, "Low Stock Book", "SKU-L", 5, 20.00, 100.00);
        Invoice invoice = new Invoice(0, 0, "Fail Customer", LocalDate.now(), List.of(),
                100.00, 0.0, 0.0, 100.00);

        int initialCount = invoiceDao.getAllInvoices().size();
        assertThrows(SQLException.class, () -> invoiceDao.createInvoice(invoice, List.of(item)));

        // Ensure stock untouched
        Product p = productDao.findById(prodId).orElseThrow();
        assertEquals(1, p.getQuantity());

        // Ensure zero new invoices created
        try {
            List<Invoice> all = invoiceDao.getAllInvoices();
            assertEquals(initialCount, all.size(), "No new invoice should exist after rollback");
        } catch (SQLException e) {
            fail("SQLException checking invoices: " + e.getMessage());
        }
    }
}
