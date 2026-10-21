package com.example.t.dao;

import com.example.t.Invoice;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import com.example.t.model.InvoiceItem;
import com.example.t.model.InvoicePayment;
import com.example.t.model.ZReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Stress and Adversarial Edge-Case Test Suite for Milestone 1.1-A DAOs:
 * - CatalogCacheDao: ISBN normalization, hyphens, lowercase 'x' check digits, null/empty edge cases, upsert.
 * - InvoicePaymentDao: Split tenders (Cash, Card, Credit), total calculation, foreign key cascade, rollback.
 * - ZReportDao: Consecutive sequence numbering, cumulative grand total math, initial empty table behavior.
 */
@DisplayName("Milestone 1.1-A - DAO Stress & Edge Case Empirical Verification")
public class DaoStressAndEdgeCaseEmpiricalTest {

    @TempDir
    Path tempDir;

    private CatalogCacheDao cacheDao;
    private InvoiceDao invoiceDao;
    private InvoicePaymentDao paymentDao;
    private ZReportDao zReportDao;

    @BeforeEach
    public void setUp() throws SQLException {
        Path testDb = tempDir.resolve("dao_stress_test.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();

        cacheDao = new CatalogCacheDao();
        invoiceDao = new InvoiceDao();
        paymentDao = new InvoicePaymentDao();
        zReportDao = new ZReportDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    // =========================================================================
    // 1. CatalogCacheDao Stress & Edge Cases
    // =========================================================================

    @Test
    @DisplayName("CatalogCacheDao: Lookup with clean ISBN vs hyphenated ISBN vs lowercase 'x' check digit")
    public void testCatalogCacheIsbnLookupVariations() throws SQLException {
        // Book 1: ISBN-10 ending with uppercase 'X'
        BookMetadata bookX = new BookMetadata();
        bookX.setIsbn13("978-0804429573");
        bookX.setIsbn10("080442957X");
        bookX.setTitle("The Catcher in the Rye Study Guide");
        bookX.setAuthors("J. D. Salinger");
        cacheDao.save(bookX);

        // 1. Exact matches
        assertTrue(cacheDao.findByIsbn("978-0804429573").isPresent(), "Must match formatted ISBN-13");
        assertTrue(cacheDao.findByIsbn("080442957X").isPresent(), "Must match formatted ISBN-10 with uppercase X");

        // 2. Unhyphenated ISBN-13 match
        assertTrue(cacheDao.findByIsbn("9780804429573").isPresent(), "Must match unhyphenated ISBN-13");

        // 3. Hyphenated ISBN-10 lookup with uppercase X
        assertTrue(cacheDao.findByIsbn("0-8044-2957-X").isPresent(), "Must match hyphenated ISBN-10");

        // 4. Whitespace trimming
        assertTrue(cacheDao.findByIsbn("  978-0804429573  ").isPresent(), "Must handle leading/trailing whitespace");

        // 5. Lowercase 'x' check digit lookup (both raw clean and hyphenated)
        // BUG SURFACE: SQLite default collation is BINARY; without UPPER() / COLLATE NOCASE / clean.toUpperCase(),
        // lookup with lowercase 'x' fails to match stored uppercase 'X'.
        Optional<BookMetadata> foundLowerX = cacheDao.findByIsbn("080442957x");
        Optional<BookMetadata> foundHyphenLowerX = cacheDao.findByIsbn("0-8044-2957-x");

        assertTrue(foundLowerX.isPresent(), "Lookup with lowercase 'x' check digit should find record saved with uppercase 'X'");
        assertTrue(foundHyphenLowerX.isPresent(), "Lookup with hyphenated lowercase 'x' check digit should find record saved with uppercase 'X'");
    }

    @Test
    @DisplayName("CatalogCacheDao: Lookup and delete when record was saved with lowercase 'x' check digit")
    public void testCatalogCacheSavedWithLowercaseX() throws SQLException {
        BookMetadata book = new BookMetadata();
        book.setIsbn13("978-0201633610");
        book.setIsbn10("020163361x"); // lowercase x saved
        book.setTitle("Design Patterns");
        cacheDao.save(book);

        // Lookup with exact lowercase x
        Optional<BookMetadata> foundLowerX = cacheDao.findByIsbn("020163361x");
        assertTrue(foundLowerX.isPresent(), "Exact lowercase 'x' must match");

        // Lookup with uppercase X (clean and hyphenated)
        Optional<BookMetadata> foundUpperX = cacheDao.findByIsbn("020163361X");
        Optional<BookMetadata> foundHyphenUpperX = cacheDao.findByIsbn("0-201-63361-X");
        assertTrue(foundUpperX.isPresent(), "Uppercase 'X' lookup should match record saved with lowercase 'x'");
        assertTrue(foundHyphenUpperX.isPresent(), "Hyphenated uppercase 'X' lookup should match record saved with lowercase 'x'");

        // Delete with uppercase X
        boolean deleted = cacheDao.delete("020163361X");
        assertTrue(deleted, "Delete with uppercase 'X' should delete record saved with lowercase 'x'");
    }

    @Test
    @DisplayName("CatalogCacheDao: Save with null and empty fields (subtitle, authors, cover URL, etc.)")
    public void testCatalogCacheSaveNullAndEmptyFields() throws SQLException {
        // Record 1: completely null optional fields
        BookMetadata minimal = new BookMetadata();
        minimal.setIsbn13("978-0000000001");
        minimal.setTitle("Minimal Title Only");
        minimal.setSubtitle(null);
        minimal.setAuthors(null);
        minimal.setPublisher(null);
        minimal.setPublishedYear(null);
        minimal.setEdition(null);
        minimal.setFormat(null);
        minimal.setGenre(null);
        minimal.setDescription(null);
        minimal.setCoverImageUrl(null);
        minimal.setCoverImagePath(null);
        minimal.setSource(null);
        minimal.setCachedAt(null);
        minimal.setRawJson(null);

        assertDoesNotThrow(() -> cacheDao.save(minimal), "Saving with null optional fields must succeed");

        Optional<BookMetadata> retrieved1 = cacheDao.findByIsbn("978-0000000001");
        assertTrue(retrieved1.isPresent());
        BookMetadata m1 = retrieved1.get();
        assertEquals("Minimal Title Only", m1.getTitle());
        assertNull(m1.getSubtitle());
        assertNull(m1.getAuthors());
        assertNull(m1.getCoverUrl());
        assertNull(m1.getCoverImagePath());
        assertEquals("LOCAL", m1.getSource(), "Default source should be LOCAL when null passed");
        assertNotNull(m1.getCachedAt(), "CachedAt timestamp must be auto-populated");

        // Record 2: empty string optional fields
        BookMetadata emptyFields = new BookMetadata();
        emptyFields.setIsbn13("978-0000000002");
        emptyFields.setTitle("Empty String Fields");
        emptyFields.setSubtitle("");
        emptyFields.setAuthors("");
        emptyFields.setCoverImageUrl("");
        emptyFields.setDescription("");

        assertDoesNotThrow(() -> cacheDao.save(emptyFields));
        Optional<BookMetadata> retrieved2 = cacheDao.findByIsbn("978-0000000002");
        assertTrue(retrieved2.isPresent());
        assertEquals("", retrieved2.get().getSubtitle());
        assertEquals("", retrieved2.get().getAuthors());
        assertEquals("", retrieved2.get().getCoverUrl());

        // Saving with null or empty ISBN-13 should throw IllegalArgumentException
        BookMetadata nullIsbn = new BookMetadata();
        nullIsbn.setTitle("Missing ISBN");
        assertThrows(IllegalArgumentException.class, () -> cacheDao.save(nullIsbn));

        BookMetadata emptyIsbn = new BookMetadata();
        emptyIsbn.setIsbn13("   ");
        emptyIsbn.setTitle("Blank ISBN");
        assertThrows(IllegalArgumentException.class, () -> cacheDao.save(emptyIsbn));

        assertThrows(IllegalArgumentException.class, () -> cacheDao.save(null));
    }

    @Test
    @DisplayName("CatalogCacheDao: Update / upsert on existing ISBN-13 preserves count and updates attributes")
    public void testCatalogCacheUpsertExistingIsbn() throws SQLException {
        BookMetadata initial = new BookMetadata();
        initial.setIsbn13("978-0134685991");
        initial.setIsbn10("0134685997");
        initial.setTitle("Effective Java Initial");
        initial.setAuthors("Joshua Bloch");
        initial.setPageCount(350);
        initial.setCoverImageUrl("http://old-cover.com/1.jpg");
        cacheDao.save(initial);

        assertEquals(1, cacheDao.getCount(), "Should have exactly 1 record");

        // Perform update via save()
        BookMetadata updated = new BookMetadata();
        updated.setIsbn13("978-0134685991"); // Same ISBN-13
        updated.setIsbn10("0134685997");
        updated.setTitle("Effective Java 3rd Edition");
        updated.setAuthors("Joshua Bloch");
        updated.setPageCount(412);
        updated.setCoverImageUrl("http://new-cover.com/2.jpg");
        updated.setEdition("3rd Edition");
        cacheDao.save(updated);

        assertEquals(1, cacheDao.getCount(), "Count must remain 1 after upserting existing ISBN-13");

        Optional<BookMetadata> fetched = cacheDao.findByIsbn("978-0134685991");
        assertTrue(fetched.isPresent());
        assertEquals("Effective Java 3rd Edition", fetched.get().getTitle());
        assertEquals(412, fetched.get().getPageCount());
        assertEquals("http://new-cover.com/2.jpg", fetched.get().getCoverUrl());
        assertEquals("3rd Edition", fetched.get().getEdition());
    }

    // =========================================================================
    // 2. InvoicePaymentDao Stress & Edge Cases
    // =========================================================================

    @Test
    @DisplayName("InvoicePaymentDao: Save split tenders across Cash, Card, and Store Credit and verify totals")
    public void testSplitTenderMultiPaymentSaveAndTotals() throws SQLException {
        int invoiceId = createTestInvoice("Alice Smith", 300.0);

        List<InvoicePayment> splitTenders = new ArrayList<>();
        // Tender 1: Cash ($100 applied, $120 tendered, $20 change)
        splitTenders.add(new InvoicePayment(invoiceId, "CASH", 100.0, 120.0, 20.0, "Register Drawer #1"));
        // Tender 2: Card ($150 applied, $150 tendered, $0 change)
        splitTenders.add(new InvoicePayment(invoiceId, "CARD", 150.0, 150.0, 0.0, "Visa **** 1111 Auth: 998877"));
        // Tender 3: Store Credit Voucher ($50 applied, $50 tendered, $0 change)
        splitTenders.add(new InvoicePayment(invoiceId, "STORE_CREDIT", 50.0, 50.0, 0.0, "Gift Voucher #GV-2026-X"));

        paymentDao.savePayments(invoiceId, splitTenders);

        List<InvoicePayment> saved = paymentDao.getPaymentsByInvoiceId(invoiceId);
        assertEquals(3, saved.size(), "Should have recorded 3 payment tenders");

        assertEquals("CASH", saved.get(0).getPaymentType());
        assertEquals(100.0, saved.get(0).getAmount(), 0.001);
        assertEquals(120.0, saved.get(0).getTenderedAmount(), 0.001);
        assertEquals(20.0, saved.get(0).getChangeDue(), 0.001);

        assertEquals("CARD", saved.get(1).getPaymentType());
        assertEquals(150.0, saved.get(1).getAmount(), 0.001);

        assertEquals("STORE_CREDIT", saved.get(2).getPaymentType());
        assertEquals(50.0, saved.get(2).getAmount(), 0.001);

        // Verify getTotalPaidForInvoice
        double totalPaid = paymentDao.getTotalPaidForInvoice(invoiceId);
        assertEquals(300.0, totalPaid, 0.001, "Total paid must be exactly 100 + 150 + 50 = 300.0");
    }

    @Test
    @DisplayName("InvoicePaymentDao: getTotalPaidForInvoice returns 0.0 for nonexistent or unpaid invoice, null/empty batch handling")
    public void testGetTotalPaidForEmptyOrNonexistentInvoice() throws SQLException {
        int unpaidInvoiceId = createTestInvoice("Unpaid Customer", 75.0);

        assertEquals(0.0, paymentDao.getTotalPaidForInvoice(unpaidInvoiceId), 0.001,
                "Unpaid invoice must have total paid = 0.0");
        assertEquals(0.0, paymentDao.getTotalPaidForInvoice(999999), 0.001,
                "Nonexistent invoice ID must safely return 0.0 without errors");
        assertEquals(0, paymentDao.getPaymentsByInvoiceId(999999).size(),
                "Nonexistent invoice must return empty list of payments");

        // null or empty payments list should no-op safely without exception
        assertDoesNotThrow(() -> paymentDao.savePayments(unpaidInvoiceId, null));
        assertDoesNotThrow(() -> paymentDao.savePayments(unpaidInvoiceId, new ArrayList<>()));
        assertDoesNotThrow(() -> paymentDao.savePayment(null));
    }

    @Test
    @DisplayName("InvoicePaymentDao: Transaction rollback on failure prevents partial payment insertion")
    public void testTransactionRollbackOnPaymentFailure() throws SQLException {
        // Attempting to save payments for a nonexistent invoice ID will violate foreign key constraint
        int nonExistentInvoiceId = 888888;

        List<InvoicePayment> payments = new ArrayList<>();
        payments.add(new InvoicePayment(nonExistentInvoiceId, "CASH", 50.0, 50.0, 0.0, "Drawer"));
        payments.add(new InvoicePayment(nonExistentInvoiceId, "CARD", 50.0, 50.0, 0.0, "Card"));

        assertThrows(SQLException.class, () -> paymentDao.savePayments(nonExistentInvoiceId, payments),
                "Foreign key constraint violation must throw SQLException");

        // Verify no orphaned payments exist
        List<InvoicePayment> remaining = paymentDao.getPaymentsByInvoiceId(nonExistentInvoiceId);
        assertEquals(0, remaining.size(), "Rollback must ensure no payments are saved for failed transaction");
    }

    // =========================================================================
    // 3. ZReportDao Stress & Edge Cases
    // =========================================================================

    @Test
    @DisplayName("ZReportDao: Empty table behavior for sequence, grand total, and latest report")
    public void testZReportEmptyTableBehavior() throws SQLException {
        // 1. Initial sequence number must be 1
        assertEquals(1, zReportDao.getNextSequenceNumber(), "Initial sequence number must be 1 on empty table");

        // 2. Initial cumulative grand total must be 0.0
        assertEquals(0.0, zReportDao.getLatestCumulativeGrandTotal(), 0.001,
                "Initial cumulative grand total must be 0.0 on empty table");

        // 3. Latest Z-Report must be empty
        Optional<ZReport> latest = zReportDao.getLatestZReport();
        assertFalse(latest.isPresent(), "getLatestZReport must return empty Optional on empty table");

        // 4. Query by arbitrary sequence number must return empty
        Optional<ZReport> bySeq = zReportDao.getZReportBySequence(1);
        assertFalse(bySeq.isPresent(), "getZReportBySequence(1) must return empty Optional on empty table");

        // 5. getAllZReports must return empty list
        List<ZReport> all = zReportDao.getAllZReports();
        assertNotNull(all);
        assertTrue(all.isEmpty(), "getAllZReports must return empty list on empty table");
    }

    @Test
    @DisplayName("ZReportDao: Sequence number generation across multiple reports and unique constraint enforcement")
    public void testZReportSequenceNumberGenerationAndUniqueness() throws SQLException {
        // Save report 1 with sequence 0 (auto-generated)
        ZReport r1 = createSampleZReport(0, 1000.0, 1000.0);
        zReportDao.saveZReport(r1);
        assertEquals(1, r1.getZSequenceNumber(), "Auto-generated sequence number must be 1");
        assertEquals(2, zReportDao.getNextSequenceNumber(), "Next sequence number must be 2");

        // Save report 2 with sequence 0 (auto-generated)
        ZReport r2 = createSampleZReport(0, 2000.0, 3000.0);
        zReportDao.saveZReport(r2);
        assertEquals(2, r2.getZSequenceNumber(), "Auto-generated sequence number must be 2");
        assertEquals(3, zReportDao.getNextSequenceNumber(), "Next sequence number must be 3");

        // Save report 3 with sequence 0 (auto-generated)
        ZReport r3 = createSampleZReport(0, 1500.0, 4500.0);
        zReportDao.saveZReport(r3);
        assertEquals(3, r3.getZSequenceNumber(), "Auto-generated sequence number must be 3");
        assertEquals(4, zReportDao.getNextSequenceNumber(), "Next sequence number must be 4");

        // Save report 4 with an explicitly jumped sequence number (e.g. sequence 10)
        ZReport r4 = createSampleZReport(10, 500.0, 5000.0);
        zReportDao.saveZReport(r4);
        assertEquals(10, r4.getZSequenceNumber());
        assertEquals(11, zReportDao.getNextSequenceNumber(), "Next sequence number must jump to max + 1 = 11");

        // Attempting to save a report with duplicate sequence number must throw SQLException (UNIQUE constraint)
        ZReport duplicate = createSampleZReport(2, 800.0, 5800.0);
        assertThrows(SQLException.class, () -> zReportDao.saveZReport(duplicate),
                "Duplicate z_sequence_number must violate UNIQUE constraint");

        // Saving null report must throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> zReportDao.saveZReport(null));
    }

    @Test
    @DisplayName("ZReportDao: Cumulative Grand Total math CGT_K = CGT_{K-1} + net_sales across consecutive registers")
    public void testCumulativeGrandTotalMath() throws SQLException {
        double[] dailyNetSales = { 1234.56, 2345.67, 3456.78, 4567.89, 5678.90 };
        double runningGrandTotal = 0.0;

        for (int i = 0; i < dailyNetSales.length; i++) {
            double priorCgt = zReportDao.getLatestCumulativeGrandTotal();
            assertEquals(runningGrandTotal, priorCgt, 0.001,
                    "Prior cumulative grand total must match running grand total before day " + (i + 1));

            double netSales = dailyNetSales[i];
            runningGrandTotal += netSales;

            ZReport dayReport = createSampleZReport(i + 1, netSales, runningGrandTotal);
            zReportDao.saveZReport(dayReport);

            double latestCgt = zReportDao.getLatestCumulativeGrandTotal();
            assertEquals(runningGrandTotal, latestCgt, 0.001,
                    "Cumulative grand total after day " + (i + 1) + " must equal CGT_{K-1} + net_sales");
        }

        // Verify all 5 reports are stored and ordered in descending sequence order
        List<ZReport> all = zReportDao.getAllZReports();
        assertEquals(5, all.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(5 - i, all.get(i).getZSequenceNumber(), "Reports must be returned in descending sequence order");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int createTestInvoice(String customer, double total) throws SQLException {
        Invoice inv = new Invoice();
        inv.setCustomerName(customer);
        inv.setOrderTotal(total);
        inv.setDiscountPercentage(0.0);
        inv.setDiscount(0.0);
        inv.setNetPrice(total);

        List<InvoiceItem> items = new ArrayList<>();
        InvoiceItem item = new InvoiceItem();
        item.setProductId(1);
        item.setQuantity(1);
        item.setUnitPrice(total);
        item.setLineTotal(total);
        items.add(item);

        try (Connection conn = DatabaseManager.getConnection()) {
            invoiceDao.createInvoice(inv, items, conn);
            return inv.getInvoiceId();
        }
    }

    private ZReport createSampleZReport(int seq, double netSales, double cumulativeGrandTotal) {
        ZReport r = new ZReport();
        r.setZSequenceNumber(seq);
        r.setPeriodStart(new Timestamp(System.currentTimeMillis() - 86400000));
        r.setPeriodEnd(new Timestamp(System.currentTimeMillis()));
        r.setOpeningFloat(100.0);
        r.setTotalCashSales(netSales * 0.5);
        r.setTotalCardSales(netSales * 0.5);
        r.setTotalCreditSales(0.0);
        r.setGrossSales(netSales);
        r.setTotalDiscount(0.0);
        r.setTaxableSales(netSales);
        r.setTotalTax(0.0);
        r.setNetSales(netSales);
        r.setTransactionCount(15);
        r.setExpectedDrawerCash(100.0 + (netSales * 0.5));
        r.setActualDrawerCash(100.0 + (netSales * 0.5));
        r.setCashOverShort(0.0);
        r.setCumulativeGrandTotal(cumulativeGrandTotal);
        r.setClosedByUser("challenger_emp");
        return r;
    }
}
