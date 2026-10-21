package com.example.t;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1 Empirical Challenge: Product & Invoice Models")
public class ModelEmpiricalChallengeTest {

    private Path appDataInvoiceFile;
    private Path srcInvoiceFile;
    private Path srcBackupFile;

    @BeforeEach
    public void setUp() throws Exception {
        String appDataPath = FilePathManager.getInvoiceHistoryFilePath();
        if (appDataPath != null) {
            appDataInvoiceFile = Paths.get(appDataPath);
        }
        srcInvoiceFile = Paths.get("src/main/invoice_history.txt");
        srcBackupFile = Paths.get("src/main/invoice_history.txt.test_backup");

        // Backup existing src/main/invoice_history.txt if it exists
        if (Files.exists(srcInvoiceFile)) {
            Files.copy(srcInvoiceFile, srcBackupFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Remove temporary test file in AppData
        if (appDataInvoiceFile != null && Files.exists(appDataInvoiceFile)) {
            try {
                Files.deleteIfExists(appDataInvoiceFile);
            } catch (IOException ignored) {}
        }

        // Restore src/main/invoice_history.txt from backup
        if (Files.exists(srcBackupFile)) {
            Files.move(srcBackupFile, srcInvoiceFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // =========================================================================
    // SECTION 1: JavaFX Property ChangeListener & Binding Verification
    // =========================================================================

    @Test
    @DisplayName("Product: Mutating all 14 properties must trigger JavaFX ChangeListeners")
    public void testProductPropertyChangeListeners() {
        Product p = new Product(1, "Original Book", 10, 20.0, 10.0);

        AtomicBoolean idFired = new AtomicBoolean(false);
        p.productIdProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.intValue() == 99) idFired.set(true);
        });
        p.setProductId(99);
        assertTrue(idFired.get(), "productIdProperty must fire ChangeListener on mutation");

        AtomicBoolean nameFired = new AtomicBoolean(false);
        p.productNameProperty().addListener((obs, oldVal, newVal) -> {
            if ("Updated Book".equals(newVal)) nameFired.set(true);
        });
        p.setProductName("Updated Book");
        assertTrue(nameFired.get(), "productNameProperty must fire ChangeListener on mutation");

        AtomicBoolean categoryFired = new AtomicBoolean(false);
        p.categoryProperty().addListener((obs, oldVal, newVal) -> {
            if ("Stationery".equals(newVal)) categoryFired.set(true);
        });
        p.setCategory("Stationery");
        assertTrue(categoryFired.get(), "categoryProperty must fire ChangeListener on mutation");

        AtomicBoolean skuFired = new AtomicBoolean(false);
        p.skuProperty().addListener((obs, oldVal, newVal) -> {
            if ("SKU-NEW-99".equals(newVal)) skuFired.set(true);
        });
        p.setSku("SKU-NEW-99");
        assertTrue(skuFired.get(), "skuProperty must fire ChangeListener on mutation");

        AtomicBoolean authorFired = new AtomicBoolean(false);
        p.authorProperty().addListener((obs, oldVal, newVal) -> {
            if ("Arthur Conan Doyle".equals(newVal)) authorFired.set(true);
        });
        p.setAuthor("Arthur Conan Doyle");
        assertTrue(authorFired.get(), "authorProperty must fire ChangeListener on mutation");

        AtomicBoolean publisherFired = new AtomicBoolean(false);
        p.publisherProperty().addListener((obs, oldVal, newVal) -> {
            if ("Penguin Books".equals(newVal)) publisherFired.set(true);
        });
        p.setPublisher("Penguin Books");
        assertTrue(publisherFired.get(), "publisherProperty must fire ChangeListener on mutation");

        AtomicBoolean editionFired = new AtomicBoolean(false);
        p.editionProperty().addListener((obs, oldVal, newVal) -> {
            if ("3rd Edition".equals(newVal)) editionFired.set(true);
        });
        p.setEdition("3rd Edition");
        assertTrue(editionFired.get(), "editionProperty must fire ChangeListener on mutation");

        AtomicBoolean purchasePriceFired = new AtomicBoolean(false);
        p.purchasePriceProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 14.50) < 0.001) purchasePriceFired.set(true);
        });
        p.setPurchasePrice(14.50);
        assertTrue(purchasePriceFired.get(), "purchasePriceProperty must fire ChangeListener on mutation");

        AtomicBoolean priceFired = new AtomicBoolean(false);
        p.priceProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 29.99) < 0.001) priceFired.set(true);
        });
        p.setPrice(29.99);
        assertTrue(priceFired.get(), "priceProperty must fire ChangeListener on mutation");

        AtomicBoolean qtyFired = new AtomicBoolean(false);
        p.quantityProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.intValue() == 42) qtyFired.set(true);
        });
        p.setQuantity(42);
        assertTrue(qtyFired.get(), "quantityProperty must fire ChangeListener on mutation");

        AtomicBoolean thresholdFired = new AtomicBoolean(false);
        p.minStockThresholdProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.intValue() == 12) thresholdFired.set(true);
        });
        p.setMinStockThreshold(12);
        assertTrue(thresholdFired.get(), "minStockThresholdProperty must fire ChangeListener on mutation");

        AtomicBoolean discountFired = new AtomicBoolean(false);
        p.discountProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 15.0) < 0.001) discountFired.set(true);
        });
        p.setDiscount(15.0);
        assertTrue(discountFired.get(), "discountProperty must fire ChangeListener on mutation");

        AtomicBoolean statusFired = new AtomicBoolean(false);
        p.statusProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.booleanValue()) statusFired.set(true);
        });
        p.setStatus(false);
        assertTrue(statusFired.get(), "statusProperty must fire ChangeListener on mutation");

        AtomicBoolean discountedPriceFired = new AtomicBoolean(false);
        p.discountedPriceProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 19.99) < 0.001) discountedPriceFired.set(true);
        });
        p.setDiscountedPrice(19.99);
        assertTrue(discountedPriceFired.get(), "discountedPriceProperty must fire ChangeListener on mutation");
    }

    @Test
    @DisplayName("Invoice: Mutating all 8 properties must trigger JavaFX ChangeListeners")
    public void testInvoicePropertyChangeListeners() {
        Invoice inv = new Invoice();

        AtomicBoolean idFired = new AtomicBoolean(false);
        inv.invoiceIdProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.intValue() == 555) idFired.set(true);
        });
        inv.setInvoiceId(555);
        assertTrue(idFired.get(), "invoiceIdProperty must fire ChangeListener on mutation");

        AtomicBoolean orderNumFired = new AtomicBoolean(false);
        inv.orderNumProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.intValue() == 888) orderNumFired.set(true);
        });
        inv.setOrderNum(888);
        assertTrue(orderNumFired.get(), "orderNumProperty must fire ChangeListener on mutation");

        AtomicBoolean nameFired = new AtomicBoolean(false);
        inv.customerNameProperty().addListener((obs, oldVal, newVal) -> {
            if ("Ahmed Al-Mansoor".equals(newVal)) nameFired.set(true);
        });
        inv.setCustomerName("Ahmed Al-Mansoor");
        assertTrue(nameFired.get(), "customerNameProperty must fire ChangeListener on mutation");

        AtomicBoolean dateFired = new AtomicBoolean(false);
        LocalDate targetDate = LocalDate.of(2026, 12, 31);
        inv.invoiceDateProperty().addListener((obs, oldVal, newVal) -> {
            if (targetDate.equals(newVal)) dateFired.set(true);
        });
        inv.setInvoiceDate(targetDate);
        assertTrue(dateFired.get(), "invoiceDateProperty must fire ChangeListener on mutation");

        AtomicBoolean totalFired = new AtomicBoolean(false);
        inv.orderTotalProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 250.75) < 0.001) totalFired.set(true);
        });
        inv.setOrderTotal(250.75);
        assertTrue(totalFired.get(), "orderTotalProperty must fire ChangeListener on mutation");

        AtomicBoolean discPctFired = new AtomicBoolean(false);
        inv.discountPercentageProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 12.5) < 0.001) discPctFired.set(true);
        });
        inv.setDiscountPercentage(12.5);
        assertTrue(discPctFired.get(), "discountPercentageProperty must fire ChangeListener on mutation");

        AtomicBoolean discFired = new AtomicBoolean(false);
        inv.discountProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 31.34) < 0.001) discFired.set(true);
        });
        inv.setDiscount(31.34);
        assertTrue(discFired.get(), "discountProperty must fire ChangeListener on mutation");

        AtomicBoolean netPriceFired = new AtomicBoolean(false);
        inv.netPriceProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - 219.41) < 0.001) netPriceFired.set(true);
        });
        inv.setNetPrice(219.41);
        assertTrue(netPriceFired.get(), "netPriceProperty must fire ChangeListener on mutation");
    }

    @Test
    @DisplayName("Models must handle null values in setters safely without throwing NullPointerException")
    public void testNullSafetyInModelSetters() {
        Product p = new Product();
        assertDoesNotThrow(() -> p.setProductName(null));
        assertEquals("", p.getProductName());

        assertDoesNotThrow(() -> p.setCategory(null));
        assertEquals("", p.getCategory());

        assertDoesNotThrow(() -> p.setSku(null));
        assertEquals("", p.getSku());

        assertDoesNotThrow(() -> p.setAuthor(null));
        assertEquals("", p.getAuthor());

        assertDoesNotThrow(() -> p.setPublisher(null));
        assertEquals("", p.getPublisher());

        assertDoesNotThrow(() -> p.setEdition(null));
        assertEquals("", p.getEdition());

        Invoice inv = new Invoice();
        assertDoesNotThrow(() -> inv.setCustomerName(null));
        assertEquals("", inv.getCustomerName());

        assertDoesNotThrow(() -> inv.setInvoiceDate(null));
        assertNotNull(inv.getInvoiceDate());

        assertDoesNotThrow(() -> inv.setProducts(null));
        assertNotNull(inv.getProducts());
        assertTrue(inv.getProducts().isEmpty());
    }

    // =========================================================================
    // SECTION 2: Discount Calculations & Boundary Values
    // =========================================================================

    @Test
    @DisplayName("Discount: 0%, 50%, 100% boundary calculations")
    public void testStandardDiscountBoundaries() {
        Product p = new Product(10, "Test Pen", 100, 200.0, 100.0);

        // 0% discount
        p.setDiscount(0.0);
        assertEquals(200.0, p.getDiscountedPrice(), 0.001);

        // 50% discount
        p.setDiscount(50.0);
        assertEquals(100.0, p.getDiscountedPrice(), 0.001);

        // 100% discount (free item)
        p.setDiscount(100.0);
        assertEquals(0.0, p.getDiscountedPrice(), 0.001);
    }

    @Test
    @DisplayName("Discount & Price Boundary: Zero quantity, zero price, and negative price")
    public void testPriceAndQuantityBoundaries() {
        // Zero price product
        Product freeItem = new Product(11, "Free Promotional Bookmark", 50, 0.0, 0.0);
        freeItem.setDiscount(10.0);
        assertEquals(0.0, freeItem.getDiscountedPrice(), 0.001);

        // Zero quantity product
        Product outOfStock = new Product(12, "Out of Stock Book", 0, 45.0, 20.0);
        assertEquals(0, outOfStock.getQuantity());

        // Negative quantity (e.g. inventory discrepancy or return)
        outOfStock.setQuantity(-5);
        assertEquals(-5, outOfStock.getQuantity());

        // Negative price boundary
        Product negativePrice = new Product(13, "Defective Refund Item", 1, -30.0, 0.0);
        negativePrice.setDiscount(10.0);
        // -30.0 * (1 - 0.10) = -27.0
        assertEquals(-27.0, negativePrice.getDiscountedPrice(), 0.001);
    }

    @Test
    @DisplayName("Discount Boundary: Over 100% discount and negative discount behavior")
    public void testAtypicalDiscountValues() {
        Product p = new Product(14, "Special Item", 10, 100.0, 50.0);

        // Over 100% discount: 150% discount yields negative discounted price (store pays customer)
        p.setDiscount(150.0);
        assertEquals(-50.0, p.getDiscountedPrice(), 0.001);

        // Negative discount: -20% acts as a markup: 100 * (1 - (-0.20)) = 120.0
        p.setDiscount(-20.0);
        assertEquals(120.0, p.getDiscountedPrice(), 0.001);
    }

    @Test
    @DisplayName("Empirical Verification: setPrice does not automatically recalculate discountedPrice until calculateDiscountedPrice is invoked")
    public void testPriceMutationDiscountedPriceSync() {
        Product p = new Product(15, "Marker", 20, 100.0, 50.0);
        p.setDiscount(10.0); // 10% discount -> 90.0
        assertEquals(90.0, p.getDiscountedPrice(), 0.001);

        // When setPrice is changed to 200.0 without re-calling setDiscount
        p.setPrice(200.0);
        // Note: In Product.java, discountedPrice is NOT bound to priceProperty,
        // so it retains 90.0 until calculateDiscountedPrice() is invoked.
        assertEquals(90.0, p.getDiscountedPrice(), 0.001,
                "Empirical observation: setPrice does not recalculate discountedPrice automatically");

        // Invoking calculateDiscountedPrice explicitly updates it:
        p.calculateDiscountedPrice();
        assertEquals(180.0, p.getDiscountedPrice(), 0.001,
                "calculateDiscountedPrice updates discountedPrice to reflect new price");
    }

    // =========================================================================
    // SECTION 3: readInvoicesFromFile Empirical Stress Testing
    // =========================================================================

    private void writeToInvoiceFile(String content) throws IOException {
        if (appDataInvoiceFile != null) {
            Files.createDirectories(appDataInvoiceFile.getParent());
            Files.writeString(appDataInvoiceFile, content);
        } else {
            Files.writeString(srcInvoiceFile, content);
        }
    }

    @Test
    @DisplayName("readInvoicesFromFile: Missing file returns empty list with ZERO exceptions")
    public void testReadInvoicesFromMissingFile() throws IOException {
        // Ensure no invoice file exists in AppData or src/main
        if (appDataInvoiceFile != null && Files.exists(appDataInvoiceFile)) {
            Files.deleteIfExists(appDataInvoiceFile);
        }
        if (Files.exists(srcInvoiceFile)) {
            Files.deleteIfExists(srcInvoiceFile);
        }
        Path rootFile = Paths.get("invoice_history.txt");
        if (Files.exists(rootFile)) {
            Files.deleteIfExists(rootFile);
        }

        assertDoesNotThrow(() -> {
            List<Invoice> result = Invoice.readInvoicesFromFile();
            assertNotNull(result, "Result must never be null when file is missing");
            assertTrue(result.isEmpty(), "Result must be empty when file is missing");
        });
    }

    @Test
    @DisplayName("readInvoicesFromFile: Empty file (0 bytes) returns empty list with ZERO exceptions")
    public void testReadInvoicesFromEmptyFile() throws IOException {
        writeToInvoiceFile("");

        assertDoesNotThrow(() -> {
            List<Invoice> result = Invoice.readInvoicesFromFile();
            assertNotNull(result);
            assertTrue(result.isEmpty(), "Empty file must yield empty list");
        });
    }

    @Test
    @DisplayName("readInvoicesFromFile: Corrupted arbitrary binary content returns safely with ZERO exceptions")
    public void testReadInvoicesFromCorruptedBinary() throws IOException {
        byte[] binaryGarbage = new byte[]{
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
                (byte) 0x00, (byte) 0xFF, (byte) 0xFE, (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF
        };
        if (appDataInvoiceFile != null) {
            Files.createDirectories(appDataInvoiceFile.getParent());
            Files.write(appDataInvoiceFile, binaryGarbage);
        } else {
            Files.write(srcInvoiceFile, binaryGarbage);
        }

        assertDoesNotThrow(() -> {
            List<Invoice> result = Invoice.readInvoicesFromFile();
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("readInvoicesFromFile: Malformed numeric values and dates return safely with ZERO exceptions")
    public void testReadInvoicesFromMalformedNumericValues() throws IOException {
        String malformedContent =
                "Invoice ID: not_a_valid_integer\n" +
                "Order Date: 32-13-2026_invalid_date\n" +
                "Customer Name: Malformed Tester\n" +
                "Total Amount: Rs.NotANumber\n" +
                "Order Details:\n" +
                "Product Name\tQuantity\tPrice\tDiscount %\n" +
                "Pen\tbad_qty\tbad_price\tbad_discount\n" +
                "Book\t10\t49.99\t5.0\n" +
                "\n" +
                "Invoice ID: 99999999999999999999999999999999999999999\n" + // Overflow integer
                "Order Date: 2026-10-19\n" +
                "Customer Name: Large Number Tester\n" +
                "Total Amount: $$$$$$\n" +
                "Order Details:\n" +
                "Notebook\t2\t15.00\n" + // Missing discount column
                "\n";

        writeToInvoiceFile(malformedContent);

        assertDoesNotThrow(() -> {
            List<Invoice> result = Invoice.readInvoicesFromFile();
            assertNotNull(result);
            assertEquals(2, result.size());

            // First invoice should have defaulted invoiceId to 0, totalAmount to 0.0, and 1 valid item
            Invoice inv1 = result.get(0);
            assertEquals(0, inv1.getInvoiceId());
            assertEquals("Malformed Tester", inv1.getCustomerName());
            assertEquals(0.0, inv1.getOrderTotal(), 0.001);
            assertEquals(1, inv1.getProducts().size(), "Only the valid line item should be parsed");
            assertEquals("Book", inv1.getProducts().get(0).getProductName());

            // Second invoice should have handled numeric overflow safely
            Invoice inv2 = result.get(1);
            assertEquals(0, inv2.getInvoiceId()); // overflow caught, stays 0
            assertEquals("Large Number Tester", inv2.getCustomerName());
            assertEquals(0.0, inv2.getOrderTotal(), 0.001); // non-numeric total caught, stays 0.0
            assertEquals(1, inv2.getProducts().size());
            assertEquals("Notebook", inv2.getProducts().get(0).getProductName());
        });
    }

    @Test
    @DisplayName("readInvoicesFromFile: Incomplete records ending without trailing newline flush correctly")
    public void testReadInvoicesTrailingFlush() throws IOException {
        String content =
                "Invoice ID: 888\n" +
                "Order Date: 2026-05-15\n" +
                "Customer Name: Trailing Customer\n" +
                "Total Amount: $75.50\n" +
                "Order Details:\n" +
                "Product Name\tQuantity\tPrice\tDiscount %\n" +
                "Geometry Box\t1\t75.50\t0.0"; // No newline at end of file

        writeToInvoiceFile(content);

        List<Invoice> result = Invoice.readInvoicesFromFile();
        assertNotNull(result);
        assertEquals(1, result.size());
        Invoice inv = result.get(0);
        assertEquals(888, inv.getInvoiceId());
        assertEquals("Trailing Customer", inv.getCustomerName());
        assertEquals(75.50, inv.getOrderTotal(), 0.001);
        assertEquals(1, inv.getProducts().size());
        assertEquals("Geometry Box", inv.getProducts().get(0).getProductName());
    }

    @Test
    @DisplayName("readInvoicesFromFile: Multi-currency symbol stripping ($ and Rs)")
    public void testReadInvoicesCurrencyStripping() throws IOException {
        String content =
                "Invoice ID: 101\n" +
                "Order Date: 2026-01-01\n" +
                "Customer Name: US Customer\n" +
                "Total Amount: $120.50\n" +
                "Order Details:\n" +
                "Book A\t1\t120.50\t0.0\n" +
                "\n" +
                "Invoice ID: 102\n" +
                "Order Date: 2026-01-02\n" +
                "Customer Name: PK Customer\n" +
                "Total Amount: Rs350.00\n" +
                "Order Details:\n" +
                "Book B\t1\t350.00\t0.0\n";

        writeToInvoiceFile(content);

        List<Invoice> result = Invoice.readInvoicesFromFile();
        assertEquals(2, result.size());
        assertEquals(120.50, result.get(0).getOrderTotal(), 0.001);
        assertEquals(350.00, result.get(1).getOrderTotal(), 0.001);
    }
}
