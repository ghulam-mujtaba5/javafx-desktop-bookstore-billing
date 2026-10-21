package com.example.t;

import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService;
import com.example.t.service.PdfInvoiceService;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4 - Branded OpenPDF Invoice & Receipt Service Tests")
public class PdfInvoiceServiceTest {

    private static Path tempDir;
    private PdfInvoiceService pdfService;

    @BeforeAll
    public static void setupTemp() throws IOException {
        tempDir = Files.createTempDirectory("pdf_invoice_test_");
    }

    @BeforeEach
    public void setUp() throws SQLException {
        Path dbPath = tempDir.resolve("test_bookstore.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        pdfService = new PdfInvoiceService(tempDir.resolve("invoices"));
    }

    @Test
    @DisplayName("M4.PDF.1: Generates valid PDF file with standard %PDF- header")
    public void testGenerateValidPdfHeader() throws IOException {
        Invoice invoice = new Invoice(101, 101, "Jane Doe", LocalDate.now(),
                Collections.emptyList(), 50.00, 0.0, 0.0, 54.13);
        InvoiceItem item = new InvoiceItem(1, 101, 1, "Domain-Driven Design", "DDD-01", 1, 50.00, 50.00);

        StoreSettings settings = StoreSettings.defaultSettings();
        Path pdfPath = pdfService.generateInvoicePdf(invoice, Collections.singletonList(item), settings);

        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath), "Generated PDF file must exist");
        assertTrue(Files.size(pdfPath) > 500, "PDF file must not be empty");

        // Verify PDF Magic Bytes: %PDF-
        byte[] headerBytes = new byte[5];
        try (var in = Files.newInputStream(pdfPath)) {
            int read = in.read(headerBytes);
            assertEquals(5, read);
            assertEquals("%PDF-", new String(headerBytes));
        }
    }

    @Test
    @DisplayName("M4.PDF.2: Generates 80mm thermal receipt format")
    public void testGenerate80mmThermalReceipt() throws IOException {
        Invoice invoice = new Invoice(102, 102, "Walk-in Customer", LocalDate.now(),
                Collections.emptyList(), 30.00, 5.0, 1.50, 30.86);
        InvoiceItem item = new InvoiceItem(2, 102, 2, "A5 Spiral Notebook", "ST-001", 3, 10.00, 30.00);

        StoreSettings settings = StoreSettings.defaultSettings();
        Path targetPath = tempDir.resolve("thermal_receipt_80mm.pdf");
        Path result = pdfService.generateReceiptPdf(invoice, Collections.singletonList(item), settings, true, targetPath);

        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertTrue(Files.size(result) > 500);
    }

    @Test
    @DisplayName("M4.PDF.3: Multi-item invoice breakdown with discount and regional VAT")
    public void testMultiItemPdfWithVatAndDiscount() throws IOException {
        InvoiceItem item1 = new InvoiceItem(1, 103, 1, "Clean Architecture", "CA-01", 1, 40.00, 40.00);
        InvoiceItem item2 = new InvoiceItem(2, 103, 2, "Pilot G2 Gel Pen", "PEN-02", 4, 2.50, 10.00);
        List<InvoiceItem> items = Arrays.asList(item1, item2);

        StoreSettings ukSettings = StoreSettings.defaultSettings();
        ukSettings.applyPreset(RegionalPreset.UK); // 20% VAT, GBP £

        BillingService.CalculationResult calc = BillingService.calculateInvoiceItems(items, 10.0, ukSettings, 60.00);

        Invoice invoice = new Invoice(103, 103, "Arthur Dent", LocalDate.now(),
                Collections.emptyList(), calc.getSubtotal(), calc.getDiscountPercent(), calc.getDiscountAmount(), calc.getNetTotal());

        Path pdfPath = pdfService.generateInvoicePdf(invoice, items, ukSettings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 1000);
    }

    @Test
    @DisplayName("M4.PDF.4: Gulf UAE bilingual invoice generation with Arabic labels")
    public void testGulfUaeBilingualPdf() throws IOException {
        InvoiceItem item = new InvoiceItem(1, 104, 1, "Arabic Calligraphy Kit", "CAL-01", 1, 120.00, 120.00);

        StoreSettings uaeSettings = StoreSettings.defaultSettings();
        uaeSettings.applyPreset(RegionalPreset.GULF_UAE); // 5% VAT, AED, Bilingual

        Invoice invoice = new Invoice(104, 104, "Tariq Ali", LocalDate.now(),
                Collections.emptyList(), 120.00, 0.0, 0.0, 126.00);

        Path pdfPath = pdfService.generateInvoicePdf(invoice, Collections.singletonList(item), uaeSettings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 1000);

        // Verify Arabic text preservation in PDF content
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfPath.toString());
        com.lowagie.text.pdf.parser.PdfTextExtractor extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);
        reader.close();

        assertNotNull(text);
        assertTrue(text.contains("فاتورة ضريبية") || text.contains("TAX INVOICE"), "Invoice must contain title text");
        assertTrue(text.contains("فاتورة ضريبية"), "Arabic title 'فاتورة ضريبية' must be preserved");
        assertTrue(text.contains("الصنف"), "Arabic column label 'الصنف' must be preserved");
        assertTrue(text.contains("شكراً لزيارتكم"), "Arabic footer 'شكراً لزيارتكم' must be preserved");
    }

    @Test
    @DisplayName("M4.PDF.5: Saudi Arabia 15% VAT and Qatar 0% VAT receipts")
    public void testSaudiAndQatarReceipts() throws IOException {
        InvoiceItem item = new InvoiceItem(1, 105, 1, "Scientific Calculator", "CALC-99", 1, 100.00, 100.00);

        StoreSettings saudiSettings = StoreSettings.defaultSettings();
        saudiSettings.applyPreset(RegionalPreset.GULF_SAUDI);

        Invoice saudiInv = new Invoice(105, 105, "Omar", LocalDate.now(),
                Collections.emptyList(), 100.00, 0.0, 0.0, 115.00);
        Path saudiPdf = pdfService.generateInvoicePdf(saudiInv, Collections.singletonList(item), saudiSettings);
        assertTrue(Files.exists(saudiPdf));

        StoreSettings qatarSettings = StoreSettings.defaultSettings();
        qatarSettings.applyPreset(RegionalPreset.GULF_QATAR);

        Invoice qatarInv = new Invoice(106, 106, "Rashid", LocalDate.now(),
                Collections.emptyList(), 100.00, 0.0, 0.0, 100.00);
        Path qatarPdf = pdfService.generateInvoicePdf(qatarInv, Collections.singletonList(item), qatarSettings);
        assertTrue(Files.exists(qatarPdf));
    }

    @Test
    @DisplayName("M4.PDF.6: Invoices directory fallback resolution")
    public void testInvoicesDirectoryResolution() {
        PdfInvoiceService defaultService = new PdfInvoiceService();
        Path dir = defaultService.getInvoicesDirectory();
        assertNotNull(dir);
        assertTrue(Files.exists(dir));
    }
}
