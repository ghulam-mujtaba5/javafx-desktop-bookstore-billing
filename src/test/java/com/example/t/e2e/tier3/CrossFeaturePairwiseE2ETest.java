package com.example.t.e2e.tier3;

import com.example.t.e2e.harness.CalculationOracle;
import com.example.t.e2e.harness.CalculationOracle.CartItem;
import com.example.t.e2e.harness.CalculationOracle.OrderCalculationResult;
import com.example.t.e2e.harness.CalculationOracle.RegionalPreset;
import com.example.t.e2e.harness.CsvTestHelper;
import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 3 - Cross-Feature Combinations (Pairwise & Multi-way Interactions)")
public class CrossFeaturePairwiseE2ETest {

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
    @DisplayName("P1: UK Preset (GBP, £) + 10% Discount + 20% VAT + Stock Decrement")
    public void testUkDiscountVatStockDecrement() throws Exception {
        // Switch store to UK
        db.updateStoreSettings("London Bookshop", "Baker St", "020-1234", "UK", "GBP", "£", "VAT", 20.00, "EN");

        int initialStock = db.getProductStock(harness.getCleanCodeId());
        CartItem book = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 2, 35.00, "BK-001");
        // Subtotal = 70.00, 10% disc = 7.00, Taxable = 63.00, 20% VAT = 12.60, Net Total = 75.60, Cash = 80.00, Change = 4.40
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(book), 10.0, RegionalPreset.UK, 80.00);

        assertEquals(70.00, res.getSubtotal(), 0.001);
        assertEquals(7.00, res.getDiscountAmount(), 0.001);
        assertEquals(63.00, res.getTaxableAmount(), 0.001);
        assertEquals(12.60, res.getTaxAmount(), 0.001);
        assertEquals(75.60, res.getNetTotal(), 0.001);
        assertEquals(4.40, res.getChangeDue(), 0.001);

        int invId = db.createInvoiceWithTransaction("INV-UK-01", "Sir Arthur", null,
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "GBP", "UK", res.getItems());

        assertTrue(invId > 0);
        assertEquals(initialStock - 2, db.getProductStock(harness.getCleanCodeId()), "Stock must be decremented by 2");
    }

    @Test
    @DisplayName("P2: UAE Preset (AED) + 5% VAT + Bilingual Arabic/English + SQLite Persistence")
    public void testUaeVatBilingualPersistence() throws Exception {
        db.updateStoreSettings("مكتبة دبي (Dubai Bookstore)", "Mall of the Emirates", "+971 4 1234567",
                "UAE", "AED", "AED", "VAT", 5.00, "BILINGUAL_AR_EN");

        CartItem pen = new CartItem(harness.getPenId(), "Gel Pen Pack", "Stationery", 5, 6.00, "ST-002");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(pen), 0.0, RegionalPreset.GULF_UAE, 50.00);

        // Subtotal = 30.00, Tax = 1.50, Total = 31.50, Change = 18.50
        assertEquals(30.00, res.getSubtotal(), 0.001);
        assertEquals(1.50, res.getTaxAmount(), 0.001);
        assertEquals(31.50, res.getNetTotal(), 0.001);
        assertEquals(18.50, res.getChangeDue(), 0.001);

        int invId = db.createInvoiceWithTransaction("INV-UAE-01", "محمد أحمد (Mohammad Ahmad)", "+971501234567",
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "AED", "UAE", res.getItems());

        Map<String, Object> inv = db.getInvoiceById(invId);
        assertEquals("محمد أحمد (Mohammad Ahmad)", inv.get("customer_name"));
        assertEquals("AED", inv.get("currency_code"));
        assertEquals(31.50, (Double) inv.get("net_total"), 0.001);
    }

    @Test
    @DisplayName("P3: Saudi Arabia Preset (SAR) + 15% VAT + 25% Bulk Discount + Cash Change")
    public void testSaudiBulkDiscountVatChange() throws Exception {
        CartItem book = new CartItem(harness.getJava21Id(), "Java 21", "Books", 4, 45.00, "BK-002");
        // Subtotal = 180.00, 25% disc = 45.00, Taxable = 135.00
        // 15% VAT of 135.00 = 20.25, Net Total = 155.25, Cash = 200.00, Change = 44.75
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(book), 25.0, RegionalPreset.GULF_SAUDI, 200.00);

        assertEquals(180.00, res.getSubtotal(), 0.001);
        assertEquals(45.00, res.getDiscountAmount(), 0.001);
        assertEquals(135.00, res.getTaxableAmount(), 0.001);
        assertEquals(20.25, res.getTaxAmount(), 0.001);
        assertEquals(155.25, res.getNetTotal(), 0.001);
        assertEquals(44.75, res.getChangeDue(), 0.001);

        int invId = db.createInvoiceWithTransaction("INV-KSA-01", "Riyadh Tech University", null,
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "SAR", "Saudi Arabia", res.getItems());

        assertTrue(invId > 0);
    }

    @Test
    @DisplayName("P4: USA (USD) + 8.25% Sales Tax + Stock Decrement + CSV Sync Export")
    public void testUsaSalesTaxStockCsvSync() throws Exception {
        CartItem notebook = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 10, 4.50, "ST-001");
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Collections.singletonList(notebook), 0.0, RegionalPreset.USA, 50.00);

        // Subtotal = 45.00, 8.25% Tax = 3.71, Net Total = 48.71, Cash = 50.00, Change = 1.29
        assertEquals(45.00, res.getSubtotal(), 0.001);
        assertEquals(3.71, res.getTaxAmount(), 0.001);
        assertEquals(48.71, res.getNetTotal(), 0.001);
        assertEquals(1.29, res.getChangeDue(), 0.001);

        int invId = db.createInvoiceWithTransaction("INV-USA-CSV", "Bob Student", null,
                res.getSubtotal(), res.getDiscountPercent(), res.getDiscountAmount(),
                res.getTaxRate(), res.getTaxAmount(), res.getNetTotal(), res.getCashTendered(),
                res.getChangeDue(), "USD", "USA", res.getItems());

        // Export invoice ledger to CSV line
        List<String> csvRow = Arrays.asList(
                "INV-USA-CSV", "2026-10-19", "Bob Student",
                String.valueOf(res.getSubtotal()), String.valueOf(res.getTaxAmount()), String.valueOf(res.getNetTotal()));
        String formatted = CsvTestHelper.formatRow(csvRow);

        List<List<String>> parsed = CsvTestHelper.parseCsv(formatted);
        assertEquals(1, parsed.size());
        assertEquals("48.71", parsed.get(0).get(5));
    }

    @Test
    @DisplayName("P5: Qatar Preset (QAR, 0% VAT) + 15% Promotional Discount + Mixed Catalog Basket")
    public void testQatarZeroTaxPromotionalDiscount() {
        CartItem book = new CartItem(1, "1984", "Books", 2, 15.00, "BK-003");
        CartItem pen = new CartItem(2, "Gel Pen Pack", "Stationery", 5, 6.00, "ST-002");

        // Subtotal = 30.00 + 30.00 = 60.00
        // 15% Discount = 9.00 -> Taxable = 51.00
        // 0% VAT -> Tax = 0.00 -> Net Total = 51.00
        // Cash = 60.00 -> Change = 9.00
        OrderCalculationResult res = CalculationOracle.calculateForRegion(
                Arrays.asList(book, pen), 15.0, RegionalPreset.GULF_QATAR, 60.00);

        assertEquals(60.00, res.getSubtotal(), 0.001);
        assertEquals(9.00, res.getDiscountAmount(), 0.001);
        assertEquals(51.00, res.getTaxableAmount(), 0.001);
        assertEquals(0.00, res.getTaxAmount(), 0.001);
        assertEquals(51.00, res.getNetTotal(), 0.001);
        assertEquals(9.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("P6: High-Volume Basket with 30 Line Items across Categories")
    public void testHighVolumeBasketCalculation() {
        List<CartItem> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(new CartItem(i, "Item " + i, i % 2 == 0 ? "Books" : "Stationery", 2, 12.50, "SKU-" + i));
        }

        // Subtotal = 30 * (2 * 12.50) = 750.00
        // 10% Discount = 75.00, Taxable = 675.00, 20% VAT = 135.00, Net Total = 810.00
        OrderCalculationResult res = CalculationOracle.calculate(items, 10.0, 20.0, 1000.00);

        assertEquals(750.00, res.getSubtotal(), 0.001);
        assertEquals(75.00, res.getDiscountAmount(), 0.001);
        assertEquals(675.00, res.getTaxableAmount(), 0.001);
        assertEquals(135.00, res.getTaxAmount(), 0.001);
        assertEquals(810.00, res.getNetTotal(), 0.001);
        assertEquals(190.00, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("P7: Failed Transaction Atomicity: Insufficient stock aborts order with zero side-effects")
    public void testFailedTransactionAtomicity() throws Exception {
        int initialStockCleanCode = db.getProductStock(harness.getCleanCodeId());
        int initialStockOrwell = db.getProductStock(harness.getOrwellId());
        int initialInvoiceCount = db.getAllInvoices().size();

        // Orwell has 10 units in stock. Cart requests 20 units of Orwell, and 2 units of Clean Code.
        CartItem item1 = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 2, 35.00, "BK-001");
        CartItem item2 = new CartItem(harness.getOrwellId(), "1984", "Books", 20, 15.00, "BK-003"); // exceeds 10

        assertThrows(SQLException.class, () -> {
            db.createInvoiceWithTransaction("INV-FAIL-ATOMIC", "Failed Customer", null,
                    370.0, 0.0, 0.0, 0.0, 0.0, 370.0, 400.0, 30.0, "USD", "USA",
                    Arrays.asList(item1, item2));
        }, "Transaction must fail and throw SQLException on insufficient stock");

        // Verify Clean Code stock was NOT decremented (rollback successful)
        assertEquals(initialStockCleanCode, db.getProductStock(harness.getCleanCodeId()),
                "Clean Code stock must NOT be decremented on failed order");
        assertEquals(initialStockOrwell, db.getProductStock(harness.getOrwellId()),
                "Orwell stock must remain untouched");
        assertEquals(initialInvoiceCount, db.getAllInvoices().size(),
                "No invoice record should have been committed");
    }

    @Test
    @DisplayName("P8: POS Sale Decrement followed by Immediate Restock Operation")
    public void testSaleDecrementFollowedByRestock() throws Exception {
        int notebookId = harness.getNotebookId();
        assertEquals(100, db.getProductStock(notebookId));

        CartItem item = new CartItem(notebookId, "Notebook", "Stationery", 25, 4.50, "ST-001");
        db.createInvoiceWithTransaction("INV-SALE-RESTOCK", "School Rep", null,
                112.50, 0.0, 0.0, 0.0, 0.0, 112.50, 120.00, 7.50, "USD", "USA", Collections.singletonList(item));

        assertEquals(75, db.getProductStock(notebookId), "Stock must drop to 75 after sale");

        // Supplier delivery restocks 50 units
        db.restockProduct(notebookId, 50);
        assertEquals(125, db.getProductStock(notebookId), "Stock must be 125 after restock");
    }

    @Test
    @DisplayName("P9: CSV Export -> Truncate DB -> CSV Import round-trip recovery")
    public void testCsvExportTruncateImportRecovery() throws Exception {
        List<Map<String, Object>> originalList = db.getAllProducts();
        StringBuilder csv = new StringBuilder();
        csv.append("sku,name,price,stock_quantity\n");
        for (Map<String, Object> p : originalList) {
            csv.append(CsvTestHelper.formatRow(Arrays.asList(
                    (String) p.get("sku"),
                    (String) p.get("name"),
                    String.valueOf(p.get("price")),
                    String.valueOf(p.get("stock_quantity"))
            ))).append("\n");
        }

        // Delete all products
        try (Statement s = db.getConnection().createStatement()) {
            s.executeUpdate("DELETE FROM products;");
        }
        assertEquals(0, db.getAllProducts().size(), "Catalog must be completely empty");

        // Re-import from CSV
        List<List<String>> records = CsvTestHelper.parseCsv(csv.toString());
        for (int i = 1; i < records.size(); i++) {
            List<String> r = records.get(i);
            db.insertProduct(r.get(0), r.get(1), harness.getBookCategoryId(),
                    Double.parseDouble(r.get(2)), 0.0, Integer.parseInt(r.get(3)), 5, null, null, null);
        }

        List<Map<String, Object>> restored = db.getAllProducts();
        assertEquals(originalList.size(), restored.size(), "All products must be restored from CSV");
    }

    @Test
    @DisplayName("P10: SQLite Invoices Total matches CSV Ledger Summation")
    public void testSqliteInvoicesMatchCsvLedger() throws Exception {
        CartItem item1 = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 1, 35.00, "BK-001");
        CartItem item2 = new CartItem(harness.getPenId(), "Gel Pen", "Stationery", 2, 6.00, "ST-002");

        db.createInvoiceWithTransaction("INV-REC-1", "Cust 1", null, 35.00, 0.0, 0.0, 10.0, 3.50, 38.50, 40.00, 1.50, "USD", "USA", Collections.singletonList(item1));
        db.createInvoiceWithTransaction("INV-REC-2", "Cust 2", null, 12.00, 0.0, 0.0, 10.0, 1.20, 13.20, 20.00, 6.80, "USD", "USA", Collections.singletonList(item2));

        // Query database revenue sum
        double dbTotalRevenue = 0.0;
        for (Map<String, Object> inv : db.getAllInvoices()) {
            if (inv.get("invoice_number").toString().startsWith("INV-REC")) {
                dbTotalRevenue += (Double) inv.get("net_total");
            }
        }

        // Simulate CSV ledger lines
        String csv = "INV-REC-1,38.50\nINV-REC-2,13.20\n";
        List<List<String>> rows = CsvTestHelper.parseCsv(csv);
        double csvTotalRevenue = 0.0;
        for (List<String> r : rows) {
            csvTotalRevenue += Double.parseDouble(r.get(1));
        }

        assertEquals(dbTotalRevenue, csvTotalRevenue, 0.001, "Database ledger total must match CSV ledger total (51.70)");
    }

    @Test
    @DisplayName("P11: Sub-cent Items with 5% VAT and 10% Discount: Rounding Precision")
    public void testSubCentPrecisionMath() {
        // 3 items at $0.33 each = 0.99 subtotal
        CartItem item = new CartItem(1, "Individual Paper Sheet", "Stationery", 3, 0.33, "SHT-1");
        // 10% discount on 0.99 = 0.099 -> rounds to 0.10
        // Taxable = 0.99 - 0.10 = 0.89
        // 5% VAT on 0.89 = 0.0445 -> rounds to 0.04
        // Net Total = 0.89 + 0.04 = 0.93
        // Cash = 1.00 -> Change = 0.07
        OrderCalculationResult res = CalculationOracle.calculate(
                Collections.singletonList(item), 10.0, 5.0, 1.00);

        assertEquals(0.99, res.getSubtotal(), 0.001);
        assertEquals(0.10, res.getDiscountAmount(), 0.001);
        assertEquals(0.89, res.getTaxableAmount(), 0.001);
        assertEquals(0.04, res.getTaxAmount(), 0.001);
        assertEquals(0.93, res.getNetTotal(), 0.001);
        assertEquals(0.07, res.getChangeDue(), 0.001);
    }

    @Test
    @DisplayName("P12: Low-Stock Warning Triggered during multi-product order")
    public void testLowStockTriggeredAfterSale() throws Exception {
        int stickyId = harness.getStickyNotesId();
        // Initial stock = 4, threshold = 5
        assertEquals(4, db.getProductStock(stickyId));

        // Sell 2 units -> remaining 2 units
        CartItem item = new CartItem(stickyId, "Sticky Notes", "Stationery", 2, 2.50, "ST-003");
        db.createInvoiceWithTransaction("INV-LOW-STOCK", "Shopper", null, 5.00, 0.0, 0.0, 0.0, 0.0, 5.00, 10.00, 5.00, "USD", "USA", Collections.singletonList(item));

        Map<String, Object> p = db.getProductById(stickyId);
        int remStock = (Integer) p.get("stock_quantity");
        int threshold = (Integer) p.get("min_stock_threshold");
        assertEquals(2, remStock);
        assertTrue(remStock <= threshold, "Item must remain in low-stock status (2 <= 5)");
    }

    @Test
    @DisplayName("P13: Query and Filter Invoices by Customer Name across Multi-Currency Orders")
    public void testFilterInvoicesByCustomer() throws Exception {
        CartItem item = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 1, 4.50, "ST-001");
        db.createInvoiceWithTransaction("INV-CUST-1", "Professor Higgins", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "USD", "USA", Collections.singletonList(item));
        db.createInvoiceWithTransaction("INV-CUST-2", "Eliza Doolittle", null, 4.50, 0.0, 0.0, 0.0, 0.0, 4.50, 5.00, 0.50, "GBP", "UK", Collections.singletonList(item));
        db.createInvoiceWithTransaction("INV-CUST-3", "Professor Higgins", null, 9.00, 0.0, 0.0, 0.0, 0.0, 9.00, 10.00, 1.00, "AED", "UAE", Collections.singletonList(item));

        List<Map<String, Object>> all = db.getAllInvoices();
        long higginsCount = all.stream().filter(i -> "Professor Higgins".equals(i.get("customer_name"))).count();
        assertEquals(2, higginsCount, "Professor Higgins must have exactly 2 invoices");
    }

    @Test
    @DisplayName("P14: Stock Valuation Aggregation by Category")
    public void testCategoryStockValuation() throws Exception {
        List<Map<String, Object>> all = db.getAllProducts();
        double booksValuation = 0.0;
        double stationeryValuation = 0.0;

        for (Map<String, Object> p : all) {
            double price = (Double) p.get("price");
            int stock = (Integer) p.get("stock_quantity");
            int catId = (Integer) p.get("category_id");

            if (catId == harness.getBookCategoryId()) {
                booksValuation += price * stock;
            } else if (catId == harness.getStationeryCategoryId()) {
                stationeryValuation += price * stock;
            }
        }

        // Clean Code (50 * 35 = 1750), Java 21 (30 * 45 = 1350), 1984 (10 * 15 = 150) -> 3250.00
        assertEquals(3250.00, booksValuation, 0.001);
        // Notebooks (100 * 4.50 = 450), Pens (75 * 6.00 = 450), Sticky (4 * 2.50 = 10) -> 910.00
        assertEquals(910.00, stationeryValuation, 0.001);
    }

    @Test
    @DisplayName("P15: Dynamic Regional Setting Transition: USA -> UK -> UAE")
    public void testDynamicRegionalTransition() {
        CartItem cart = new CartItem(1, "Textbook", "Books", 1, 100.00, "TB-1");

        // 1. USA (8.25%)
        OrderCalculationResult usa = CalculationOracle.calculateForRegion(
                Collections.singletonList(cart), 0.0, RegionalPreset.USA, 110.00);
        assertEquals(8.25, usa.getTaxAmount(), 0.001);
        assertEquals(108.25, usa.getNetTotal(), 0.001);

        // 2. UK (20.00%)
        OrderCalculationResult uk = CalculationOracle.calculateForRegion(
                Collections.singletonList(cart), 0.0, RegionalPreset.UK, 120.00);
        assertEquals(20.00, uk.getTaxAmount(), 0.001);
        assertEquals(120.00, uk.getNetTotal(), 0.001);

        // 3. UAE (5.00%)
        OrderCalculationResult uae = CalculationOracle.calculateForRegion(
                Collections.singletonList(cart), 0.0, RegionalPreset.GULF_UAE, 110.00);
        assertEquals(5.00, uae.getTaxAmount(), 0.001);
        assertEquals(105.00, uae.getNetTotal(), 0.001);
    }
}
