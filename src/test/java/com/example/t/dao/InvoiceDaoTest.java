package com.example.t.dao;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - InvoiceDao Unit Tests")
public class InvoiceDaoTest {

    @TempDir
    Path tempDir;

    private InvoiceDao invoiceDao;
    private ProductDao productDao;

    @BeforeEach
    public void setUp() {
        Path testDb = tempDir.resolve("invoice_test.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();
        invoiceDao = new InvoiceDao();
        productDao = new ProductDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Atomic Invoice Creation with Stock Decrement and Line Items")
    public void testCreateInvoiceAtomicSuccess() throws SQLException {
        Product book = productDao.findBySku("BK-001").orElseThrow();
        Product notebook = productDao.findBySku("ST-001").orElseThrow();

        int initialBookStock = book.getQuantity();
        int initialNotebookStock = notebook.getQuantity();

        InvoiceItem item1 = new InvoiceItem(book.getProductId(), book.getProductName(), book.getSku(), 2, 35.00);
        InvoiceItem item2 = new InvoiceItem(notebook.getProductId(), notebook.getProductName(), notebook.getSku(), 5, 4.50);

        // Subtotal = 2*35 (70) + 5*4.50 (22.50) = 92.50
        Invoice inv = new Invoice(0, 0, "Dr. John Watson", LocalDate.now(), null, 92.50, 0.0, 0.0, 92.50);

        int invoiceId = invoiceDao.createInvoice(inv, Arrays.asList(item1, item2));
        assertTrue(invoiceId > 0, "Created invoice ID must be positive");

        // Verify stock decremented
        assertEquals(initialBookStock - 2, productDao.getStock(book.getProductId()));
        assertEquals(initialNotebookStock - 5, productDao.getStock(notebook.getProductId()));

        // Verify invoice retrievable
        Optional<Invoice> fetched = invoiceDao.findInvoiceById(invoiceId);
        assertTrue(fetched.isPresent());
        assertEquals("Dr. John Watson", fetched.get().getCustomerName());
        assertEquals(92.50, fetched.get().getOrderTotal(), 0.001);

        // Verify line items
        List<InvoiceItem> items = invoiceDao.getInvoiceItems(invoiceId);
        assertEquals(2, items.size());
        assertEquals(2, items.get(0).getQuantity());
        assertEquals(5, items.get(1).getQuantity());
    }

    @Test
    @DisplayName("Verify Transaction Rollback on Insufficient Stock leaves Inventory Untouched")
    public void testCreateInvoiceRollbackOnInsufficientStock() throws SQLException {
        Product orwell = productDao.findBySku("BK-003").orElseThrow(); // stock = 10
        Product pen = productDao.findBySku("ST-002").orElseThrow(); // stock = 75

        int initialOrwellStock = orwell.getQuantity();
        int initialPenStock = pen.getQuantity();
        int initialInvoiceCount = invoiceDao.getAllInvoices().size();

        // Valid item + item that exceeds stock (asking for 50 when only 10 exist)
        InvoiceItem item1 = new InvoiceItem(pen.getProductId(), pen.getProductName(), pen.getSku(), 5, 6.00);
        InvoiceItem item2 = new InvoiceItem(orwell.getProductId(), orwell.getProductName(), orwell.getSku(), 50, 15.00);

        Invoice inv = new Invoice(0, 0, "Failed Customer", LocalDate.now(), null, 780.00, 0.0, 0.0, 780.00);

        assertThrows(SQLException.class, () -> invoiceDao.createInvoice(inv, Arrays.asList(item1, item2)));

        // Verify rollback: Pen stock must NOT have been decremented
        assertEquals(initialPenStock, productDao.getStock(pen.getProductId()), "Pen stock must remain untouched");
        assertEquals(initialOrwellStock, productDao.getStock(orwell.getProductId()), "Orwell stock must remain untouched");

        // Verify no invoice was created
        assertEquals(initialInvoiceCount, invoiceDao.getAllInvoices().size(), "Invoice count must remain unchanged after rollback");
        assertTrue(invoiceDao.findInvoicesByCustomer("Failed Customer").isEmpty(), "Failed customer invoice must not exist");
    }

    @Test
    @DisplayName("Verify Foreign Key Cascade Delete Removes Line Items when Invoice is Deleted")
    public void testCascadeDelete() throws SQLException {
        Product pen = productDao.findBySku("ST-002").orElseThrow();
        InvoiceItem item = new InvoiceItem(pen.getProductId(), pen.getProductName(), pen.getSku(), 1, 6.00);
        Invoice inv = new Invoice(0, 0, "Temporary Buyer", LocalDate.now(), null, 6.00, 0.0, 0.0, 6.00);

        int invoiceId = invoiceDao.createInvoice(inv, Collections.singletonList(item));
        assertFalse(invoiceDao.getInvoiceItems(invoiceId).isEmpty());

        // Delete invoice
        boolean deleted = invoiceDao.deleteInvoice(invoiceId);
        assertTrue(deleted);

        // Invoices and its items must be gone
        assertFalse(invoiceDao.findInvoiceById(invoiceId).isPresent());
        assertTrue(invoiceDao.getInvoiceItems(invoiceId).isEmpty(), "Cascaded items must be deleted");
    }

    @Test
    @DisplayName("Verify Query by Customer Name and Date Range")
    public void testQueryByCustomerAndDate() throws SQLException {
        Product notebook = productDao.findBySku("ST-001").orElseThrow();
        InvoiceItem item = new InvoiceItem(notebook.getProductId(), notebook.getProductName(), notebook.getSku(), 1, 4.50);

        Invoice inv1 = new Invoice(0, 0, "Alice Wonderland", LocalDate.of(2026, 10, 1), null, 4.50, 0.0, 0.0, 4.50);
        Invoice inv2 = new Invoice(0, 0, "Bob Builder", LocalDate.of(2026, 10, 15), null, 4.50, 0.0, 0.0, 4.50);

        invoiceDao.createInvoice(inv1, Collections.singletonList(item));
        invoiceDao.createInvoice(inv2, Collections.singletonList(item));

        List<Invoice> aliceList = invoiceDao.findInvoicesByCustomer("Alice");
        assertEquals(1, aliceList.size());
        assertEquals("Alice Wonderland", aliceList.get(0).getCustomerName());

        List<Invoice> rangeList = invoiceDao.findInvoicesByDateRange(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20));
        assertEquals(1, rangeList.size());
        assertEquals("Bob Builder", rangeList.get(0).getCustomerName());
    }
}
