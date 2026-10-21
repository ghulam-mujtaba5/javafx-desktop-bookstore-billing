package com.example.t.e2e.harness;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded SQLite Test Database manager for E2E tests.
 * Initializes and manages a hermetic SQLite instance matching the 6-table normalized DDL
 * specified in PROJECT.md:
 * - schema_migrations
 * - categories
 * - products
 * - invoices
 * - invoice_items
 * - store_settings
 *
 * Configures PRAGMA journal_mode = WAL and PRAGMA foreign_keys = ON.
 */
public class SqliteTestDatabase implements AutoCloseable {

    private final Connection connection;
    private final Path dbPath;

    public SqliteTestDatabase(Path dbPath) throws SQLException {
        this.dbPath = dbPath;
        String url = dbPath == null ? "jdbc:sqlite::memory:" : "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
        this.connection = DriverManager.getConnection(url);
        initPragmas();
        initSchema();
    }

    public Connection getConnection() {
        return connection;
    }

    public Path getDbPath() {
        return dbPath;
    }

    private void initPragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            if (dbPath != null) {
                // WAL mode is persistent on file-based SQLite databases
                stmt.execute("PRAGMA journal_mode = WAL;");
            }
        }
    }

    public String getJournalMode() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
            if (rs.next()) {
                return rs.getString(1).toLowerCase();
            }
        }
        return "unknown";
    }

    public boolean areForeignKeysEnabled() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
            if (rs.next()) {
                return rs.getInt(1) == 1;
            }
        }
        return false;
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS categories (" +
                    "category_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL UNIQUE, " +
                    "description TEXT" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS products (" +
                    "product_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sku TEXT UNIQUE NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "category_id INTEGER REFERENCES categories(category_id), " +
                    "price REAL NOT NULL, " +
                    "purchase_price REAL, " +
                    "stock_quantity INTEGER NOT NULL DEFAULT 0, " +
                    "min_stock_threshold INTEGER DEFAULT 5, " +
                    "author TEXT, " +
                    "publisher TEXT, " +
                    "isbn TEXT, " +
                    "status TEXT DEFAULT 'ACTIVE', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS invoices (" +
                    "invoice_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "invoice_number TEXT UNIQUE NOT NULL, " +
                    "customer_name TEXT DEFAULT 'Walk-in Customer', " +
                    "customer_phone TEXT, " +
                    "invoice_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "subtotal REAL NOT NULL, " +
                    "discount_percent REAL DEFAULT 0.0, " +
                    "discount_amount REAL DEFAULT 0.0, " +
                    "tax_rate REAL DEFAULT 0.0, " +
                    "tax_amount REAL DEFAULT 0.0, " +
                    "net_total REAL NOT NULL, " +
                    "cash_tendered REAL NOT NULL, " +
                    "change_due REAL NOT NULL, " +
                    "payment_method TEXT DEFAULT 'CASH', " +
                    "currency_code TEXT DEFAULT 'USD', " +
                    "region TEXT DEFAULT 'USA'" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS invoice_items (" +
                    "item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE, " +
                    "product_id INTEGER NOT NULL REFERENCES products(product_id), " +
                    "quantity INTEGER NOT NULL, " +
                    "unit_price REAL NOT NULL, " +
                    "line_total REAL NOT NULL" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS store_settings (" +
                    "setting_id INTEGER PRIMARY KEY CHECK (setting_id = 1), " +
                    "store_name TEXT NOT NULL, " +
                    "store_address TEXT, " +
                    "store_phone TEXT, " +
                    "region_preset TEXT NOT NULL DEFAULT 'USA', " +
                    "currency_code TEXT NOT NULL DEFAULT 'USD', " +
                    "currency_symbol TEXT NOT NULL DEFAULT '$', " +
                    "tax_name TEXT NOT NULL DEFAULT 'Sales Tax', " +
                    "tax_rate REAL NOT NULL DEFAULT 0.0, " +
                    "receipt_language TEXT NOT NULL DEFAULT 'EN', " +
                    "admin_password_hash TEXT NOT NULL" +
                    ");");

            // Record initial migration
            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version) VALUES (1);");

            // Seed default settings if missing
            stmt.execute("INSERT OR IGNORE INTO store_settings (setting_id, store_name, store_address, store_phone, " +
                    "region_preset, currency_code, currency_symbol, tax_name, tax_rate, receipt_language, admin_password_hash) " +
                    "VALUES (1, 'GM Bookstore & Stationery', '123 Main Street', '0300 0000000', " +
                    "'USA', 'USD', '$', 'Sales Tax', 8.25, 'EN', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918');");
        }
    }

    public int insertCategory(String name, String description) throws SQLException {
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public int insertProduct(String sku, String name, int categoryId, double price, double purchasePrice,
                             int stock, int minStock, String author, String publisher, String isbn) throws SQLException {
        String sql = "INSERT INTO products (sku, name, category_id, price, purchase_price, stock_quantity, " +
                "min_stock_threshold, author, publisher, isbn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sku);
            pstmt.setString(2, name);
            pstmt.setInt(3, categoryId);
            pstmt.setDouble(4, price);
            pstmt.setDouble(5, purchasePrice);
            pstmt.setInt(6, stock);
            pstmt.setInt(7, minStock);
            pstmt.setString(8, author);
            pstmt.setString(9, publisher);
            pstmt.setString(10, isbn);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public Map<String, Object> getProductById(int productId) throws SQLException {
        String sql = "SELECT * FROM products WHERE product_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapCurrentRow(rs);
                }
            }
        }
        return null;
    }

    public Map<String, Object> getProductBySku(String sku) throws SQLException {
        String sql = "SELECT * FROM products WHERE sku = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sku);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapCurrentRow(rs);
                }
            }
        }
        return null;
    }

    public List<Map<String, Object>> getAllProducts() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY product_id ASC;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCurrentRow(rs));
            }
        }
        return list;
    }

    public boolean updateProduct(int productId, String name, double price, int stock) throws SQLException {
        String sql = "UPDATE products SET name = ?, price = ?, stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE product_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setDouble(2, price);
            pstmt.setInt(3, stock);
            pstmt.setInt(4, productId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProduct(int productId) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean decrementStock(int productId, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to decrement must be positive: " + quantity);
        }
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE product_id = ? AND stock_quantity >= ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, quantity);
            pstmt.setInt(2, productId);
            pstmt.setInt(3, quantity);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean restockProduct(int productId, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to restock must be positive: " + quantity);
        }
        String sql = "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE product_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, quantity);
            pstmt.setInt(2, productId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public int getProductStock(int productId) throws SQLException {
        Map<String, Object> product = getProductById(productId);
        if (product == null) return -1;
        return (Integer) product.get("stock_quantity");
    }

    /**
     * Executes atomic invoice creation with stock decrement for all line items.
     * Rolls back on any failure or insufficient stock.
     */
    public int createInvoiceWithTransaction(String invoiceNumber, String customerName, String customerPhone,
                                            double subtotal, double discountPercent, double discountAmount,
                                            double taxRate, double taxAmount, double netTotal,
                                            double cashTendered, double changeDue, String currencyCode,
                                            String region, List<CalculationOracle.CartItem> items) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            // 1. Decrement stock for all items
            for (CalculationOracle.CartItem item : items) {
                String decSql = "UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE product_id = ? AND stock_quantity >= ?;";
                try (PreparedStatement decStmt = connection.prepareStatement(decSql)) {
                    decStmt.setInt(1, item.getQuantity());
                    decStmt.setInt(2, item.getProductId());
                    decStmt.setInt(3, item.getQuantity());
                    int updated = decStmt.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Insufficient stock for product ID: " + item.getProductId());
                    }
                }
            }

            // 2. Insert invoice header
            int invoiceId;
            String invSql = "INSERT INTO invoices (invoice_number, customer_name, customer_phone, subtotal, " +
                    "discount_percent, discount_amount, tax_rate, tax_amount, net_total, cash_tendered, " +
                    "change_due, currency_code, region) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement invStmt = connection.prepareStatement(invSql, Statement.RETURN_GENERATED_KEYS)) {
                invStmt.setString(1, invoiceNumber);
                invStmt.setString(2, customerName != null && !customerName.trim().isEmpty() ? customerName : "Walk-in Customer");
                invStmt.setString(3, customerPhone);
                invStmt.setDouble(4, subtotal);
                invStmt.setDouble(5, discountPercent);
                invStmt.setDouble(6, discountAmount);
                invStmt.setDouble(7, taxRate);
                invStmt.setDouble(8, taxAmount);
                invStmt.setDouble(9, netTotal);
                invStmt.setDouble(10, cashTendered);
                invStmt.setDouble(11, changeDue);
                invStmt.setString(12, currencyCode);
                invStmt.setString(13, region);
                invStmt.executeUpdate();
                try (ResultSet rs = invStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        invoiceId = rs.getInt(1);
                    } else {
                        throw new SQLException("Failed to obtain invoice ID key.");
                    }
                }
            }

            // 3. Insert line items
            String itemSql = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                    "VALUES (?, ?, ?, ?, ?);";
            try (PreparedStatement itemStmt = connection.prepareStatement(itemSql)) {
                for (CalculationOracle.CartItem item : items) {
                    itemStmt.setInt(1, invoiceId);
                    itemStmt.setInt(2, item.getProductId());
                    itemStmt.setInt(3, item.getQuantity());
                    itemStmt.setDouble(4, item.getUnitPrice());
                    itemStmt.setDouble(5, item.getLineSubtotal());
                    itemStmt.addBatch();
                }
                itemStmt.executeBatch();
            }

            connection.commit();
            return invoiceId;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public Map<String, Object> getInvoiceById(int invoiceId) throws SQLException {
        String sql = "SELECT * FROM invoices WHERE invoice_id = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapCurrentRow(rs);
            }
        }
        return null;
    }

    public List<Map<String, Object>> getInvoiceItems(int invoiceId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT ii.*, p.name AS product_name, p.sku FROM invoice_items ii " +
                "JOIN products p ON ii.product_id = p.product_id WHERE ii.invoice_id = ? ORDER BY ii.item_id ASC;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, invoiceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCurrentRow(rs));
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAllInvoices() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM invoices ORDER BY invoice_id DESC;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCurrentRow(rs));
            }
        }
        return list;
    }

    public Map<String, Object> getStoreSettings() throws SQLException {
        String sql = "SELECT * FROM store_settings WHERE setting_id = 1;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapCurrentRow(rs);
            }
        }
        return null;
    }

    public boolean updateStoreSettings(String name, String address, String phone, String region,
                                       String currencyCode, String currencySymbol, String taxName,
                                       double taxRate, String language) throws SQLException {
        String sql = "UPDATE store_settings SET store_name = ?, store_address = ?, store_phone = ?, " +
                "region_preset = ?, currency_code = ?, currency_symbol = ?, tax_name = ?, tax_rate = ?, " +
                "receipt_language = ? WHERE setting_id = 1;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, address);
            pstmt.setString(3, phone);
            pstmt.setString(4, region);
            pstmt.setString(5, currencyCode);
            pstmt.setString(6, currencySymbol);
            pstmt.setString(7, taxName);
            pstmt.setDouble(8, taxRate);
            pstmt.setString(9, language);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Map<String, Object> mapCurrentRow(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();
        for (int i = 1; i <= count; i++) {
            map.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
        }
        return map;
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
