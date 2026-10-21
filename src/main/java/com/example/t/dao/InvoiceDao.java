package com.example.t.dao;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Invoices and Line Items.
 * Enforces atomic transactions across invoice generation and stock decrement.
 */
public class InvoiceDao {

    /**
     * Atomically creates an invoice, decrements inventory for all items,
     * and inserts line items. Rolls back on any failure or insufficient stock.
     */
    public int createInvoice(Invoice invoice, List<InvoiceItem> items) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return createInvoice(invoice, items, conn);
        }
    }

    public int createInvoice(Invoice invoice, List<InvoiceItem> items, Connection conn) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            // 1. Atomically decrement stock for all items
            if (items != null) {
                String decSql = "UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE product_id = ? AND stock_quantity >= ?;";
                try (PreparedStatement decStmt = conn.prepareStatement(decSql)) {
                    for (InvoiceItem item : items) {
                        decStmt.setInt(1, item.getQuantity());
                        decStmt.setInt(2, item.getProductId());
                        decStmt.setInt(3, item.getQuantity());
                        int updated = decStmt.executeUpdate();
                        if (updated == 0) {
                            throw new SQLException("Insufficient stock for product ID: " + item.getProductId());
                        }
                    }
                }
            }

            // 2. Insert invoice header
            int invoiceId;
            String invNum = "INV-" + System.currentTimeMillis();
            String customer = invoice.getCustomerName();
            if (customer == null || customer.trim().isEmpty()) {
                customer = "Walk-in Customer";
            }

            LocalDate invDate = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now();
            String dateStr = invDate.toString() + " 12:00:00";

            String invSql = "INSERT INTO invoices (invoice_number, customer_name, invoice_date, subtotal, " +
                    "discount_percent, discount_amount, tax_rate, tax_amount, net_total, cash_tendered, " +
                    "change_due, payment_method, currency_code, region) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 0.0, 0.0, ?, ?, 0.0, 'CASH', 'USD', 'USA');";

            try (PreparedStatement invStmt = conn.prepareStatement(invSql, Statement.RETURN_GENERATED_KEYS)) {
                invStmt.setString(1, invNum);
                invStmt.setString(2, customer);
                invStmt.setString(3, dateStr);
                invStmt.setDouble(4, invoice.getOrderTotal());
                invStmt.setDouble(5, invoice.getDiscountPercentage());
                invStmt.setDouble(6, invoice.getDiscount());
                invStmt.setDouble(7, invoice.getNetPrice());
                invStmt.setDouble(8, invoice.getNetPrice()); // tendered default
                invStmt.executeUpdate();

                try (ResultSet rs = invStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        invoiceId = rs.getInt(1);
                        invoice.setInvoiceId(invoiceId);
                        invoice.setOrderNum(invoiceId);
                    } else {
                        throw new SQLException("Failed to obtain generated invoice ID.");
                    }
                }
            }

            // 3. Insert line items
            if (items != null && !items.isEmpty()) {
                String itemSql = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                        "VALUES (?, ?, ?, ?, ?);";
                try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                    for (InvoiceItem item : items) {
                        itemStmt.setInt(1, invoiceId);
                        itemStmt.setInt(2, item.getProductId());
                        itemStmt.setInt(3, item.getQuantity());
                        itemStmt.setDouble(4, item.getUnitPrice());
                        itemStmt.setDouble(5, item.getLineTotal());
                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }
            }

            conn.commit();
            return invoiceId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    public List<Invoice> getAllInvoices() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getAllInvoices(conn);
        }
    }

    public List<Invoice> getAllInvoices(Connection conn) throws SQLException {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT * FROM invoices ORDER BY invoice_id DESC;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapInvoice(rs));
            }
        }
        return list;
    }

    public Optional<Invoice> findInvoiceById(int id) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findInvoiceById(id, conn);
        }
    }

    public Optional<Invoice> findInvoiceById(int id, Connection conn) throws SQLException {
        String sql = "SELECT * FROM invoices WHERE invoice_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Invoice inv = mapInvoice(rs);
                    inv.setProducts(loadInvoiceProducts(id, conn));
                    return Optional.of(inv);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Invoice> findInvoiceByNumber(String invoiceNumber) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT * FROM invoices WHERE invoice_number = ? COLLATE NOCASE;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, invoiceNumber);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Invoice inv = mapInvoice(rs);
                        inv.setProducts(loadInvoiceProducts(inv.getInvoiceId(), conn));
                        return Optional.of(inv);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public List<Invoice> findInvoicesByCustomer(String customerName) throws SQLException {
        List<Invoice> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT * FROM invoices WHERE customer_name LIKE ? ORDER BY invoice_id DESC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "%" + (customerName != null ? customerName.trim() : "") + "%");
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapInvoice(rs));
                    }
                }
            }
        }
        return list;
    }

    public List<Invoice> findInvoicesByDateRange(LocalDate from, LocalDate to) throws SQLException {
        List<Invoice> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT * FROM invoices WHERE DATE(invoice_date) >= DATE(?) AND DATE(invoice_date) <= DATE(?) " +
                    "ORDER BY invoice_id DESC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, from != null ? from.toString() : "1970-01-01");
                pstmt.setString(2, to != null ? to.toString() : "2999-12-31");
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapInvoice(rs));
                    }
                }
            }
        }
        return list;
    }

    public List<InvoiceItem> getInvoiceItems(int invoiceId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getInvoiceItems(invoiceId, conn);
        }
    }

    public List<InvoiceItem> getInvoiceItems(int invoiceId, Connection conn) throws SQLException {
        List<InvoiceItem> list = new ArrayList<>();
        String sql = "SELECT ii.*, p.name AS product_name, p.sku FROM invoice_items ii " +
                "JOIN products p ON ii.product_id = p.product_id WHERE ii.invoice_id = ? " +
                "ORDER BY ii.item_id ASC;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new InvoiceItem(
                            rs.getInt("item_id"),
                            rs.getInt("invoice_id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            rs.getString("sku"),
                            rs.getInt("quantity"),
                            rs.getDouble("unit_price"),
                            rs.getDouble("line_total")
                    ));
                }
            }
        }
        return list;
    }

    public boolean deleteInvoice(int invoiceId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return deleteInvoice(invoiceId, conn);
        }
    }

    public boolean deleteInvoice(int invoiceId, Connection conn) throws SQLException {
        String sql = "DELETE FROM invoices WHERE invoice_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Invoice mapInvoice(ResultSet rs) throws SQLException {
        int id = rs.getInt("invoice_id");
        String customer = rs.getString("customer_name");
        String dateStr = rs.getString("invoice_date");
        LocalDate date = LocalDate.now();
        if (dateStr != null && dateStr.length() >= 10) {
            try {
                date = LocalDate.parse(dateStr.substring(0, 10));
            } catch (Exception ignored) {}
        }
        double subtotal = rs.getDouble("subtotal");
        double discPercent = rs.getDouble("discount_percent");
        double discAmount = rs.getDouble("discount_amount");
        double net = rs.getDouble("net_total");

        return new Invoice(id, id, customer, date, new ArrayList<>(), subtotal, discPercent, discAmount, net);
    }

    public List<Product> loadInvoiceProducts(int invoiceId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return loadInvoiceProducts(invoiceId, conn);
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    public List<Product> loadInvoiceProducts(int invoiceId, Connection conn) {
        List<Product> products = new ArrayList<>();
        try {
            List<InvoiceItem> items = getInvoiceItems(invoiceId, conn);
            for (InvoiceItem item : items) {
                Product p = new Product(item.getProductId(), item.getProductName(),
                        item.getQuantity(), item.getUnitPrice(), item.getUnitPrice());
                p.setSku(item.getSku());
                products.add(p);
            }
        } catch (SQLException ignored) {}
        return products;
    }
}
