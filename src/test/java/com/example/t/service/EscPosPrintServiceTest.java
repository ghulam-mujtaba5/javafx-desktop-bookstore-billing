package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.model.InvoiceItem;
import com.example.t.model.InvoicePayment;
import com.example.t.model.StoreSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ESC/POS Thermal Printing & Hardware Control Unit Tests")
public class EscPosPrintServiceTest {

    private EscPosPrintService printService;
    private StoreSettings settings;
    private Invoice sampleInvoice;
    private List<InvoiceItem> sampleItems;
    private List<InvoicePayment> samplePayments;

    @BeforeEach
    void setUp() {
        printService = new EscPosPrintService();

        settings = StoreSettings.defaultSettings();
        settings.setStoreName("City Lights Bookstore");
        settings.setStoreAddress("261 Columbus Ave, San Francisco, CA");
        settings.setStorePhone("415-362-8193");
        settings.setCurrencySymbol("$");
        settings.setTaxName("Sales Tax");

        sampleInvoice = new Invoice(
                1001,
                1,
                "Jane Reader",
                LocalDate.now(),
                new ArrayList<>(),
                54.00,
                10.0,
                5.40,
                52.80,
                8.625,
                4.20,
                55.00,
                2.20,
                "SPLIT",
                "USD",
                "USA"
        );

        InvoiceItem item1 = new InvoiceItem(1, 1001, 10, "Clean Code", "BK-001", 1, 40.00, 40.00);
        InvoiceItem item2 = new InvoiceItem(2, 1001, 20, "Moleskine Classic Notebook", "ST-002", 1, 14.00, 14.00);
        item2.setNotes("Staff pick recommendation");
        sampleItems = List.of(item1, item2);

        InvoicePayment pay1 = new InvoicePayment(1001, "CASH", 20.00, 20.00, 0.00, null);
        InvoicePayment pay2 = new InvoicePayment(1001, "CARD", 32.80, 32.80, 0.00, "AUTH-9876");
        samplePayments = List.of(pay1, pay2);
    }

    @Test
    @DisplayName("Cash drawer kick pulse produces standard ESC p byte sequence")
    void testDrawerKickPulseBytes() {
        byte[] pulse = EscPosPrintService.generateDrawerKickPulse();
        assertNotNull(pulse);
        assertEquals(5, pulse.length);
        assertEquals((byte) 0x1B, pulse[0]); // ESC
        assertEquals((byte) 0x70, pulse[1]); // p
        assertEquals((byte) 0x00, pulse[2]); // pin 0 (drawer 1)
        assertEquals((byte) 0x19, pulse[3]); // 25ms on
        assertEquals((byte) 0xFA, pulse[4]); // 250ms off
    }

    @Test
    @DisplayName("80mm Thermal receipt bytecode starts with ESC @ and ends with full cut")
    void test80mmThermalReceiptStructure() throws IOException {
        byte[] receipt = printService.generateThermalReceiptBytes(
                sampleInvoice, sampleItems, samplePayments, settings, true, true
        );

        assertNotNull(receipt);
        assertTrue(receipt.length > 100);

        // Begins with ESC @ (Init)
        assertEquals((byte) 0x1B, receipt[0]);
        assertEquals((byte) 0x40, receipt[1]);

        // Second sequence contains drawer kick (0x1B, 0x70)
        assertEquals((byte) 0x1B, receipt[2]);
        assertEquals((byte) 0x70, receipt[3]);

        // Ends with Full Cut command (GS V A 0x00)
        int len = receipt.length;
        assertEquals((byte) 0x1D, receipt[len - 4]); // GS
        assertEquals((byte) 0x56, receipt[len - 3]); // V
        assertEquals((byte) 0x41, receipt[len - 2]); // A (full cut)
        assertEquals((byte) 0x00, receipt[len - 1]); // 0x00

        String text = new String(receipt, StandardCharsets.UTF_8);
        assertTrue(text.contains("City Lights Bookstore"));
        assertTrue(text.contains("Clean Code"));
        assertTrue(text.contains("Moleskine Classic Notebook"));
        assertTrue(text.contains("Staff pick recommendation"));
        assertTrue(text.contains("TOTAL DUE:"));
        assertTrue(text.contains("CASH:"));
        assertTrue(text.contains("CARD:"));
    }

    @Test
    @DisplayName("58mm Compact thermal receipt formats correctly without drawer kick")
    void test58mmThermalReceiptWithoutDrawerKick() throws IOException {
        byte[] receipt = printService.generateThermalReceiptBytes(
                sampleInvoice, sampleItems, samplePayments, settings, false, false
        );

        assertNotNull(receipt);
        assertTrue(receipt.length > 50);

        // Does not have drawer kick right after init
        assertFalse(receipt[2] == 0x1B && receipt[3] == 0x70);

        String text = new String(receipt, StandardCharsets.UTF_8);
        assertTrue(text.contains("City Lights"));
        assertTrue(text.contains("TOTAL DUE:"));
    }

    @Test
    @DisplayName("Thermal shelf label generator includes product info, price, and barcode cut")
    void testThermalShelfLabelGeneration() throws IOException {
        Product book = new Product(101, "The Hobbit", 25, 14.95, 8.50);
        book.setAuthor("J.R.R. Tolkien");
        book.setSku("9780547928227");

        byte[] labelBytes = printService.generateShelfLabelBytes(book, settings);
        assertNotNull(labelBytes);
        assertTrue(labelBytes.length > 20);

        // Begins with ESC @
        assertEquals((byte) 0x1B, labelBytes[0]);
        assertEquals((byte) 0x40, labelBytes[1]);

        // Ends with partial cut (GS V B 0x00)
        int len = labelBytes.length;
        assertEquals((byte) 0x1D, labelBytes[len - 4]);
        assertEquals((byte) 0x56, labelBytes[len - 3]);
        assertEquals((byte) 0x42, labelBytes[len - 2]);
        assertEquals((byte) 0x00, labelBytes[len - 1]);

        String text = new String(labelBytes, StandardCharsets.UTF_8);
        assertTrue(text.contains("The Hobbit"));
        assertTrue(text.contains("J.R.R. Tolkien"));
        assertTrue(text.contains("$14.95"));
        assertTrue(text.contains("*9780547928227*"));
    }
}
