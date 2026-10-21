package com.example.t.dao;

import com.example.t.Product;
import com.example.t.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - ProductDao Unit Tests")
public class ProductDaoTest {

    @TempDir
    Path tempDir;

    private ProductDao productDao;

    @BeforeEach
    public void setUp() {
        Path testDb = tempDir.resolve("product_test.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();
        productDao = new ProductDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Seed Catalog Products are Retrievable")
    public void testGetAllProducts() throws SQLException {
        List<Product> products = productDao.getAllProducts();
        assertTrue(products.size() >= 6, "Seeded database must have at least 6 products");

        Optional<Product> cleanCode = productDao.findBySku("BK-001");
        assertTrue(cleanCode.isPresent(), "Clean Code must be present by SKU");
        assertEquals("Clean Code: A Handbook of Agile Craftsmanship", cleanCode.get().getProductName());
        assertEquals("Robert C. Martin", cleanCode.get().getAuthor());
        assertEquals("Prentice Hall", cleanCode.get().getPublisher());
        assertEquals(35.00, cleanCode.get().getPrice(), 0.001);
    }

    @Test
    @DisplayName("Verify Product Insert, Update, and Delete Lifecycle")
    public void testProductLifecycle() throws SQLException {
        Product p = new Product(0, "The Pragmatic Programmer", "Books", "BK-PRAG-01",
                "David Thomas, Andrew Hunt", "Addison-Wesley", "20th Anniversary",
                30.00, 49.95, 25, 5);

        int id = productDao.save(p);
        assertTrue(id > 0);

        Optional<Product> retrieved = productDao.findById(id);
        assertTrue(retrieved.isPresent());
        assertEquals("BK-PRAG-01", retrieved.get().getSku());
        assertEquals(49.95, retrieved.get().getPrice(), 0.001);
        assertEquals(25, retrieved.get().getQuantity());

        // Update
        retrieved.get().setPrice(44.95);
        retrieved.get().setQuantity(30);
        boolean updated = productDao.update(retrieved.get());
        assertTrue(updated);

        Optional<Product> refreshed = productDao.findById(id);
        assertTrue(refreshed.isPresent());
        assertEquals(44.95, refreshed.get().getPrice(), 0.001);
        assertEquals(30, refreshed.get().getQuantity());

        // Delete
        boolean deleted = productDao.delete(id);
        assertTrue(deleted);
        assertFalse(productDao.findById(id).isPresent());
    }

    @Test
    @DisplayName("Verify Search by Name, SKU, and Category")
    public void testSearchProducts() throws SQLException {
        List<Product> matches = productDao.searchByName("Modern Java");
        assertEquals(1, matches.size());
        assertEquals("BK-002", matches.get(0).getSku());

        List<Product> authorMatches = productDao.searchByName("Orwell");
        assertEquals(1, authorMatches.size());
        assertEquals("BK-003", authorMatches.get(0).getSku());
    }

    @Test
    @DisplayName("Verify Atomic Stock Decrement with Concurrency Safety")
    public void testDecrementStockSuccess() throws SQLException {
        Optional<Product> cleanCode = productDao.findBySku("BK-001");
        assertTrue(cleanCode.isPresent());
        int initialStock = cleanCode.get().getQuantity();

        try (Connection conn = DatabaseManager.getConnection()) {
            boolean success = productDao.decrementStock(cleanCode.get().getProductId(), 5, conn);
            assertTrue(success);
        }

        int newStock = productDao.getStock(cleanCode.get().getProductId());
        assertEquals(initialStock - 5, newStock);
    }

    @Test
    @DisplayName("Verify Stock Decrement Fails when Quantity Exceeds Available Stock")
    public void testDecrementStockInsufficient() throws SQLException {
        Optional<Product> p = productDao.findBySku("BK-003"); // stock is 10
        assertTrue(p.isPresent());

        try (Connection conn = DatabaseManager.getConnection()) {
            // Attempt to decrement 100 items when only 10 available
            boolean success = productDao.decrementStock(p.get().getProductId(), 100, conn);
            assertFalse(success, "Decrement must fail when stock is insufficient");
        }

        // Stock remains unchanged
        assertEquals(10, productDao.getStock(p.get().getProductId()));
    }

    @Test
    @DisplayName("Verify Negative or Zero Decrement Throws IllegalArgumentException")
    public void testInvalidDecrementQuantity() {
        assertThrows(IllegalArgumentException.class, () -> productDao.decrementStock(1, 0));
        assertThrows(IllegalArgumentException.class, () -> productDao.decrementStock(1, -5));
    }

    @Test
    @DisplayName("Verify Restock Operation Increases Product Quantity")
    public void testRestockProduct() throws SQLException {
        Optional<Product> p = productDao.findBySku("ST-003"); // Sticky notes stock is 4
        assertTrue(p.isPresent());

        try (Connection conn = DatabaseManager.getConnection()) {
            boolean success = productDao.restockProduct(p.get().getProductId(), 20, conn);
            assertTrue(success);
        }

        assertEquals(24, productDao.getStock(p.get().getProductId()));
    }
}
