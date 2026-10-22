package com.example.t.service;

import com.example.t.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Dead Stock Radar & Publisher Return Sheet Generator.
 * Detects slow-moving and non-moving inventory (e.g. 60, 90, 180+ days without sales),
 * groups return candidates by publisher/distributor, and generates vendor credit return manifests.
 */
public class DeadStockRadarService {

    public static class DeadStockItem {
        private final int productId;
        private final String name;
        private final String sku;
        private final String author;
        private final String publisher;
        private final int stock;
        private final double costPrice;
        private final double retailPrice;
        private final LocalDate lastSaleDate;
        private final int daysIdle;

        public DeadStockItem(int productId, String name, String sku, String author, String publisher,
                             int stock, double costPrice, double retailPrice, LocalDate lastSaleDate, int daysIdle) {
            this.productId = productId;
            this.name = name != null ? name : "Unknown";
            this.sku = sku != null ? sku : "";
            this.author = author != null ? author : "";
            this.publisher = publisher != null && !publisher.isBlank() ? publisher : "Independent / Unassigned";
            this.stock = stock;
            this.costPrice = costPrice;
            this.retailPrice = retailPrice;
            this.lastSaleDate = lastSaleDate;
            this.daysIdle = daysIdle;
        }

        public int getProductId() { return productId; }
        public String getName() { return name; }
        public String getSku() { return sku; }
        public String getAuthor() { return author; }
        public String getPublisher() { return publisher; }
        public int getStock() { return stock; }
        public double getCostPrice() { return costPrice; }
        public double getRetailPrice() { return retailPrice; }
        public LocalDate getLastSaleDate() { return lastSaleDate; }
        public int getDaysIdle() { return daysIdle; }

        /** Total cost tied up in this dead stock item */
        public double getPotentialCredit() {
            double effectiveCost = costPrice > 0.0 ? costPrice : retailPrice * 0.6; // fallback 40% margin estimate
            return BillingService.roundMoney(effectiveCost * stock);
        }
    }

    /**
     * Analyzes local SQLite database for items currently in stock with zero sales in the specified window.
     * @param thresholdDays Minimum days without a sale (e.g. 30, 60, 90, 180).
     */
    public List<DeadStockItem> findDeadStock(int thresholdDays) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findDeadStock(thresholdDays, conn);
        }
    }

    public List<DeadStockItem> findDeadStock(int thresholdDays, Connection conn) throws SQLException {
        List<DeadStockItem> items = new ArrayList<>();
        int safeThreshold = Math.max(0, thresholdDays);

        String sql = "SELECT p.product_id, p.name, p.sku, p.author, p.publisher, p.stock_quantity, p.purchase_price, p.price, " +
                "MAX(i.invoice_date) as last_sale_date, " +
                "CAST(julianday('now') - julianday(COALESCE(MAX(i.invoice_date), '2000-01-01')) AS INTEGER) as days_idle " +
                "FROM products p " +
                "LEFT JOIN invoice_items ii ON p.product_id = ii.product_id " +
                "LEFT JOIN invoices i ON ii.invoice_id = i.invoice_id " +
                "WHERE p.stock_quantity > 0 " +
                "GROUP BY p.product_id " +
                "HAVING MAX(i.invoice_date) IS NULL OR days_idle >= ? " +
                "ORDER BY days_idle DESC, p.stock_quantity DESC;";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, safeThreshold);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("product_id");
                    String name = rs.getString("name");
                    String sku = rs.getString("sku");
                    String author = rs.getString("author");
                    String publisher = rs.getString("publisher");
                    int stock = rs.getInt("stock_quantity");
                    double costPrice = rs.getDouble("purchase_price");
                    double retailPrice = rs.getDouble("price");

                    String dateStr = rs.getString("last_sale_date");
                    LocalDate lastDate = null;
                    if (dateStr != null && dateStr.length() >= 10) {
                        try {
                            lastDate = LocalDate.parse(dateStr.substring(0, 10));
                        } catch (Exception ignored) {}
                    }
                    int daysIdle = rs.getInt("days_idle");

                    items.add(new DeadStockItem(id, name, sku, author, publisher, stock, costPrice, retailPrice, lastDate, daysIdle));
                }
            }
        }
        return items;
    }

    /**
     * Groups dead stock items by publisher/distributor for bulk return requests.
     */
    public Map<String, List<DeadStockItem>> groupByPublisher(List<DeadStockItem> items) {
        Map<String, List<DeadStockItem>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (items != null) {
            for (DeadStockItem item : items) {
                map.computeIfAbsent(item.getPublisher(), k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }

    /**
     * Exports a vendor return manifest in RFC 4180 CSV format.
     */
    public String generateVendorReturnCsv(List<DeadStockItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("SKU/ISBN,Title,Author,Publisher,Units In Stock,Unit Cost,Estimated Return Credit,Last Sale Date,Days Inactive\r\n");

        if (items != null) {
            for (DeadStockItem item : items) {
                sb.append(escape(item.getSku())).append(",");
                sb.append(escape(item.getName())).append(",");
                sb.append(escape(item.getAuthor())).append(",");
                sb.append(escape(item.getPublisher())).append(",");
                sb.append(item.getStock()).append(",");
                sb.append(String.format(Locale.US, "%.2f", item.getCostPrice())).append(",");
                sb.append(String.format(Locale.US, "%.2f", item.getPotentialCredit())).append(",");
                sb.append(item.getLastSaleDate() != null ? item.getLastSaleDate().toString() : "NEVER SOLD").append(",");
                sb.append(item.getDaysIdle()).append("\r\n");
            }
        }
        return sb.toString();
    }

    public void exportVendorReturnCsvToFile(Path destination, List<DeadStockItem> items) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.writeString(destination, generateVendorReturnCsv(items));
    }

    private static String escape(String field) {
        if (field == null) return "\"\"";
        if (field.contains("\"") || field.contains(",") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
