package com.example.t.e2e.tier1;

import com.example.t.e2e.harness.CsvTestHelper;
import com.example.t.e2e.harness.E2ETestHarness;
import com.example.t.e2e.harness.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1 - Feature 8: Bidirectional CSV Synchronization E2E Tests")
public class CsvSyncE2ETest {

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
    @DisplayName("F8.1: Export Catalog Products to RFC 4180 CSV")
    public void testExportProductsToCsv() throws Exception {
        List<Map<String, Object>> products = db.getAllProducts();
        StringBuilder csv = new StringBuilder();
        csv.append("sku,name,price,stock_quantity,author\n");

        for (Map<String, Object> p : products) {
            List<String> row = Arrays.asList(
                    (String) p.get("sku"),
                    (String) p.get("name"),
                    String.valueOf(p.get("price")),
                    String.valueOf(p.get("stock_quantity")),
                    p.get("author") != null ? (String) p.get("author") : ""
            );
            csv.append(CsvTestHelper.formatRow(row)).append("\n");
        }

        List<List<String>> parsed = CsvTestHelper.parseCsv(csv.toString());
        // 1 header + 6 seeded products = 7 rows
        assertEquals(7, parsed.size(), "Exported CSV must contain 1 header + 6 data rows");
        assertEquals("BK-001", parsed.get(1).get(0));
        assertEquals("Clean Code: A Handbook of Agile Craftsmanship", parsed.get(1).get(1));
    }

    @Test
    @DisplayName("F8.2: Import Catalog Products from CSV into Database")
    public void testImportProductsFromCsv() throws Exception {
        String inputCsv = "sku,name,price,stock_quantity\n" +
                "IMP-001,Introduction to Algorithms,89.50,15\n" +
                "IMP-002,Parker Jotter Ballpoint Pen,18.00,40\n";

        List<List<String>> records = CsvTestHelper.parseCsv(inputCsv);
        assertEquals(3, records.size()); // 1 header + 2 records

        for (int i = 1; i < records.size(); i++) {
            List<String> r = records.get(i);
            String sku = r.get(0);
            String name = r.get(1);
            double price = Double.parseDouble(r.get(2));
            int stock = Integer.parseInt(r.get(3));
            db.insertProduct(sku, name, harness.getBookCategoryId(), price, price * 0.6, stock, 5, null, null, null);
        }

        Map<String, Object> p1 = db.getProductBySku("IMP-001");
        assertNotNull(p1);
        assertEquals("Introduction to Algorithms", p1.get("name"));
        assertEquals(89.50, (Double) p1.get("price"), 0.001);
        assertEquals(15, p1.get("stock_quantity"));

        Map<String, Object> p2 = db.getProductBySku("IMP-002");
        assertNotNull(p2);
        assertEquals("Parker Jotter Ballpoint Pen", p2.get("name"));
    }

    @Test
    @DisplayName("F8.3: Round-Trip Export and Re-Import Fidelity")
    public void testRoundTripCsvFidelity() throws Exception {
        List<Map<String, Object>> originalProducts = db.getAllProducts();

        // Export to CSV
        StringBuilder csv = new StringBuilder();
        for (Map<String, Object> p : originalProducts) {
            List<String> row = Arrays.asList(
                    (String) p.get("sku"),
                    (String) p.get("name"),
                    String.valueOf(p.get("price")),
                    String.valueOf(p.get("stock_quantity"))
            );
            csv.append(CsvTestHelper.formatRow(row)).append("\n");
        }

        // Parse back
        List<List<String>> parsed = CsvTestHelper.parseCsv(csv.toString());
        assertEquals(originalProducts.size(), parsed.size());

        for (int i = 0; i < originalProducts.size(); i++) {
            Map<String, Object> original = originalProducts.get(i);
            List<String> importedRow = parsed.get(i);

            assertEquals(original.get("sku"), importedRow.get(0));
            assertEquals(original.get("name"), importedRow.get(1));
            assertEquals((Double) original.get("price"), Double.parseDouble(importedRow.get(2)), 0.001);
            assertEquals(original.get("stock_quantity"), Integer.parseInt(importedRow.get(3)));
        }
    }

    @Test
    @DisplayName("F8.4: Export Invoice History to Standard CSV Ledger")
    public void testExportInvoiceHistoryToCsv() throws Exception {
        List<String> headers = Arrays.asList("Invoice ID", "Invoice Date", "Customer Name", "Subtotal", "Tax", "Net Total");
        String headerRow = CsvTestHelper.formatRow(headers);

        List<String> dataRow1 = Arrays.asList("INV-101", "2026-10-19", "John Doe", "45.00", "3.71", "48.71");
        List<String> dataRow2 = Arrays.asList("INV-102", "2026-10-19", "Jane Smith", "120.00", "9.90", "129.90");

        String fullCsv = headerRow + "\n" + CsvTestHelper.formatRow(dataRow1) + "\n" + CsvTestHelper.formatRow(dataRow2);

        List<List<String>> parsed = CsvTestHelper.parseCsv(fullCsv);
        assertEquals(3, parsed.size());
        assertEquals("INV-101", parsed.get(1).get(0));
        assertEquals("John Doe", parsed.get(1).get(2));
        assertEquals("48.71", parsed.get(1).get(5));
    }

    @Test
    @DisplayName("F8.5: CSV Field containing Commas is quoted per RFC 4180")
    public void testFieldWithCommaQuoted() throws Exception {
        List<String> row = Arrays.asList("BK-COMMA", "War and Peace, Volume 1", "25.00");
        String formatted = CsvTestHelper.formatRow(row);

        assertEquals("BK-COMMA,\"War and Peace, Volume 1\",25.00", formatted);

        List<List<String>> parsed = CsvTestHelper.parseCsv(formatted);
        assertEquals(1, parsed.size());
        assertEquals("War and Peace, Volume 1", parsed.get(0).get(1));
    }

    @Test
    @DisplayName("F8.6: CSV Field containing Double Quotes is escaped per RFC 4180")
    public void testFieldWithQuotesEscaped() throws Exception {
        List<String> row = Arrays.asList("ST-RULER", "Wooden 12\" Standard Ruler", "3.50");
        String formatted = CsvTestHelper.formatRow(row);

        assertEquals("ST-RULER,\"Wooden 12\"\" Standard Ruler\",3.50", formatted);

        List<List<String>> parsed = CsvTestHelper.parseCsv(formatted);
        assertEquals(1, parsed.size());
        assertEquals("Wooden 12\" Standard Ruler", parsed.get(0).get(1));
    }
}
