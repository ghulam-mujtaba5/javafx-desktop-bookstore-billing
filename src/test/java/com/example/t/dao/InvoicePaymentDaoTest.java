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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-A - InvoicePaymentDao Split Payment Persistence Tests")
public class InvoicePaymentDaoTest {

    @TempDir
    Path tempDir;

    private InvoiceDao invoiceDao;
    private InvoicePaymentDao paymentDao;
    private int sampleInvoiceId;

    @BeforeEach
    public void setUp() throws SQLException {
        Path testDb = tempDir.resolve("test_payments.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();

        invoiceDao = new InvoiceDao();
        paymentDao = new InvoicePaymentDao();

        Invoice inv = new Invoice();
        inv.setCustomerName("Jane Doe");
        inv.setOrderTotal(100.0);
        inv.setDiscountPercentage(0.0);
        inv.setDiscount(0.0);
        inv.setNetPrice(100.0);

        List<InvoiceItem> items = new ArrayList<>();
        InvoiceItem item = new InvoiceItem();
        item.setProductId(1);
        item.setQuantity(2);
        item.setUnitPrice(50.0);
        item.setLineTotal(100.0);
        items.add(item);

        try (Connection conn = DatabaseManager.getConnection()) {
            invoiceDao.createInvoice(inv, items, conn);
            sampleInvoiceId = inv.getInvoiceId();
        }
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify single payment save and retrieval")
    public void testSaveSinglePayment() throws SQLException {
        InvoicePayment payment = new InvoicePayment();
        payment.setInvoiceId(sampleInvoiceId);
        payment.setPaymentType("CASH");
        payment.setAmount(50.0);
        payment.setTenderedAmount(60.0);
        payment.setChangeDue(10.0);
        payment.setReferenceInfo("Cash drawer #1");

        paymentDao.savePayment(payment);
        assertTrue(payment.getPaymentId() > 0, "Generated payment ID must be assigned");

        List<InvoicePayment> retrieved = paymentDao.getPaymentsByInvoiceId(sampleInvoiceId);
        assertEquals(1, retrieved.size());
        InvoicePayment p = retrieved.get(0);
        assertEquals("CASH", p.getPaymentType());
        assertEquals(50.0, p.getAmount(), 0.001);
        assertEquals(60.0, p.getTenderedAmount(), 0.001);
        assertEquals(10.0, p.getChangeDue(), 0.001);
        assertEquals("Cash drawer #1", p.getReferenceInfo());
    }

    @Test
    @DisplayName("Verify split tender multi-payment batch insertion and total calculation")
    public void testSplitTenderBatchPayments() throws SQLException {
        List<InvoicePayment> splitPayments = new ArrayList<>();

        // 1. Cash payment: $40 applied, $50 tendered, $10 change
        InvoicePayment cashTender = new InvoicePayment(sampleInvoiceId, "CASH", 40.0, 50.0, 10.0, "Register 1");
        splitPayments.add(cashTender);

        // 2. Card payment: $45 applied, $45 tendered, $0 change
        InvoicePayment cardTender = new InvoicePayment(sampleInvoiceId, "CARD", 45.0, 45.0, 0.0, "Visa **** 4242");
        splitPayments.add(cardTender);

        // 3. Store credit voucher: $15 applied, $15 tendered, $0 change
        InvoicePayment creditTender = new InvoicePayment(sampleInvoiceId, "STORE_CREDIT", 15.0, 15.0, 0.0, "Voucher #V999");
        splitPayments.add(creditTender);

        paymentDao.savePayments(sampleInvoiceId, splitPayments);

        List<InvoicePayment> retrieved = paymentDao.getPaymentsByInvoiceId(sampleInvoiceId);
        assertEquals(3, retrieved.size(), "Must retrieve exactly 3 payment tenders");

        double totalPaid = paymentDao.getTotalPaidForInvoice(sampleInvoiceId);
        assertEquals(100.0, totalPaid, 0.001, "Total paid amount must equal sum of applied tenders (40 + 45 + 15 = 100)");
    }

    @Test
    @DisplayName("Verify Foreign Key ON DELETE CASCADE removes payments when invoice is deleted")
    public void testCascadeDeleteWithInvoice() throws SQLException {
        InvoicePayment p1 = new InvoicePayment(sampleInvoiceId, "CARD", 100.0, 100.0, 0.0, "Auth 12345");
        paymentDao.savePayment(p1);

        assertEquals(1, paymentDao.getPaymentsByInvoiceId(sampleInvoiceId).size());

        // Delete parent invoice
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM invoices WHERE invoice_id = " + sampleInvoiceId + ";");
        }

        // Payments should be automatically cascaded
        List<InvoicePayment> remaining = paymentDao.getPaymentsByInvoiceId(sampleInvoiceId);
        assertEquals(0, remaining.size(), "Invoice payments must be deleted via cascade when parent invoice is deleted");
    }
}
