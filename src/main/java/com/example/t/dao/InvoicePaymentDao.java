package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoicePayment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for invoice payment records (invoice_payments table).
 * Supports multi-tender split payments (Cash, Card, Store Credit) per invoice.
 */
public class InvoicePaymentDao {

    public void savePayment(InvoicePayment payment) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            savePayment(payment, conn);
        }
    }

    public void savePayment(InvoicePayment payment, Connection conn) throws SQLException {
        if (payment == null) return;
        String sql = "INSERT INTO invoice_payments (" +
                "invoice_id, payment_type, amount, tendered_amount, change_due, reference_info, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP));";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, payment.getInvoiceId());
            pstmt.setString(2, payment.getPaymentType() != null ? payment.getPaymentType() : "CASH");
            pstmt.setDouble(3, payment.getAmount());
            pstmt.setDouble(4, payment.getTenderedAmount());
            pstmt.setDouble(5, payment.getChangeDue());
            pstmt.setString(6, payment.getReferenceInfo());
            if (payment.getCreatedAt() != null) {
                pstmt.setTimestamp(7, payment.getCreatedAt());
            } else {
                pstmt.setNull(7, Types.TIMESTAMP);
            }

            pstmt.executeUpdate();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    payment.setPaymentId(generatedKeys.getInt(1));
                }
            }
        }
    }

    public void savePayments(int invoiceId, List<InvoicePayment> payments) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                savePayments(invoiceId, payments, conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    public void savePayments(int invoiceId, List<InvoicePayment> payments, Connection conn) throws SQLException {
        if (payments == null || payments.isEmpty()) return;

        String sql = "INSERT INTO invoice_payments (" +
                "invoice_id, payment_type, amount, tendered_amount, change_due, reference_info, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP));";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (InvoicePayment payment : payments) {
                payment.setInvoiceId(invoiceId);
                pstmt.setInt(1, invoiceId);
                pstmt.setString(2, payment.getPaymentType() != null ? payment.getPaymentType() : "CASH");
                pstmt.setDouble(3, payment.getAmount());
                pstmt.setDouble(4, payment.getTenderedAmount());
                pstmt.setDouble(5, payment.getChangeDue());
                pstmt.setString(6, payment.getReferenceInfo());
                if (payment.getCreatedAt() != null) {
                    pstmt.setTimestamp(7, payment.getCreatedAt());
                } else {
                    pstmt.setNull(7, Types.TIMESTAMP);
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public List<InvoicePayment> getPaymentsByInvoiceId(int invoiceId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getPaymentsByInvoiceId(invoiceId, conn);
        }
    }

    public List<InvoicePayment> getPaymentsByInvoiceId(int invoiceId, Connection conn) throws SQLException {
        List<InvoicePayment> list = new ArrayList<>();
        String sql = "SELECT * FROM invoice_payments WHERE invoice_id = ? ORDER BY payment_id ASC;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public double getTotalPaidForInvoice(int invoiceId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getTotalPaidForInvoice(invoiceId, conn);
        }
    }

    public double getTotalPaidForInvoice(int invoiceId, Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0.0) FROM invoice_payments WHERE invoice_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    public boolean deletePaymentsByInvoiceId(int invoiceId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return deletePaymentsByInvoiceId(invoiceId, conn);
        }
    }

    public boolean deletePaymentsByInvoiceId(int invoiceId, Connection conn) throws SQLException {
        String sql = "DELETE FROM invoice_payments WHERE invoice_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private InvoicePayment mapRow(ResultSet rs) throws SQLException {
        InvoicePayment payment = new InvoicePayment();
        payment.setPaymentId(rs.getInt("payment_id"));
        payment.setInvoiceId(rs.getInt("invoice_id"));
        payment.setPaymentType(rs.getString("payment_type"));
        payment.setAmount(rs.getDouble("amount"));
        payment.setTenderedAmount(rs.getDouble("tendered_amount"));
        payment.setChangeDue(rs.getDouble("change_due"));
        payment.setReferenceInfo(rs.getString("reference_info"));
        payment.setCreatedAt(rs.getTimestamp("created_at"));
        return payment;
    }
}
