package com.example.t.service;

import com.example.t.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dead Stock Radar & Vendor Return Analysis Tests")
public class DeadStockRadarServiceTest {

    private Path tempDb;
    private DeadStockRadarService radarService;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        tempDb = Files.createTempFile("test_deadstock_", ".db");
        DatabaseManager.setCustomDatabasePath(tempDb);
        DatabaseManager.initializeDatabase();
        radarService = new DeadStockRadarService();
    }

    @AfterEach
    void tearDown() throws IOException {
        DatabaseManager.resetDatabasePath();
        try {
            Files.deleteIfExists(tempDb);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Identifies items with zero sales and inactive inventory older than threshold")
    void testFindDeadStock() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Insert distinct publisher products
            stmt.executeUpdate("INSERT INTO products (name, sku, category_id, price, purchase_price, stock_quantity, min_stock_threshold, author, publisher) " +
                    "VALUES ('Obsolete Manual', 'OBS-001', 1, 29.99, 12.00, 15, 2, 'Old Author', 'Vintage Press');");

            stmt.executeUpdate("INSERT INTO products (name, sku, category_id, price, purchase_price, stock_quantity, min_stock_threshold, author, publisher) " +
                    "VALUES ('Rare Anthology', 'RARE-002', 1, 49.99, 25.00, 4, 1, 'Classic Poet', 'Vintage Press');");

            stmt.executeUpdate("INSERT INTO products (name, sku, category_id, price, purchase_price, stock_quantity, min_stock_threshold, author, publisher) " +
                    "VALUES ('Zero Stock Item', 'ZERO-003', 1, 10.00, 5.00, 0, 1, 'Ghost Author', 'Ghost Press');");
        }

        List<DeadStockRadarService.DeadStockItem> deadStock = radarService.findDeadStock(30);
        assertNotNull(deadStock);
        assertFalse(deadStock.isEmpty());

        // Zero-stock items must NOT be included in dead stock
        boolean hasZeroStock = deadStock.stream().anyMatch(i -> i.getStock() <= 0);
        assertFalse(hasZeroStock, "Zero stock items must not be flagged as dead stock");

        // Verify potential return credit calculation (cost_price * stock)
        DeadStockRadarService.DeadStockItem manual = deadStock.stream()
                .filter(i -> "OBS-001".equals(i.getSku()))
                .findFirst()
                .orElse(null);

        assertNotNull(manual);
        assertEquals(15, manual.getStock());
        assertEquals(12.00, manual.getCostPrice());
        assertEquals(180.00, manual.getPotentialCredit()); // 15 * 12.00 = 180.00
    }

    @Test
    @DisplayName("Groups dead stock items by publisher/distributor for return claims")
    void testGroupByPublisher() {
        DeadStockRadarService.DeadStockItem item1 = new DeadStockRadarService.DeadStockItem(
                1, "Book A", "SKU1", "Author 1", "Penguin Random House", 10, 8.00, 15.00, null, 120
        );
        DeadStockRadarService.DeadStockItem item2 = new DeadStockRadarService.DeadStockItem(
                2, "Book B", "SKU2", "Author 2", "Penguin Random House", 5, 10.00, 20.00, null, 95
        );
        DeadStockRadarService.DeadStockItem item3 = new DeadStockRadarService.DeadStockItem(
                3, "Book C", "SKU3", "Author 3", "HarperCollins", 8, 6.00, 12.00, null, 60
        );

        Map<String, List<DeadStockRadarService.DeadStockItem>> grouped = radarService.groupByPublisher(List.of(item1, item2, item3));
        assertNotNull(grouped);
        assertEquals(2, grouped.size());
        assertTrue(grouped.containsKey("Penguin Random House"));
        assertTrue(grouped.containsKey("HarperCollins"));
        assertEquals(2, grouped.get("Penguin Random House").size());
        assertEquals(1, grouped.get("HarperCollins").size());
    }

    @Test
    @DisplayName("Generates RFC 4180 compliant vendor return CSV manifest")
    void testGenerateVendorReturnCsv() {
        DeadStockRadarService.DeadStockItem item = new DeadStockRadarService.DeadStockItem(
                1, "The Great Gatsby, Special Edition", "ISBN-9780743273565", "F. Scott Fitzgerald", "Scribner", 12, 9.50, 16.00, null, 150
        );

        String csv = radarService.generateVendorReturnCsv(List.of(item));
        assertNotNull(csv);
        assertTrue(csv.contains("SKU/ISBN,Title,Author,Publisher"));
        assertTrue(csv.contains("\"The Great Gatsby, Special Edition\"")); // Quoted because of comma
        assertTrue(csv.contains("Scribner"));
        assertTrue(csv.contains("114.00")); // 12 * 9.50 = 114.00
        assertTrue(csv.contains("NEVER SOLD"));
    }
}
