package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - CsvSyncService RFC 4180 Unit Tests")
public class CsvSyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Export Products to RFC 4180 CSV")
    public void testExportProducts() {
        Product p1 = new Product(1, "Clean Code", "Books", "BK-001", "Robert C. Martin", "Prentice Hall", "1st", 20.0, 35.0, 50, 5);
        Product p2 = new Product(2, "A5 Spiral Notebook", "Stationery", "ST-001", "", "Oxford", "", 2.0, 4.5, 100, 10);

        String csv = CsvSyncService.exportProductsToCsv(Arrays.asList(p1, p2));
        assertNotNull(csv);

        List<List<String>> parsed = CsvSyncService.parseCsv(csv);
        assertEquals(3, parsed.size(), "1 header + 2 product rows");
        assertEquals("sku", parsed.get(0).get(0));
        assertEquals("BK-001", parsed.get(1).get(0));
        assertEquals("Clean Code", parsed.get(1).get(1));
        assertEquals("ST-001", parsed.get(2).get(0));
    }

    @Test
    @DisplayName("Import Products from CSV with Round-Trip Fidelity")
    public void testImportProducts() {
        String csvInput = "sku,name,price,stock_quantity,author\n" +
                "BK-IMP-1,Effective Java,45.00,20,Joshua Bloch\n" +
                "ST-IMP-2,Ballpoint Pen,1.50,150,\n";

        List<Product> products = CsvSyncService.importProductsFromCsv(csvInput);
        assertEquals(2, products.size());

        Product p1 = products.get(0);
        assertEquals("BK-IMP-1", p1.getSku());
        assertEquals("Effective Java", p1.getProductName());
        assertEquals(45.00, p1.getPrice(), 0.001);
        assertEquals(20, p1.getQuantity());
        assertEquals("Joshua Bloch", p1.getAuthor());

        Product p2 = products.get(1);
        assertEquals("ST-IMP-2", p2.getSku());
        assertEquals("Ballpoint Pen", p2.getProductName());
        assertEquals(1.50, p2.getPrice(), 0.001);
        assertEquals(150, p2.getQuantity());
    }

    @Test
    @DisplayName("CSV Field containing Commas is Quoted per RFC 4180")
    public void testCommasQuoted() {
        List<String> row = Arrays.asList("BK-COMMA", "War and Peace, Volume 1", "25.00");
        String formatted = CsvSyncService.formatRow(row);

        assertEquals("BK-COMMA,\"War and Peace, Volume 1\",25.00", formatted);

        List<List<String>> parsed = CsvSyncService.parseCsv(formatted);
        assertEquals(1, parsed.size());
        assertEquals("War and Peace, Volume 1", parsed.get(0).get(1));
    }

    @Test
    @DisplayName("CSV Field containing Double Quotes is Escaped with Double Quotes per RFC 4180")
    public void testQuotesEscaped() {
        List<String> row = Arrays.asList("ST-RULER", "Wooden 12\" Standard Ruler", "3.50");
        String formatted = CsvSyncService.formatRow(row);

        assertEquals("ST-RULER,\"Wooden 12\"\" Standard Ruler\",3.50", formatted);

        List<List<String>> parsed = CsvSyncService.parseCsv(formatted);
        assertEquals(1, parsed.size());
        assertEquals("Wooden 12\" Standard Ruler", parsed.get(0).get(1));
    }

    @Test
    @DisplayName("Export Invoices to CSV and Append to Ledger File")
    public void testInvoiceExportAndLedgerAppend() throws IOException {
        Path ledger = tempDir.resolve("invoice_ledger.csv");

        Invoice inv1 = new Invoice(101, 101, "Alice Cooper", LocalDate.of(2026, 10, 19), null, 45.00, 0.0, 0.0, 48.71);
        Invoice inv2 = new Invoice(102, 102, "Bob Dylan", LocalDate.of(2026, 10, 19), null, 80.00, 10.0, 8.0, 77.76);

        // Append 1st
        CsvSyncService.appendInvoiceToLedger(inv1, ledger);
        assertTrue(Files.exists(ledger));

        // Append 2nd
        CsvSyncService.appendInvoiceToLedger(inv2, ledger);

        String content = Files.readString(ledger);
        List<List<String>> parsed = CsvSyncService.parseCsv(content);
        // 1 header + 2 invoices
        assertEquals(3, parsed.size());
        assertEquals("101", parsed.get(1).get(0));
        assertEquals("Alice Cooper", parsed.get(1).get(2));
        assertEquals("102", parsed.get(2).get(0));
        assertEquals("Bob Dylan", parsed.get(2).get(2));
    }
}
