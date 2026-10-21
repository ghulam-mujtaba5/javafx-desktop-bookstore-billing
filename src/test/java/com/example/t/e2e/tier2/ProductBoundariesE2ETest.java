package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 1: Product Management Boundary & Corner Cases")
public class ProductBoundariesE2ETest {

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
    @DisplayName("B1.1: Duplicate SKU Insertion fails with Unique Constraint Violation")
    public void testDuplicateSkuRejection() {
        // BK-001 already exists in seeded database
        assertThrows(SQLException.class, () -> {
            db.insertProduct("BK-001", "Duplicate Clean Code", harness.getBookCategoryId(),
                    40.00, 20.00, 10, 5, "Author", "Publisher", "ISBN");
        }, "Inserting a duplicate SKU must violate the UNIQUE(sku) constraint");
    }

    @Test
    @DisplayName("B1.2: Unicode Characters in Product Name (Arabic & Multilingual)")
    public void testUnicodeProductName() throws Exception {
        String arabicTitle = "مقدمة في الخوارزميات (Introduction to Algorithms)";
        int id = db.insertProduct("BK-AR-01", arabicTitle, harness.getBookCategoryId(),
                120.00, 70.00, 15, 2, "توماس كورمن", "دار المعرفة", "978-0262033848");

        Map<String, Object> retrieved = db.getProductById(id);
        assertNotNull(retrieved);
        assertEquals(arabicTitle, retrieved.get("name"), "Multilingual Arabic text must be stored and retrieved with full fidelity");
        assertEquals("توماس كورمن", retrieved.get("author"));
    }

    @Test
    @DisplayName("B1.3: Extremely Long Product Name (500 characters)")
    public void testExtremelyLongProductName() throws Exception {
        StringBuilder longName = new StringBuilder("Comprehensive Guide to Software Architecture: ");
        while (longName.length() < 500) {
            longName.append("Patterns, Practices, Enterprise Scalability, Robustness, and Cloud Infrastructure; ");
        }
        String name500 = longName.substring(0, 500);

        int id = db.insertProduct("BK-LONG-1", name500, harness.getBookCategoryId(),
                99.00, 50.00, 10, 2, "Architect", "Publisher", "123-4567890123");

        Map<String, Object> retrieved = db.getProductById(id);
        assertNotNull(retrieved);
        assertEquals(name500, retrieved.get("name"));
    }

    @Test
    @DisplayName("B1.4: Zero-Price Promotional Product ($0.00)")
    public void testZeroPriceProduct() throws Exception {
        int id = db.insertProduct("FREE-01", "Free Promotional Bookmark", harness.getStationeryCategoryId(),
                0.00, 0.00, 500, 50, null, "Vendor", null);

        Map<String, Object> retrieved = db.getProductById(id);
        assertNotNull(retrieved);
        assertEquals(0.00, (Double) retrieved.get("price"), 0.001);
    }

    @Test
    @DisplayName("B1.5: Non-existent Product ID returns null")
    public void testNonExistentProductId() throws Exception {
        Map<String, Object> product = db.getProductById(999999);
        assertNull(product, "Querying non-existent ID should return null");
    }

    @Test
    @DisplayName("B1.6: Non-existent SKU lookup returns null")
    public void testNonExistentSku() throws Exception {
        Map<String, Object> product = db.getProductBySku("DOES-NOT-EXIST");
        assertNull(product, "Querying non-existent SKU should return null");
    }
}
