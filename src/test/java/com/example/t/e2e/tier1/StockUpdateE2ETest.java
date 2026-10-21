package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 2: Stock Update & Inventory Visibility E2E Tests")
public class StockUpdateE2ETest {

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
    @DisplayName("F2.1: Restock Product increases inventory quantity")
    public void testRestockProduct() throws Exception {
        int initialStock = db.getProductStock(harness.getCleanCodeId());
        assertEquals(50, initialStock);

        boolean restocked = db.restockProduct(harness.getCleanCodeId(), 25);
        assertTrue(restocked, "Restock command must succeed");

        int newStock = db.getProductStock(harness.getCleanCodeId());
        assertEquals(75, newStock, "Stock must increment by 25");
    }

    @Test
    @DisplayName("F2.2: Decrement Product Stock on sale decreases inventory quantity")
    public void testDecrementProductStock() throws Exception {
        int initialStock = db.getProductStock(harness.getNotebookId());
        assertEquals(100, initialStock);

        boolean decremented = db.decrementStock(harness.getNotebookId(), 5);
        assertTrue(decremented, "Decrement command must succeed");

        int newStock = db.getProductStock(harness.getNotebookId());
        assertEquals(95, newStock, "Stock must decrement from 100 to 95");
    }

    @Test
    @DisplayName("F2.3: Low-Stock Threshold detection when stock <= min_stock_threshold")
    public void testLowStockDetection() throws Exception {
        Map<String, Object> sticky = db.getProductById(harness.getStickyNotesId());
        int stock = (Integer) sticky.get("stock_quantity");
        int threshold = (Integer) sticky.get("min_stock_threshold");

        assertEquals(4, stock);
        assertEquals(5, threshold);
        assertTrue(stock <= threshold, "Item with stock 4 and threshold 5 must trigger low-stock status");
    }

    @Test
    @DisplayName("F2.4: Prevent decrementing beyond available stock count")
    public void testPreventOversellingStock() throws Exception {
        int availableStock = db.getProductStock(harness.getOrwellId());
        assertEquals(10, availableStock);

        // Attempt to decrement 15 units when only 10 available
        boolean success = db.decrementStock(harness.getOrwellId(), 15);
        assertFalse(success, "Decrementing 15 from 10 units must fail");

        // Verify stock remains untouched at 10
        assertEquals(10, db.getProductStock(harness.getOrwellId()), "Stock must remain unchanged on failed decrement");
    }

    @Test
    @DisplayName("F2.5: Restocking low-stock item clears low-stock condition")
    public void testRestockClearsLowStock() throws Exception {
        int stickyId = harness.getStickyNotesId();
        assertTrue(db.getProductStock(stickyId) <= 5);

        db.restockProduct(stickyId, 20);
        int refreshedStock = db.getProductStock(stickyId);
        assertEquals(24, refreshedStock);
        assertTrue(refreshedStock > 5, "Stock must now exceed threshold 5");
    }

    @Test
    @DisplayName("F2.6: Decrementing to exact 0 results in Out-of-Stock state")
    public void testDecrementToExactZeroStock() throws Exception {
        int orwellId = harness.getOrwellId();
        assertEquals(10, db.getProductStock(orwellId));

        boolean decremented = db.decrementStock(orwellId, 10);
        assertTrue(decremented, "Decrementing exact 10 units must succeed");

        int remainingStock = db.getProductStock(orwellId);
        assertEquals(0, remainingStock, "Stock must now be exactly 0");

        // Further decrement should fail
        boolean extraDecrement = db.decrementStock(orwellId, 1);
        assertFalse(extraDecrement, "Cannot decrement out-of-stock item");
    }
}
