package com.example.t.database;

import com.example.t.Logger;
import com.example.t.PasswordUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Idempotent importer for legacy flat-text files:
 * - stock.txt
 * - Shop_Data.txt
 * - password.txt
 * - invoice_history.txt
 */
public class LegacyDataImporter {

    public static void importIfLegacyExists(Connection conn) {
        Path[] candidateDirs = new Path[]{
                Paths.get("src", "main"),
                Paths.get("."),
                Paths.get(System.getenv("APPDATA") != null ? System.getenv("APPDATA") : ".", "GM Shop")
        };

        for (Path dir : candidateDirs) {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                if (Files.exists(dir.resolve("stock.txt")) ||
                        Files.exists(dir.resolve("Shop_Data.txt")) ||
                        Files.exists(dir.resolve("shop_data.txt")) ||
                        Files.exists(dir.resolve("password.txt")) ||
                        Files.exists(dir.resolve("invoice_history.txt"))) {
                    Logger.info("Found legacy files in directory: " + dir + ". Executing migration...");
                    importFromDirectory(conn, dir);
                    return;
                }
            }
        }
    }

    public static int importFromDirectory(Connection conn, Path dir) {
        int importedCount = 0;
        try {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                importedCount += importShopData(conn, dir);
                importedCount += importPassword(conn, dir);
                importedCount += importStock(conn, dir);
                importedCount += importInvoiceHistory(conn, dir);
                conn.commit();
                Logger.info("Legacy data import committed successfully. Records/components imported: " + importedCount);
            } catch (Exception e) {
                conn.rollback();
                Logger.error("Failed to import legacy data, rolling back: " + e.getMessage());
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            Logger.error("Database error during legacy data import: " + e.getMessage());
        }
        return importedCount;
    }

    public static int importShopData(Connection conn, Path dir) {
        Path path = dir.resolve("Shop_Data.txt");
        if (!Files.exists(path)) {
            path = dir.resolve("shop_data.txt");
        }
        if (!Files.exists(path)) {
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String name = reader.readLine();
            String address = reader.readLine();
            String phone = reader.readLine();

            if (name != null && !name.trim().isEmpty()) {
                String sql = "UPDATE store_settings SET store_name = ?, store_address = ?, store_phone = ? " +
                        "WHERE setting_id = 1;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, name.trim());
                    pstmt.setString(2, address != null ? address.trim() : "");
                    pstmt.setString(3, phone != null ? phone.trim() : "");
                    pstmt.executeUpdate();
                    Logger.info("Imported legacy Shop_Data: " + name);
                    return 1;
                }
            }
        } catch (Exception e) {
            Logger.warn("Could not import legacy Shop_Data.txt: " + e.getMessage());
        }
        return 0;
    }

    public static int importPassword(Connection conn, Path dir) {
        Path path = dir.resolve("password.txt");
        if (!Files.exists(path)) {
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String plainPassword = line.trim();
                if ("book shop".equalsIgnoreCase(plainPassword)) {
                    plainPassword = "admin";
                }
                String hashedPassword = PasswordUtil.hashPassword(plainPassword);
                String sql = "UPDATE store_settings SET admin_password_hash = ? WHERE setting_id = 1;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, hashedPassword);
                    pstmt.executeUpdate();
                    Logger.info("Imported legacy password and updated store settings");
                    return 1;
                }
            }
        } catch (Exception e) {
            Logger.warn("Could not import legacy password.txt: " + e.getMessage());
        }
        return 0;
    }

    public static int importStock(Connection conn, Path dir) {
        Path path = dir.resolve("stock.txt");
        if (!Files.exists(path)) {
            return 0;
        }

        int bookCatId = getOrCreateCategory(conn, "Books", "Printed books");
        int statCatId = getOrCreateCategory(conn, "Stationery", "Office supplies");

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    try {
                        int legacyId = Integer.parseInt(parts[0].trim());
                        String name = parts[1].trim();
                        int qty = Integer.parseInt(parts[2].trim());
                        double price = Double.parseDouble(parts[3].trim());
                        double cost = Double.parseDouble(parts[4].trim());
                        boolean status = parts.length > 5 ? Boolean.parseBoolean(parts[5].trim()) : true;

                        // Check if product already exists by name
                        if (productExistsByName(conn, name)) {
                            continue;
                        }

                        // Determine category and generate SKU
                        boolean isStat = isStationeryName(name);
                        int catId = isStat ? statCatId : bookCatId;
                        String skuPrefix = isStat ? "ST-LEG-" : "BK-LEG-";
                        String sku = skuPrefix + (legacyId > 0 ? legacyId : lineNum);

                        // Ensure SKU uniqueness
                        sku = ensureUniqueSku(conn, sku);

                        String sql = "INSERT INTO products (sku, name, category_id, price, purchase_price, " +
                                "stock_quantity, min_stock_threshold, status) VALUES (?, ?, ?, ?, ?, ?, 5, ?);";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setString(1, sku);
                            pstmt.setString(2, name);
                            pstmt.setInt(3, catId);
                            pstmt.setDouble(4, price);
                            pstmt.setDouble(5, cost);
                            pstmt.setInt(6, qty);
                            pstmt.setString(7, status ? "ACTIVE" : "INACTIVE");
                            pstmt.executeUpdate();
                            count++;
                        }
                    } catch (Exception ex) {
                        Logger.warn("Skipping invalid line " + lineNum + " in stock.txt: " + ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            Logger.warn("Failed reading stock.txt: " + e.getMessage());
        }

        Logger.info("Imported " + count + " products from legacy stock.txt");
        return count;
    }

    public static int importInvoiceHistory(Connection conn, Path dir) {
        Path path = dir.resolve("invoice_history.txt");
        if (!Files.exists(path)) {
            return 0;
        }

        int count = 0;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            int invoiceId = 0;
            LocalDate invoiceDate = LocalDate.now();
            String customerName = "Walk-in Customer";
            double totalAmount = 0.0;
            List<LegacyItem> items = new ArrayList<>();
            boolean inDetails = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    if (invoiceId > 0 || !items.isEmpty()) {
                        if (saveLegacyInvoice(conn, invoiceId, customerName, invoiceDate, totalAmount, items)) {
                            count++;
                        }
                        invoiceId = 0;
                        customerName = "Walk-in Customer";
                        totalAmount = 0.0;
                        items.clear();
                        inDetails = false;
                    }
                    continue;
                }

                if (line.startsWith("Invoice ID:")) {
                    try {
                        invoiceId = Integer.parseInt(line.replace("Invoice ID:", "").trim());
                    } catch (NumberFormatException ignored) {}
                } else if (line.startsWith("Order Date:")) {
                    try {
                        String dateStr = line.replace("Order Date:", "").trim();
                        invoiceDate = LocalDate.parse(dateStr, dateFormatter);
                    } catch (Exception ignored) {}
                } else if (line.startsWith("Customer Name:")) {
                    customerName = line.replace("Customer Name:", "").trim();
                } else if (line.startsWith("Total Amount:")) {
                    try {
                        String raw = line.replace("Total Amount:", "").replace("Rs", "").replace("$", "").trim();
                        totalAmount = Double.parseDouble(raw);
                    } catch (NumberFormatException ignored) {}
                } else if (line.startsWith("Order Details:") || line.startsWith("Product Name\t")) {
                    inDetails = true;
                } else if (inDetails) {
                    String[] tokens = line.split("\t+");
                    if (tokens.length >= 3) {
                        try {
                            String name = tokens[0].trim();
                            int qty = Integer.parseInt(tokens[1].trim());
                            double price = Double.parseDouble(tokens[2].trim());
                            items.add(new LegacyItem(name, qty, price));
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (invoiceId > 0 || !items.isEmpty()) {
                if (saveLegacyInvoice(conn, invoiceId, customerName, invoiceDate, totalAmount, items)) {
                    count++;
                }
            }
        } catch (IOException e) {
            Logger.warn("Failed reading invoice_history.txt: " + e.getMessage());
        }

        Logger.info("Imported " + count + " invoices from legacy invoice_history.txt");
        return count;
    }

    private static boolean saveLegacyInvoice(Connection conn, int legacyId, String customer,
                                             LocalDate date, double total, List<LegacyItem> items) {
        String invNum = "INV-LEGACY-" + (legacyId > 0 ? legacyId : System.currentTimeMillis());
        try {
            // Check if already imported
            String checkSql = "SELECT invoice_id FROM invoices WHERE invoice_number = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setString(1, invNum);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return false;
                }
            }

            int createdInvoiceId;
            String sql = "INSERT INTO invoices (invoice_number, customer_name, invoice_date, subtotal, " +
                    "discount_percent, discount_amount, tax_rate, tax_amount, net_total, cash_tendered, " +
                    "change_due, payment_method, currency_code, region) " +
                    "VALUES (?, ?, ?, ?, 0.0, 0.0, 0.0, 0.0, ?, ?, 0.0, 'CASH', 'USD', 'USA');";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, invNum);
                pstmt.setString(2, customer != null && !customer.isBlank() ? customer : "Walk-in Customer");
                pstmt.setString(3, date != null ? date.toString() + " 12:00:00" : LocalDate.now().toString());
                pstmt.setDouble(4, total);
                pstmt.setDouble(5, total);
                pstmt.setDouble(6, total);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        createdInvoiceId = rs.getInt(1);
                    } else {
                        return false;
                    }
                }
            }

            // Insert line items
            for (LegacyItem item : items) {
                int prodId = getOrCreateProductId(conn, item.name, item.price);
                String itemSql = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                        "VALUES (?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(itemSql)) {
                    pstmt.setInt(1, createdInvoiceId);
                    pstmt.setInt(2, prodId);
                    pstmt.setInt(3, item.quantity);
                    pstmt.setDouble(4, item.price);
                    pstmt.setDouble(5, item.price * item.quantity);
                    pstmt.executeUpdate();
                }
            }
            return true;
        } catch (SQLException e) {
            Logger.warn("Failed inserting legacy invoice " + invNum + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean productExistsByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT product_id FROM products WHERE name = ? COLLATE NOCASE;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String ensureUniqueSku(Connection conn, String baseSku) throws SQLException {
        String sku = baseSku;
        int counter = 1;
        while (true) {
            String sql = "SELECT product_id FROM products WHERE sku = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, sku);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return sku;
                    }
                }
            }
            sku = baseSku + "-" + counter++;
        }
    }

    private static int getOrCreateCategory(Connection conn, String name, String desc) {
        try {
            String sql = "SELECT category_id FROM categories WHERE name = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            String insertSql = "INSERT INTO categories (name, description) VALUES (?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, name);
                pstmt.setString(2, desc);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException ignored) {}
        return 1;
    }

    private static int getOrCreateProductId(Connection conn, String name, double price) throws SQLException {
        String sql = "SELECT product_id FROM products WHERE name = ? COLLATE NOCASE;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }

        // Insert new dummy product if not found
        String sku = ensureUniqueSku(conn, "LEG-" + Math.abs(name.hashCode()));
        String insertSql = "INSERT INTO products (sku, name, price, stock_quantity) VALUES (?, ?, ?, 0);";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sku);
            pstmt.setString(2, name);
            pstmt.setDouble(3, price);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 1;
    }

    private static boolean isStationeryName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("pen") || lower.contains("pencil") || lower.contains("notebook")
                || lower.contains("eraser") || lower.contains("ruler") || lower.contains("pad")
                || lower.contains("bag") || lower.contains("marker") || lower.contains("tape")
                || lower.contains("paper") || lower.contains("glue") || lower.contains("ink");
    }

    private static class LegacyItem {
        final String name;
        final int quantity;
        final double price;

        LegacyItem(String name, int quantity, double price) {
            this.name = name;
            this.quantity = quantity;
            this.price = price;
        }
    }
}
