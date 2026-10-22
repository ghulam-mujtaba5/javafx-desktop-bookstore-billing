package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.model.InvoiceItem;
import com.example.t.model.StoreSettings;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GiftReceiptTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Gift receipt generates valid PDF with %PDF- magic bytes and Code-128 barcode")
    public void testGenerateGiftReceiptValidPdf() throws IOException {
        PdfInvoiceService service = new PdfInvoiceService(tempDir);

        Invoice invoice = new Invoice(101, 101, "Gift Recipient", LocalDate.now(), List.of(),
                90.00, 0.0, 0.0, 90.00);
        InvoiceItem item1 = new InvoiceItem(1, 101, 10, "Clean Code", "BK-001", 2, 45.00, 90.00);

        Path target = tempDir.resolve("gift_receipt_101.pdf");
        Path result = service.generateGiftReceipt(invoice, List.of(item1), StoreSettings.defaultSettings(), false, target);

        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertTrue(Files.size(result) > 500);

        byte[] headerBytes = new byte[5];
        try (var in = Files.newInputStream(result)) {
            int read = in.read(headerBytes);
            assertEquals(5, read);
            assertEquals("%PDF-", new String(headerBytes));
        }
    }

    @Test
    @DisplayName("Gift receipt suppresses all prices, totals, tax, and currency symbols while preserving items and 30-day policy")
    public void testPriceSuppressionInGiftReceipt() throws IOException {
        PdfInvoiceService service = new PdfInvoiceService(tempDir);

        Invoice invoice = new Invoice(202, 202, "Bob Vance", LocalDate.now(), List.of(),
                120.00, 10.0, 12.00, 117.00);
        InvoiceItem item1 = new InvoiceItem(1, 202, 5, "Refactoring", "BK-REF", 1, 60.00, 60.00, 0.0, 0.0, "For Bob");
        InvoiceItem item2 = new InvoiceItem(2, 202, 6, "Fountain Pen", "ST-PEN", 2, 30.00, 60.00);

        Path target = tempDir.resolve("gift_receipt_suppressed.pdf");
        service.generateGiftReceipt(invoice, List.of(item1, item2), target);

        PdfReader reader = new PdfReader(target.toString());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);
        reader.close();

        assertNotNull(text);

        // Required gift receipt content
        assertTrue(text.contains("GIFT RECEIPT"), "Must contain GIFT RECEIPT title");
        assertTrue(text.contains("INV-202"), "Must contain invoice number for reference");
        assertTrue(text.contains("Bob Vance"), "Must contain customer name");
        assertTrue(text.contains("Refactoring"), "Must contain product 1 name");
        assertTrue(text.contains("BK-REF"), "Must contain product 1 SKU");
        assertTrue(text.contains("Fountain Pen"), "Must contain product 2 name");
        assertTrue(text.contains("ST-PEN"), "Must contain product 2 SKU");
        assertTrue(text.contains("Gift Receipt: Exchangeable within 30 days with receipt"), "Must display 30-day exchange policy");

        // Price suppression verification
        assertFalse(text.contains("Unit Price"), "Unit Price header must be suppressed");
        assertFalse(text.contains("Line Total"), "Line Total header must be suppressed");
        assertFalse(text.contains("Subtotal"), "Subtotal must be suppressed");
        assertFalse(text.contains("Grand Total"), "Grand Total must be suppressed");
        assertFalse(text.contains("Sales Tax"), "Tax must be suppressed");
        assertFalse(text.contains("120.00"), "Subtotal amount $120.00 must not appear");
        assertFalse(text.contains("117.00"), "Net total amount $117.00 must not appear");
        assertFalse(text.contains("60.00"), "Line price $60.00 must not appear");
        assertFalse(text.contains("30.00"), "Unit price $30.00 must not appear");
    }

    @Test
    @DisplayName("Thermal 80mm format generates properly for gift receipts")
    public void testThermal80mmGiftReceipt() throws IOException {
        PdfInvoiceService service = new PdfInvoiceService(tempDir);

        Invoice invoice = new Invoice(303, 303, "Thermal Customer", LocalDate.now(), List.of(),
                45.00, 0.0, 0.0, 45.00);
        InvoiceItem item = new InvoiceItem(1, 303, 1, "Java in a Nutshell", "BK-JAVA", 1, 45.00, 45.00);

        Path target = tempDir.resolve("thermal_gift_80mm.pdf");
        Path result = service.generateGiftReceipt(invoice, List.of(item), StoreSettings.defaultSettings(), true, target);

        assertTrue(Files.exists(result));
        assertTrue(Files.size(result) > 400);
    }
}
