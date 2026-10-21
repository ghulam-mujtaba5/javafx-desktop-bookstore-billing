package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 1: Product & Catalog Management E2E Tests")
public class ProductManagementE2ETest {

    private E2ETestHarness harness;
    private SqliteTestDatabase db;

    @BeforeEach
    public void setUp() throws Exception {
        harness = new E2ETestHarness();
        db = harness.getDatabase();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("F1.1: Add Book Product with Author, Publisher, and ISBN metadata")
    public void testAddBookProduct() throws Exception {
        int id = db.insertProduct("BK-TEST-1", "Design Patterns: Elements of Reusable Object-Oriented Software",
                harness.getBookCategoryId(), 54.99, 32.00, 20, 3,
                "Erich Gamma et al.", "Addison-Wesley", "978-0201633610");

        assertTrue(id > 0, "Product ID must be greater than 0");

        Map<String, Object> product = db.getProductById(id);
        assertNotNull(product, "Inserted book must be retrievable by ID");
        assertEquals("BK-TEST-1", product.get("sku"));
        assertEquals("Design Patterns: Elements of Reusable Object-Oriented Software", product.get("name"));
        assertEquals(harness.getBookCategoryId(), product.get("category_id"));
        assertEquals(54.99, (Double) product.get("price"), 0.001);
        assertEquals("Erich Gamma et al.", product.get("author"));
        assertEquals("978-0201633610", product.get("isbn"));
        assertEquals("ACTIVE", product.get("status"));
    }

    @Test
    @DisplayName("F1.2: Add Stationery Product with Barcode without Book-specific metadata")
    public void testAddStationeryProduct() throws Exception {
        int id = db.insertProduct("BAR-990123", "Faber-Castell 2B Graphite Pencils (Box of 12)",
                harness.getStationeryCategoryId(), 7.50, 3.80, 40, 5,
                null, "Faber-Castell", null);

        assertTrue(id > 0);
        Map<String, Object> product = db.getProductById(id);
        assertNotNull(product);
        assertEquals("BAR-990123", product.get("sku"));
        assertEquals(harness.getStationeryCategoryId(), product.get("category_id"));
        assertNull(product.get("author"), "Stationery items should have null author");
        assertNull(product.get("isbn"), "Stationery items should have null ISBN");
        assertEquals(7.50, (Double) product.get("price"), 0.001);
    }

    @Test
    @DisplayName("F1.3: Retrieve Product by Unique SKU/Barcode")
    public void testFindProductBySku() throws Exception {
        Map<String, Object> product = db.getProductBySku("BK-001");
        assertNotNull(product, "Clean Code must be retrievable by SKU BK-001");
        assertEquals("Clean Code: A Handbook of Agile Craftsmanship", product.get("name"));
        assertEquals(35.00, (Double) product.get("price"), 0.001);
        assertEquals(50, product.get("stock_quantity"));
    }

    @Test
    @DisplayName("F1.4: Update Product Price and Name in Catalog")
    public void testUpdateProductDetails() throws Exception {
        int targetId = harness.getOrwellId();
        boolean updated = db.updateProduct(targetId, "1984 - Centenary Collector Edition", 19.99, 25);
        assertTrue(updated, "Update query must succeed");

        Map<String, Object> refreshed = db.getProductById(targetId);
        assertEquals("1984 - Centenary Collector Edition", refreshed.get("name"));
        assertEquals(19.99, (Double) refreshed.get("price"), 0.001);
        assertEquals(25, refreshed.get("stock_quantity"));
    }

    @Test
    @DisplayName("F1.5: Delete Product and Confirm Removal from Catalog")
    public void testDeleteProduct() throws Exception {
        int tempId = db.insertProduct("BK-TEMP", "Obsolete Book", harness.getBookCategoryId(),
                5.00, 2.00, 1, 1, "Author", "Pub", "000-0000000000");

        assertNotNull(db.getProductById(tempId));
        boolean deleted = db.deleteProduct(tempId);
        assertTrue(deleted, "Delete operation must return true");

        assertNull(db.getProductById(tempId), "Deleted product must not be found by ID");
        assertNull(db.getProductBySku("BK-TEMP"), "Deleted product must not be found by SKU");
    }

    @Test
    @DisplayName("F1.6: List All Products across Categorized Catalog")
    public void testListAllProducts() throws Exception {
        List<Map<String, Object>> all = db.getAllProducts();
        assertTrue(all.size() >= 6, "Initial seeded catalog should have at least 6 products");

        long bookCount = all.stream().filter(p -> p.get("category_id").equals(harness.getBookCategoryId())).count();
        long stationeryCount = all.stream().filter(p -> p.get("category_id").equals(harness.getStationeryCategoryId())).count();

        assertEquals(3, bookCount, "Seeded catalog must contain 3 books");
        assertEquals(3, stationeryCount, "Seeded catalog must contain 3 stationery items");
    }
}
