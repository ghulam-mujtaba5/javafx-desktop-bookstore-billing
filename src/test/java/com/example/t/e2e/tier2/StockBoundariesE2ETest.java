package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 2: Stock Update Boundary & Corner Cases")
public class StockBoundariesE2ETest {

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
    @DisplayName("B2.1: Decrement with zero quantity throws IllegalArgumentException")
    public void testZeroQuantityDecrement() {
        assertThrows(IllegalArgumentException.class, () -> {
            db.decrementStock(harness.getCleanCodeId(), 0);
        }, "Decrementing 0 units must be rejected");
    }

    @Test
    @DisplayName("B2.2: Decrement with negative quantity throws IllegalArgumentException")
    public void testNegativeQuantityDecrement() {
        assertThrows(IllegalArgumentException.class, () -> {
            db.decrementStock(harness.getCleanCodeId(), -5);
        }, "Decrementing negative units must be rejected");
    }

    @Test
    @DisplayName("B2.3: Restock with zero quantity throws IllegalArgumentException")
    public void testZeroQuantityRestock() {
        assertThrows(IllegalArgumentException.class, () -> {
            db.restockProduct(harness.getCleanCodeId(), 0);
        }, "Restocking 0 units must be rejected");
    }

    @Test
    @DisplayName("B2.4: Restock with negative quantity throws IllegalArgumentException")
    public void testNegativeQuantityRestock() {
        assertThrows(IllegalArgumentException.class, () -> {
            db.restockProduct(harness.getCleanCodeId(), -10);
        }, "Restocking negative units must be rejected");
    }

    @Test
    @DisplayName("B2.5: Decrement exceeding available stock leaves inventory intact")
    public void testDecrementExceedingAvailableStock() throws Exception {
        int initialStock = db.getProductStock(harness.getOrwellId());
        assertEquals(10, initialStock);

        boolean success = db.decrementStock(harness.getOrwellId(), 11);
        assertFalse(success, "Decrementing 11 when stock is 10 must fail");
        assertEquals(10, db.getProductStock(harness.getOrwellId()), "Stock must remain unchanged");
    }

    @Test
    @DisplayName("B2.6: Massive Stock Inventory Handling (Boundary up to 1,000,000 units)")
    public void testMassiveStockQuantity() throws Exception {
        int id = db.insertProduct("BULK-PAPER", "A4 Copy Paper Reams", harness.getStationeryCategoryId(),
                5.00, 3.00, 1_000_000, 100, null, "Paper Mills", null);

        assertEquals(1_000_000, db.getProductStock(id));

        boolean decremented = db.decrementStock(id, 250_000);
        assertTrue(decremented);
        assertEquals(750_000, db.getProductStock(id));

        boolean restocked = db.restockProduct(id, 250_000);
        assertTrue(restocked);
        assertEquals(1_000_000, db.getProductStock(id));
    }
}
