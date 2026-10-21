package com.example.t.database;

import com.example.t.Logger;
import com.example.t.PasswordUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.stream.Collectors;

/**
 * Manages database schema versioning, migrations, and standard catalog seeding.
 */
public class SchemaMigrationManager {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    /**
     * Executes pending migrations on the given connection.
     *
     * @param connection active SQLite database connection
     * @return true if migrations were applied, false if already up-to-date
     * @throws SQLException on database migration error
     */
    public static boolean migrate(Connection connection) throws SQLException {
        ensureMigrationsTable(connection);
        int currentVersion = getCurrentVersion(connection);

        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            Logger.info("Applying schema migration from version " + currentVersion + " to " + CURRENT_SCHEMA_VERSION);
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (currentVersion < 1) {
                    applyV1Schema(connection);
                    seedDefaultCategories(connection);
                    seedSampleProducts(connection);
                    seedDefaultSettings(connection);
                    recordMigration(connection, 1, "Initial 6-table normalized schema and default seed catalog");
                    Logger.info("Schema migration to version 1 successfully applied.");
                }

                if (currentVersion < 2) {
                    applyV2Schema(connection);
                    recordMigration(connection, 2, "V2 enhancements: catalog_cache, invoice_payments, financial_z_reports, and invoice_items additions");
                    Logger.info("Schema migration to version 2 successfully applied.");
                }

                connection.commit();
                Logger.info("Schema migration to version " + CURRENT_SCHEMA_VERSION + " successfully committed.");
                return true;
            } catch (Exception e) {
                connection.rollback();
                Logger.error("Failed to apply schema migration, transaction rolled back: " + e.getMessage());
                throw new SQLException("Schema migration failed", e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }

        return false;
    }

    private static void ensureMigrationsTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                "version INTEGER PRIMARY KEY, " +
                "description TEXT, " +
                "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public static int getCurrentVersion(Connection connection) throws SQLException {
        String sql = "SELECT MAX(version) FROM schema_migrations;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static void recordMigration(Connection connection, int version, String description) throws SQLException {
        String sql = "INSERT OR REPLACE INTO schema_migrations (version, description, applied_at) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP);";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, version);
            pstmt.setString(2, description);
            pstmt.executeUpdate();
        }
    }

    private static void applyV1Schema(Connection connection) throws SQLException {
        String script = loadSqlScript("/com/example/t/db/V1__init_schema.sql");
        if (script == null || script.trim().isEmpty()) {
            Logger.warn("V1__init_schema.sql not found on classpath, executing embedded fallback DDL");
            executeFallbackDdl(connection);
            return;
        }

        // Remove single-line comments before splitting into statements
        StringBuilder cleanScript = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.startsWith("--")) {
                cleanScript.append(line).append("\n");
            }
        }

        String[] statements = cleanScript.toString().split(";");
        try (Statement stmt = connection.createStatement()) {
            for (String raw : statements) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    private static void applyV2Schema(Connection connection) throws SQLException {
        String script = loadSqlScript("/com/example/t/db/V2__v1_1_enhancements.sql");
        if (script == null || script.trim().isEmpty()) {
            Logger.warn("V2__v1_1_enhancements.sql not found on classpath, executing embedded fallback DDL for V2");
            executeFallbackV2Ddl(connection);
            return;
        }

        StringBuilder cleanScript = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.startsWith("--")) {
                cleanScript.append(line).append("\n");
            }
        }

        String[] statements = cleanScript.toString().split(";");
        try (Statement stmt = connection.createStatement()) {
            for (String raw : statements) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.toUpperCase().startsWith("ALTER TABLE INVOICE_ITEMS ADD COLUMN")) {
                        handleAlterTableInvoiceItems(connection, trimmed);
                    } else {
                        stmt.execute(trimmed);
                    }
                }
            }
        }
    }

    private static void handleAlterTableInvoiceItems(Connection connection, String sql) throws SQLException {
        String[] parts = sql.split("\\s+");
        if (parts.length >= 6) {
            String colName = parts[5];
            int colIdx = sql.indexOf(colName);
            String colDef = sql.substring(colIdx + colName.length()).trim();
            addColumnIfNotExists(connection, "invoice_items", colName, colDef);
        } else {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException ignored) {
                // Ignore if already applied
            }
        }
    }

    private static void addColumnIfNotExists(Connection connection, String tableName, String columnName, String columnDef) throws SQLException {
        boolean exists = false;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ");")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef + ";");
            } catch (SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    throw e;
                }
            }
        }
    }

    private static void executeFallbackV2Ddl(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS catalog_cache (" +
                    "isbn13 TEXT PRIMARY KEY, " +
                    "isbn10 TEXT COLLATE NOCASE, " +
                    "title TEXT NOT NULL, " +
                    "subtitle TEXT, " +
                    "authors TEXT, " +
                    "publisher TEXT, " +
                    "published_year TEXT, " +
                    "edition TEXT, " +
                    "format TEXT, " +
                    "genre TEXT, " +
                    "description TEXT, " +
                    "page_count INTEGER DEFAULT 0, " +
                    "cover_image_path TEXT, " +
                    "cover_url TEXT, " +
                    "source TEXT DEFAULT 'UNKNOWN', " +
                    "cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "raw_json TEXT" +
                    ");");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_catalog_cache_isbn10 ON catalog_cache(isbn10);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_catalog_cache_title ON catalog_cache(title);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_isbn ON products(isbn);");

            stmt.execute("CREATE TABLE IF NOT EXISTS invoice_payments (" +
                    "payment_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE, " +
                    "payment_type TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "tendered_amount REAL DEFAULT 0.0, " +
                    "change_due REAL DEFAULT 0.0, " +
                    "reference_info TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoice_payments_invoice ON invoice_payments(invoice_id);");

            stmt.execute("CREATE TABLE IF NOT EXISTS financial_z_reports (" +
                    "z_report_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "z_sequence_number INTEGER UNIQUE NOT NULL, " +
                    "report_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "period_start TIMESTAMP NOT NULL, " +
                    "period_end TIMESTAMP NOT NULL, " +
                    "opening_float REAL DEFAULT 0.0, " +
                    "total_cash_sales REAL NOT NULL, " +
                    "total_card_sales REAL NOT NULL, " +
                    "total_credit_sales REAL NOT NULL, " +
                    "gross_sales REAL NOT NULL, " +
                    "total_discount REAL NOT NULL, " +
                    "taxable_sales REAL NOT NULL, " +
                    "total_tax REAL NOT NULL, " +
                    "net_sales REAL NOT NULL, " +
                    "transaction_count INTEGER NOT NULL, " +
                    "expected_drawer_cash REAL NOT NULL, " +
                    "actual_drawer_cash REAL NOT NULL, " +
                    "cash_over_short REAL NOT NULL, " +
                    "cumulative_grand_total REAL NOT NULL, " +
                    "closed_by_user TEXT DEFAULT 'admin', " +
                    "notes TEXT" +
                    ");");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_financial_z_reports_seq ON financial_z_reports(z_sequence_number);");
        }
        addColumnIfNotExists(connection, "invoice_items", "discount_percent", "REAL DEFAULT 0.0");
        addColumnIfNotExists(connection, "invoice_items", "discount_amount", "REAL DEFAULT 0.0");
        addColumnIfNotExists(connection, "invoice_items", "notes", "TEXT");
    }

    private static void executeFallbackDdl(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS categories (" +
                    "category_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL UNIQUE, " +
                    "description TEXT" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS products (" +
                    "product_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sku TEXT UNIQUE NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "category_id INTEGER REFERENCES categories(category_id) ON DELETE SET NULL, " +
                    "price REAL NOT NULL, " +
                    "purchase_price REAL DEFAULT 0.0, " +
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
                    "product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE, " +
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
        }
    }

    private static void seedDefaultCategories(Connection connection) throws SQLException {
        String sql = "INSERT OR IGNORE INTO categories (name, description) VALUES (?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Category 1: Books
            pstmt.setString(1, "Books");
            pstmt.setString(2, "Printed books, literature and academic texts");
            pstmt.executeUpdate();

            // Category 2: Stationery
            pstmt.setString(1, "Stationery");
            pstmt.setString(2, "Office, school and art supplies");
            pstmt.executeUpdate();

            // Category 3: Magazines
            pstmt.setString(1, "Magazines");
            pstmt.setString(2, "Periodicals, journals and publications");
            pstmt.executeUpdate();

            // Category 4: Office Supplies
            pstmt.setString(1, "Office Supplies");
            pstmt.setString(2, "Desk accessories, files and organizers");
            pstmt.executeUpdate();
        }
    }

    private static void seedSampleProducts(Connection connection) throws SQLException {
        int bookCatId = getCategoryId(connection, "Books");
        int statCatId = getCategoryId(connection, "Stationery");

        String sql = "INSERT OR IGNORE INTO products (sku, name, category_id, price, purchase_price, " +
                "stock_quantity, min_stock_threshold, author, publisher, isbn, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE');";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Books
            insertProductSeed(pstmt, "BK-001", "Clean Code: A Handbook of Agile Craftsmanship",
                    bookCatId, 35.00, 22.00, 50, 5, "Robert C. Martin", "Prentice Hall", "978-0132350884");

            insertProductSeed(pstmt, "BK-002", "Modern Java in Action (JDK 21 Edition)",
                    bookCatId, 45.00, 28.00, 30, 5, "Raoul-Gabriel Urma", "Manning", "978-1617293566");

            insertProductSeed(pstmt, "BK-003", "1984",
                    bookCatId, 15.00, 8.50, 10, 5, "George Orwell", "Secker & Warburg", "978-0451524935");

            // Stationery
            insertProductSeed(pstmt, "ST-001", "A5 Spiral Hardcover Notebook",
                    statCatId, 4.50, 2.10, 100, 10, null, "Oxford Stationery", null);

            insertProductSeed(pstmt, "ST-002", "Premium Gel Ink Pen 0.5mm (Pack of 3)",
                    statCatId, 6.00, 3.20, 75, 10, null, "Pilot Supplies", null);

            insertProductSeed(pstmt, "ST-003", "Yellow Sticky Notes Pad 3x3",
                    statCatId, 2.50, 1.00, 4, 5, null, "PostIt Stationery", null);
        }
    }

    private static void insertProductSeed(PreparedStatement pstmt, String sku, String name, int catId,
                                          double price, double cost, int stock, int minStock,
                                          String author, String publisher, String isbn) throws SQLException {
        pstmt.setString(1, sku);
        pstmt.setString(2, name);
        if (catId > 0) {
            pstmt.setInt(3, catId);
        } else {
            pstmt.setNull(3, Types.INTEGER);
        }
        pstmt.setDouble(4, price);
        pstmt.setDouble(5, cost);
        pstmt.setInt(6, stock);
        pstmt.setInt(7, minStock);
        pstmt.setString(8, author);
        pstmt.setString(9, publisher);
        pstmt.setString(10, isbn);
        pstmt.executeUpdate();
    }

    private static void seedDefaultSettings(Connection connection) throws SQLException {
        String defaultAdminHash = PasswordUtil.hashPassword("admin");
        String sql = "INSERT OR IGNORE INTO store_settings (setting_id, store_name, store_address, store_phone, " +
                "region_preset, currency_code, currency_symbol, tax_name, tax_rate, receipt_language, admin_password_hash) " +
                "VALUES (1, 'GM Bookstore & Stationery', '123 Main Street', '0300 0000000', " +
                "'USA', 'USD', '$', 'Sales Tax', 8.25, 'EN', ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, defaultAdminHash);
            pstmt.executeUpdate();
        }
    }

    private static int getCategoryId(Connection connection, String name) throws SQLException {
        String sql = "SELECT category_id FROM categories WHERE name = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    private static String loadSqlScript(String path) {
        try (InputStream is = SchemaMigrationManager.class.getResourceAsStream(path)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            Logger.warn("Failed to load SQL script from " + path + ": " + e.getMessage());
            return null;
        }
    }
}
