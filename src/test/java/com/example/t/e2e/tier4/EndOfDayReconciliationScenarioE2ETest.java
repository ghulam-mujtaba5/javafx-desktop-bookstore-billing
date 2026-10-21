package com.example.t.e2e.tier4;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 4 - Scenario 5: End-of-Day Store Reconciliation & Multi-Transaction Audit")
public class EndOfDayReconciliationScenarioE2ETest {

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
    @DisplayName("End-of-Day Multi-Transaction Session: 5 Transactions -> Aggregate Financials -> Inventory Reconciliation")
    public void testEndOfDayStoreReconciliation() throws Exception {
        // Record starting inventory
        int initCleanCode = db.getProductStock(harness.getCleanCodeId());   // 50
        int initJava21 = db.getProductStock(harness.getJava21Id());         // 30
        int initOrwell = db.getProductStock(harness.getOrwellId());         // 10
        int initNotebook = db.getProductStock(harness.getNotebookId());     // 100
        int initPen = db.getProductStock(harness.getPenId());               // 75

        List<OrderCalculationResult> dailyOrders = new ArrayList<>();
        double expectedSubtotalSum = 0.0;
        double expectedDiscountSum = 0.0;
        double expectedTaxSum = 0.0;
        double expectedNetTotalSum = 0.0;

        // Tx 1: Morning student buys 1 Clean Code ($35.00), no discount
        CartItem t1Item = new CartItem(harness.getCleanCodeId(), "Clean Code", "Books", 1, 35.00, "BK-001");
        OrderCalculationResult r1 = CalculationOracle.calculateForRegion(Collections.singletonList(t1Item), 0.0, RegionalPreset.USA, 40.00);
        dailyOrders.add(r1);
        db.createInvoiceWithTransaction("EOD-001", "Student A", null,
                r1.getSubtotal(), r1.getDiscountPercent(), r1.getDiscountAmount(),
                r1.getTaxRate(), r1.getTaxAmount(), r1.getNetTotal(), r1.getCashTendered(),
                r1.getChangeDue(), "USD", "USA", r1.getItems());

        // Tx 2: Walk-in buys 4 Notebooks ($18.00) with 10% discount
        CartItem t2Item = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 4, 4.50, "ST-001");
        OrderCalculationResult r2 = CalculationOracle.calculateForRegion(Collections.singletonList(t2Item), 10.0, RegionalPreset.USA, 20.00);
        dailyOrders.add(r2);
        db.createInvoiceWithTransaction("EOD-002", null, null,
                r2.getSubtotal(), r2.getDiscountPercent(), r2.getDiscountAmount(),
                r2.getTaxRate(), r2.getTaxAmount(), r2.getNetTotal(), r2.getCashTendered(),
                r2.getChangeDue(), "USD", "USA", r2.getItems());

        // Tx 3: Teacher buys 2 Java 21 ($90.00) and 2 Pen Packs ($12.00) with 15% discount
        CartItem t3Book = new CartItem(harness.getJava21Id(), "Java 21", "Books", 2, 45.00, "BK-002");
        CartItem t3Pen = new CartItem(harness.getPenId(), "Pens", "Stationery", 2, 6.00, "ST-002");
        OrderCalculationResult r3 = CalculationOracle.calculateForRegion(Arrays.asList(t3Book, t3Pen), 15.0, RegionalPreset.USA, 100.00);
        dailyOrders.add(r3);
        db.createInvoiceWithTransaction("EOD-003", "Prof Higgins", "555-4321",
                r3.getSubtotal(), r3.getDiscountPercent(), r3.getDiscountAmount(),
                r3.getTaxRate(), r3.getTaxAmount(), r3.getNetTotal(), r3.getCashTendered(),
                r3.getChangeDue(), "USD", "USA", r3.getItems());

        // Tx 4: Quick sale: 1 Orwell ($15.00), exact cash ($16.24)
        CartItem t4Item = new CartItem(harness.getOrwellId(), "1984", "Books", 1, 15.00, "BK-003");
        OrderCalculationResult r4 = CalculationOracle.calculateForRegion(Collections.singletonList(t4Item), 0.0, RegionalPreset.USA, 16.24);
        dailyOrders.add(r4);
        db.createInvoiceWithTransaction("EOD-004", "Reader B", null,
                r4.getSubtotal(), r4.getDiscountPercent(), r4.getDiscountAmount(),
                r4.getTaxRate(), r4.getTaxAmount(), r4.getNetTotal(), r4.getCashTendered(),
                r4.getChangeDue(), "USD", "USA", r4.getItems());

        // Tx 5: Stationery bundle: 3 Notebooks ($13.50) + 1 Pen Pack ($6.00) with 5% discount
        CartItem t5Nb = new CartItem(harness.getNotebookId(), "Notebook", "Stationery", 3, 4.50, "ST-001");
        CartItem t5Pen = new CartItem(harness.getPenId(), "Pens", "Stationery", 1, 6.00, "ST-002");
        OrderCalculationResult r5 = CalculationOracle.calculateForRegion(Arrays.asList(t5Nb, t5Pen), 5.0, RegionalPreset.USA, 20.00);
        dailyOrders.add(r5);
        db.createInvoiceWithTransaction("EOD-005", "Office Clerk", null,
                r5.getSubtotal(), r5.getDiscountPercent(), r5.getDiscountAmount(),
                r5.getTaxRate(), r5.getTaxAmount(), r5.getNetTotal(), r5.getCashTendered(),
                r5.getChangeDue(), "USD", "USA", r5.getItems());

        // Calculate expected cumulative sums
        for (OrderCalculationResult r : dailyOrders) {
            expectedSubtotalSum += r.getSubtotal();
            expectedDiscountSum += r.getDiscountAmount();
            expectedTaxSum += r.getTaxAmount();
            expectedNetTotalSum += r.getNetTotal();
        }

        expectedSubtotalSum = CalculationOracle.roundMoney(expectedSubtotalSum);
        expectedDiscountSum = CalculationOracle.roundMoney(expectedDiscountSum);
        expectedTaxSum = CalculationOracle.roundMoney(expectedTaxSum);
        expectedNetTotalSum = CalculationOracle.roundMoney(expectedNetTotalSum);

        // Step 2: Query SQLite Invoices table and reconcile financial totals
        List<Map<String, Object>> invoices = db.getAllInvoices();
        double actualNetTotalSum = 0.0;
        double actualTaxSum = 0.0;
        double actualDiscountSum = 0.0;
        double actualSubtotalSum = 0.0;

        for (Map<String, Object> inv : invoices) {
            if (inv.get("invoice_number").toString().startsWith("EOD-")) {
                actualSubtotalSum += (Double) inv.get("subtotal");
                actualDiscountSum += (Double) inv.get("discount_amount");
                actualTaxSum += (Double) inv.get("tax_amount");
                actualNetTotalSum += (Double) inv.get("net_total");
            }
        }

        actualSubtotalSum = CalculationOracle.roundMoney(actualSubtotalSum);
        actualDiscountSum = CalculationOracle.roundMoney(actualDiscountSum);
        actualTaxSum = CalculationOracle.roundMoney(actualTaxSum);
        actualNetTotalSum = CalculationOracle.roundMoney(actualNetTotalSum);

        assertEquals(expectedSubtotalSum, actualSubtotalSum, 0.001, "Reconciled subtotal must match oracle");
        assertEquals(expectedDiscountSum, actualDiscountSum, 0.001, "Reconciled discounts must match oracle");
        assertEquals(expectedTaxSum, actualTaxSum, 0.001, "Reconciled taxes must match oracle");
        assertEquals(expectedNetTotalSum, actualNetTotalSum, 0.001, "Reconciled net revenue must match oracle");

        // Step 3: Reconcile Physical Inventory Counts
        // Clean Code: sold 1 -> 50 - 1 = 49
        assertEquals(initCleanCode - 1, db.getProductStock(harness.getCleanCodeId()));
        // Java 21: sold 2 -> 30 - 2 = 28
        assertEquals(initJava21 - 2, db.getProductStock(harness.getJava21Id()));
        // Orwell: sold 1 -> 10 - 1 = 9
        assertEquals(initOrwell - 1, db.getProductStock(harness.getOrwellId()));
        // Notebooks: sold 4 + 3 = 7 -> 100 - 7 = 93
        assertEquals(initNotebook - 7, db.getProductStock(harness.getNotebookId()));
        // Pens: sold 2 + 1 = 3 -> 75 - 3 = 72
        assertEquals(initPen - 3, db.getProductStock(harness.getPenId()));

        // Step 4: Export Daily Ledger to CSV and verify 5 rows + header
        StringBuilder ledgerCsv = new StringBuilder("invoice_number,customer,subtotal,tax,net_total\n");
        for (Map<String, Object> inv : invoices) {
            if (inv.get("invoice_number").toString().startsWith("EOD-")) {
                ledgerCsv.append(CsvTestHelper.formatRow(Arrays.asList(
                        (String) inv.get("invoice_number"),
                        (String) inv.get("customer_name"),
                        String.valueOf(inv.get("subtotal")),
                        String.valueOf(inv.get("tax_amount")),
                        String.valueOf(inv.get("net_total"))
                ))).append("\n");
            }
        }

        List<List<String>> csvRecords = CsvTestHelper.parseCsv(ledgerCsv.toString());
        assertEquals(6, csvRecords.size(), "Exported CSV must contain 1 header + 5 daily invoice rows");
    }
}
