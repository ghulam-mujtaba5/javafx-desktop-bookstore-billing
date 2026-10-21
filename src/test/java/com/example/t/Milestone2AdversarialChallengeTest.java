package com.example.t;

import com.example.t.dao.CategoryDao;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.Category;
import com.example.t.model.InvoiceItem;
import com.example.t.model.StoreSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 2:
 * Persistence, Concurrency, Constraints, and Transactional Integrity.
 *
 * Verifies:
 * 1. Transaction Atomicity: Complete rollback on insufficient stock (0 orphaned invoices, 0 altered quantities).
 * 2. Foreign Key Integrity & Cascades: Invoice deletion removes line items, category deletion handled per constraint, invalid FKs rejected.
 * 3. WAL Mode Concurrent Readers: Multiple concurrent reader connections reading while an active writer is uncommitted without SQLITE_BUSY / lock errors.
 * 4. Store Settings Singleton Constraint: Schema-level CHECK & PK constraints reject second rows or invalid setting_ids.
 */
@DisplayName("Milestone 2 - Empirical Adversarial Challenge Test Suite")
public class Milestone2AdversarialChallengeTest {

    @TempDir
    Path tempDir;

    private Path testDbFile;
    private InvoiceDao invoiceDao;
    private ProductDao productDao;
    private CategoryDao categoryDao;
    private SettingsDao settingsDao;

    @BeforeEach
    void setUp() {
        testDbFile = tempDir.resolve("m2_adversarial_bookstore.db");
        DatabaseManager.setCustomDatabasePath(testDbFile);
        DatabaseManager.initializeDatabase();

        invoiceDao = new InvoiceDao();
        productDao = new ProductDao();
        categoryDao = new CategoryDao();
        settingsDao = new SettingsDao();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetToDefault();
    }

    // =========================================================================
    // 1. TRANSACTION ATOMICITY & ROLLBACK UNDER INSUFFICIENT STOCK
    // =========================================================================

    @Test
    @DisplayName("Atomicity: Single product stock exhaustion causes total rollback (0 orphaned invoice rows, 0 altered stock)")
    void testSingleProductStockExhaustionRollback() throws SQLException {
        // Find existing product or create controlled product
        Product product = new Product(0, "Atomic Rollback Book", "Books", "SKU-ATOMIC-1",
                "Author A", "Publisher P", "1st", 10.0, 25.0, 5, 2);
        int prodId = productDao.save(product);
        assertEquals(5, productDao.getStock(prodId), "Initial stock must be exactly 5");

        int initialInvoiceCount = countRows("invoices");
        int initialItemsCount = countRows("invoice_items");

        // Attempt to purchase 6 units (stock is 5)
        InvoiceItem item = new InvoiceItem(prodId, "Atomic Rollback Book", "SKU-ATOMIC-1", 6, 25.0);
        Invoice invoice = new Invoice(0, 0, "Exhaustion Customer", LocalDate.now(), null, 150.0, 0.0, 0.0, 150.0);

        SQLException thrown = assertThrows(SQLException.class, () ->
                invoiceDao.createInvoice(invoice, Collections.singletonList(item))
        );
        assertTrue(thrown.getMessage().toLowerCase().contains("insufficient stock"),
                "Exception message must mention insufficient stock: " + thrown.getMessage());

        // Verify total rollback
        assertEquals(5, productDao.getStock(prodId), "Stock quantity must NOT be altered after failed invoice");
        assertEquals(initialInvoiceCount, countRows("invoices"), "Zero orphaned rows in invoices table");
        assertEquals(initialItemsCount, countRows("invoice_items"), "Zero orphaned rows in invoice_items table");
        assertTrue(invoiceDao.findInvoicesByCustomer("Exhaustion Customer").isEmpty(),
                "Invoice must not be findable in queries");
    }

    @Test
    @DisplayName("Atomicity: Multi-item order rolls back prior successfully decremented items when a later item fails")
    void testMultiItemOrderRollsBackPriorDecrementedStock() throws SQLException {
        // Item 1: Plenty of stock (10)
        Product p1 = new Product(0, "Item One Valid", "Books", "SKU-M-1",
                "Author", "Pub", "1", 10.0, 20.0, 10, 2);
        int id1 = productDao.save(p1);

        // Item 2: Plenty of stock (15)
        Product p2 = new Product(0, "Item Two Valid", "Stationery", "SKU-M-2",
                "Author", "Pub", "1", 5.0, 10.0, 15, 2);
        int id2 = productDao.save(p2);

        // Item 3: Insufficient stock (only 3 in stock, order asks for 4)
        Product p3 = new Product(0, "Item Three Failing", "Office Supplies", "SKU-M-3",
                "Author", "Pub", "1", 2.0, 5.0, 3, 1);
        int id3 = productDao.save(p3);

        int initialInvoices = countRows("invoices");
        int initialItems = countRows("invoice_items");

        InvoiceItem item1 = new InvoiceItem(id1, "Item One Valid", "SKU-M-1", 4, 20.0);
        InvoiceItem item2 = new InvoiceItem(id2, "Item Two Valid", "SKU-M-2", 5, 10.0);
        InvoiceItem item3 = new InvoiceItem(id3, "Item Three Failing", "SKU-M-3", 4, 5.0); // 4 > 3 -> FAIL

        Invoice invoice = new Invoice(0, 0, "Multi-Item Failure Buyer", LocalDate.now(), null, 150.0, 0.0, 0.0, 150.0);

        assertThrows(SQLException.class, () ->
                invoiceDao.createInvoice(invoice, Arrays.asList(item1, item2, item3))
        );

        // Crucial empirical assertion: Item 1 and Item 2 MUST NOT remain decremented!
        assertEquals(10, productDao.getStock(id1), "Item 1 stock must be rolled back to original 10 (not 6)");
        assertEquals(15, productDao.getStock(id2), "Item 2 stock must be rolled back to original 15 (not 10)");
        assertEquals(3, productDao.getStock(id3), "Item 3 stock must remain unchanged at 3");

        assertEquals(initialInvoices, countRows("invoices"), "No invoice row committed");
        assertEquals(initialItems, countRows("invoice_items"), "No invoice_items rows committed");
    }

    @Test
    @Timeout(15)
    @DisplayName("Atomicity Under Concurrency: 10 threads competing for stock of 5 units (each buying 2)")
    void testConcurrentInventoryOversellPrevention() throws Exception {
        Product scarceProduct = new Product(0, "Scarce Collector Item", "Books", "SKU-SCARCE",
                "Rare", "Press", "1", 100.0, 200.0, 5, 1);
        int prodId = productDao.save(scarceProduct);

        final int NUM_THREADS = 10;
        final int UNITS_PER_BUY = 2; // Stock is 5: only 2 buyers can possibly succeed (2*2 = 4 units, leaving 1 unit)
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(NUM_THREADS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // synchronous start
                    InvoiceItem item = new InvoiceItem(prodId, "Scarce Collector Item", "SKU-SCARCE", UNITS_PER_BUY, 200.0);
                    Invoice inv = new Invoice(0, 0, "Concurrent Buyer " + threadId, LocalDate.now(), null, 400.0, 0.0, 0.0, 400.0);
                    invoiceDao.createInvoice(inv, Collections.singletonList(item));
                    successCount.incrementAndGet();
                } catch (SQLException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly 2 transactions must succeed (since 2 * 2 = 4 <= 5; a 3rd would need 6 > 5)
        assertEquals(2, successCount.get(), "Exactly 2 concurrent orders must succeed with stock=5 and qty=2");
        assertEquals(NUM_THREADS - 2, failureCount.get(), "Remaining orders must fail cleanly");

        // Remaining stock must be exactly 5 - 4 = 1
        assertEquals(1, productDao.getStock(prodId), "Final stock must be strictly 1 (no negative stock)");

        // Exactly 2 invoices and 2 line items must exist for this product
        int itemsInDb = countRowsWhere("invoice_items", "product_id = " + prodId);
        assertEquals(2, itemsInDb, "Exactly 2 invoice_items rows must be recorded");
    }

    // =========================================================================
    // 2. FOREIGN KEY INTEGRITY, CASCADE DELETES & CONSTRAINTS
    // =========================================================================

    @Test
    @DisplayName("Foreign Key Cascade: Deleting an invoice properly cascades and removes all its line items")
    void testCascadeDeleteInvoiceRemovesLineItems() throws SQLException {
        Product p = productDao.findBySku("BK-001").orElseThrow();
        InvoiceItem item1 = new InvoiceItem(p.getProductId(), p.getProductName(), p.getSku(), 1, 35.0);
        InvoiceItem item2 = new InvoiceItem(p.getProductId(), p.getProductName(), p.getSku(), 1, 35.0);

        Invoice inv = new Invoice(0, 0, "Cascade Target Buyer", LocalDate.now(), null, 70.0, 0.0, 0.0, 70.0);
        int invoiceId = invoiceDao.createInvoice(inv, Arrays.asList(item1, item2));

        // Line items exist
        List<InvoiceItem> itemsBefore = invoiceDao.getInvoiceItems(invoiceId);
        assertEquals(2, itemsBefore.size(), "Two line items should exist before invoice deletion");
        assertEquals(2, countRowsWhere("invoice_items", "invoice_id = " + invoiceId));

        // Delete invoice
        boolean deleted = invoiceDao.deleteInvoice(invoiceId);
        assertTrue(deleted, "deleteInvoice should report true");

        // Assert invoice is gone
        assertTrue(invoiceDao.findInvoiceById(invoiceId).isEmpty());

        // Assert line items were cascaded and completely removed
        List<InvoiceItem> itemsAfter = invoiceDao.getInvoiceItems(invoiceId);
        assertTrue(itemsAfter.isEmpty(), "Invoice items must be empty after parent invoice deletion");
        assertEquals(0, countRowsWhere("invoice_items", "invoice_id = " + invoiceId),
                "Database invoice_items table must contain 0 rows for deleted invoice");
    }

    @Test
    @DisplayName("Foreign Key Constraint: Category deletion with existing products is handled safely per schema (ON DELETE SET NULL)")
    void testCategoryDeletionWithExistingProductsHandledPerConstraint() throws SQLException {
        // 1. Create a specific category
        Category customCat = new Category("Ephemeral SciFi", "Temporary genre");
        int catId = categoryDao.save(customCat);
        assertTrue(catId > 0);

        // 2. Create products belonging to this category
        Product p1 = new Product(0, "SciFi Novel Alpha", "Ephemeral SciFi", "SKU-SCI-1",
                "Author", "Pub", "1", 10.0, 18.0, 20, 5);
        Product p2 = new Product(0, "SciFi Novel Beta", "Ephemeral SciFi", "SKU-SCI-2",
                "Author", "Pub", "1", 12.0, 22.0, 15, 5);
        int p1Id = productDao.save(p1);
        int p2Id = productDao.save(p2);

        // Verify products initially linked to category
        assertEquals(2, productDao.findByCategoryId(catId).size());

        // 3. Delete the category
        boolean deleted = categoryDao.delete(catId);
        assertTrue(deleted, "Category deletion must succeed");
        assertTrue(categoryDao.findById(catId).isEmpty(), "Category must no longer exist");

        // 4. Products must still exist, with their category_id set to NULL per schema constraint ON DELETE SET NULL
        Optional<Product> p1After = productDao.findById(p1Id);
        Optional<Product> p2After = productDao.findById(p2Id);
        assertTrue(p1After.isPresent(), "Product 1 must not be dropped when category is deleted");
        assertTrue(p2After.isPresent(), "Product 2 must not be dropped when category is deleted");

        // In database, category_id should be NULL
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT category_id FROM products WHERE product_id = " + p1Id)) {
                assertTrue(rs.next());
                assertNull(rs.getObject("category_id"), "products.category_id must be NULL after category deletion");
            }

            // Verify foreign key integrity remains 100% valid across whole DB
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check;")) {
                assertFalse(rs.next(), "PRAGMA foreign_key_check must return 0 violations");
            }
        }
    }

    @Test
    @DisplayName("Foreign Key Constraint: Inserting invoice_item with invalid invoice_id or product_id is rejected")
    void testInvalidForeignKeyInsertionRejected() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            // Non-existent invoice_id
            String sqlBadInvoice = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                    "VALUES (999999, 1, 1, 10.0, 10.0);";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlBadInvoice)) {
                assertThrows(SQLException.class, pstmt::executeUpdate,
                        "Inserting line item with invalid invoice_id must trigger foreign key violation");
            }

            // Non-existent product_id
            Product p = productDao.findBySku("BK-001").orElseThrow();
            InvoiceItem item = new InvoiceItem(p.getProductId(), p.getProductName(), p.getSku(), 1, 35.0);
            Invoice inv = new Invoice(0, 0, "Valid Customer", LocalDate.now(), null, 35.0, 0.0, 0.0, 35.0);
            int validInvoiceId = invoiceDao.createInvoice(inv, Collections.singletonList(item));

            String sqlBadProduct = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                    "VALUES (?, 999999, 1, 10.0, 10.0);";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlBadProduct)) {
                pstmt.setInt(1, validInvoiceId);
                assertThrows(SQLException.class, pstmt::executeUpdate,
                        "Inserting line item with invalid product_id must trigger foreign key violation");
            }
        }
    }

    // =========================================================================
    // 3. WAL MODE CONCURRENT READERS DURING ACTIVE WRITER
    // =========================================================================

    @Test
    @Timeout(15)
    @DisplayName("WAL Mode: Multiple concurrent readers execute queries without locking errors while a writer transaction is open")
    void testWalModeConcurrentReadersWhileWriterActive() throws Exception {
        // Prepare test data
        int initialProductCount = countRows("products");
        assertTrue(initialProductCount > 0);

        CountDownLatch writerStartedLatch = new CountDownLatch(1);
        CountDownLatch readersFinishedLatch = new CountDownLatch(3);
        CountDownLatch writerCanCommitLatch = new CountDownLatch(1);

        AtomicInteger readerSuccessCount = new AtomicInteger(0);
        List<Throwable> readerErrors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(4);

        // 1. Thread 1: Writer opens transaction, performs uncommitted insert, and pauses
        Future<?> writerFuture = executor.submit(() -> {
            try (Connection writeConn = DatabaseManager.getConnection()) {
                writeConn.setAutoCommit(false);

                // Insert a pending product
                String sql = "INSERT INTO products (sku, name, price, stock_quantity) VALUES ('SKU-PENDING-WAL', 'Pending WAL Book', 99.0, 10);";
                try (Statement stmt = writeConn.createStatement()) {
                    stmt.executeUpdate(sql);
                }

                // Signal that write transaction is active and uncommitted
                writerStartedLatch.countDown();

                // Wait for concurrent readers to complete their reads
                boolean allowed = writerCanCommitLatch.await(5, TimeUnit.SECONDS);
                assertTrue(allowed, "Writer timed out waiting for readers");

                writeConn.commit();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait until writer has actively modified database within uncommitted transaction
        assertTrue(writerStartedLatch.await(5, TimeUnit.SECONDS));

        // 2. Threads 2, 3, 4: Concurrent readers execute reads while writer is actively uncommitted
        for (int r = 0; r < 3; r++) {
            final int readerId = r;
            executor.submit(() -> {
                try (Connection readConn = DatabaseManager.getConnection()) {
                    // Execute multiple reads
                    try (Statement stmt = readConn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products;")) {
                        assertTrue(rs.next());
                        int count = rs.getInt(1);
                        // In WAL mode, snapshot isolation means reader does NOT see uncommitted insert yet
                        assertEquals(initialProductCount, count, "Reader " + readerId + " must see snapshot before uncommitted write");
                    }

                    // Read categories
                    try (Statement stmt = readConn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT * FROM categories;")) {
                        int catCount = 0;
                        while (rs.next()) catCount++;
                        assertTrue(catCount >= 4, "Reader " + readerId + " should read categories cleanly");
                    }

                    readerSuccessCount.incrementAndGet();
                } catch (Throwable t) {
                    readerErrors.add(t);
                } finally {
                    readersFinishedLatch.countDown();
                }
            });
        }

        // Wait for all 3 readers to finish
        assertTrue(readersFinishedLatch.await(5, TimeUnit.SECONDS), "Readers must not deadlock or block on active writer");
        assertEquals(3, readerSuccessCount.get(), "All 3 concurrent readers must succeed without 'database is locked'");
        assertTrue(readerErrors.isEmpty(), "Reader encountered errors: " + readerErrors);

        // Allow writer to commit
        writerCanCommitLatch.countDown();
        writerFuture.get(5, TimeUnit.SECONDS);

        // After writer commits, reader should see newly inserted product
        assertEquals(initialProductCount + 1, countRows("products"), "Committed product must now be visible");
        assertTrue(productDao.findBySku("SKU-PENDING-WAL").isPresent());

        executor.shutdown();
    }

    @Test
    @Timeout(20)
    @DisplayName("WAL Concurrency Stress: Continuous concurrent readers during rapid intermittent writes")
    void testWalContinuousReadersStress() throws Exception {
        final int DURATION_MS = 2000;
        final int NUM_READERS = 5;
        ExecutorService executor = Executors.newFixedThreadPool(NUM_READERS + 1);

        AtomicInteger totalReadQueries = new AtomicInteger(0);
        AtomicInteger totalWriteQueries = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        long endTime = System.currentTimeMillis() + DURATION_MS;

        // Writer thread: Rapidly inserts and updates products
        executor.submit(() -> {
            int writeIdx = 0;
            while (System.currentTimeMillis() < endTime && errors.isEmpty()) {
                try {
                    Product p = new Product(0, "Stress Book " + writeIdx, "Books", "SKU-STR-" + writeIdx,
                            "Author", "Pub", "1", 10.0, 15.0, 10, 2);
                    productDao.save(p);
                    totalWriteQueries.incrementAndGet();
                    Thread.sleep(10);
                } catch (Throwable t) {
                    errors.add(t);
                    break;
                }
                writeIdx++;
            }
        });

        // Reader threads: Continuously query products and categories
        for (int i = 0; i < NUM_READERS; i++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() < endTime && errors.isEmpty()) {
                    try {
                        List<Product> products = productDao.getAllProducts();
                        assertNotNull(products);
                        List<Category> categories = categoryDao.getAllCategories();
                        assertNotNull(categories);
                        totalReadQueries.incrementAndGet();
                    } catch (Throwable t) {
                        errors.add(t);
                        break;
                    }
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "Concurrency stress encountered errors: " + errors);
        assertTrue(totalWriteQueries.get() > 10, "Writer should execute multiple transactions");
        assertTrue(totalReadQueries.get() > 50, "Readers should execute dozens of queries smoothly in WAL mode");
    }

    // =========================================================================
    // 4. STORE SETTINGS SINGLETON CONSTRAINT
    // =========================================================================

    @Test
    @DisplayName("Singleton Constraint: Attempt to insert row with setting_id != 1 is rejected by CHECK constraint")
    void testStoreSettingsRejectsInvalidSettingId() throws SQLException {
        // Schema constraint: CHECK (setting_id = 1)
        try (Connection conn = DatabaseManager.getConnection()) {
            String insertId2 = "INSERT INTO store_settings (setting_id, store_name, admin_password_hash) VALUES (2, 'Illegal Store 2', 'hash123');";
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () -> stmt.executeUpdate(insertId2),
                        "Inserting setting_id = 2 must fail CHECK constraint");
                assertTrue(ex.getMessage().toLowerCase().contains("check constraint") ||
                                ex.getMessage().toLowerCase().contains("constraint failed"),
                        "Expected check constraint error, got: " + ex.getMessage());
            }

            String insertId0 = "INSERT INTO store_settings (setting_id, store_name, admin_password_hash) VALUES (0, 'Illegal Store 0', 'hash123');";
            try (Statement stmt = conn.createStatement()) {
                assertThrows(SQLException.class, () -> stmt.executeUpdate(insertId0),
                        "Inserting setting_id = 0 must fail CHECK constraint");
            }
        }
    }

    @Test
    @DisplayName("Singleton Constraint: Attempt to insert second row with setting_id = 1 is rejected by PRIMARY KEY constraint")
    void testStoreSettingsRejectsSecondRowWithSameId() throws SQLException {
        // Row 1 already exists from database initialization
        assertEquals(1, countRows("store_settings"), "store_settings must have exactly 1 row initially");

        try (Connection conn = DatabaseManager.getConnection()) {
            String insertDuplicate = "INSERT INTO store_settings (setting_id, store_name, admin_password_hash) VALUES (1, 'Duplicate Store', 'hash123');";
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () -> stmt.executeUpdate(insertDuplicate),
                        "Inserting duplicate row with setting_id = 1 must fail PRIMARY KEY uniqueness");
                assertTrue(ex.getMessage().toLowerCase().contains("unique") ||
                                ex.getMessage().toLowerCase().contains("primary key"),
                        "Expected UNIQUE/PRIMARY KEY constraint error, got: " + ex.getMessage());
            }
        }

        assertEquals(1, countRows("store_settings"), "store_settings row count must remain strictly 1");
    }

    @Test
    @DisplayName("Singleton Contract: Repeated saves through SettingsDao update existing singleton without duplicating rows")
    void testSettingsDaoUpdatesSingletonRowInPlace() throws SQLException {
        StoreSettings s1 = settingsDao.getSettings();
        assertEquals("USA", s1.getRegionPreset());

        // Update to UK preset
        s1.setStoreName("London Literary Corner");
        s1.setRegionPreset("UK");
        s1.setCurrencyCode("GBP");
        s1.setCurrencySymbol("£");
        s1.setTaxName("VAT");
        s1.setTaxRate(20.0);
        boolean savedUK = settingsDao.saveSettings(s1);
        assertTrue(savedUK);

        // Verify still exactly 1 row
        assertEquals(1, countRows("store_settings"), "Row count must remain 1 after UK update");
        StoreSettings fetchedUK = settingsDao.getSettings();
        assertEquals("London Literary Corner", fetchedUK.getStoreName());
        assertEquals("UK", fetchedUK.getRegionPreset());
        assertEquals(20.0, fetchedUK.getTaxRate(), 0.001);

        // Update to UAE preset
        s1.setStoreName("Dubai Book Oasis");
        s1.setRegionPreset("GULF_UAE");
        s1.setCurrencyCode("AED");
        s1.setCurrencySymbol("د.إ");
        s1.setTaxName("VAT");
        s1.setTaxRate(5.0);
        boolean savedUAE = settingsDao.saveSettings(s1);
        assertTrue(savedUAE);

        // Verify still strictly 1 row
        assertEquals(1, countRows("store_settings"), "Row count must remain 1 after UAE update");
        StoreSettings fetchedUAE = settingsDao.getSettings();
        assertEquals("Dubai Book Oasis", fetchedUAE.getStoreName());
        assertEquals("GULF_UAE", fetchedUAE.getRegionPreset());
        assertEquals(5.0, fetchedUAE.getTaxRate(), 0.001);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private int countRows(String table) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table + ";")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    private int countRowsWhere(String table, String condition) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + condition + ";")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }
}
