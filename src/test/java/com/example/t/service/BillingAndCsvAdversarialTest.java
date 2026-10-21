package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService.CalculationResult;
import com.example.t.service.BillingService.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - Adversarial Challenge: Billing Calculation Engine & CSV Synchronization")
public class BillingAndCsvAdversarialTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // PART 1: BILLING ENGINE ADVERSARIAL & BOUNDARY TESTING
    // =========================================================================

    @Nested
    @DisplayName("Billing Calculations - Extreme Boundaries & Stress")
    class BillingBoundaryTests {

        @Test
        @DisplayName("Boundary: 0% discount preserves taxable amount exactly equal to subtotal")
        void testZeroPercentDiscount() {
            List<OrderItem> items = Arrays.asList(
                    new OrderItem(1, "Book Alpha", "Books", 3, 29.99, "BK-A"),
                    new OrderItem(2, "Stationery Beta", "Stationery", 5, 4.25, "ST-B")
            );
            // subtotal = round(3 * 29.99 + 5 * 4.25) = round(89.97 + 21.25) = round(111.22) = 111.22
            // discount = 0.00
            // taxable = 111.22
            // tax (8.25%) = round(111.22 * 0.0825) = round(9.17565) = 9.18
            // net = 111.22 + 9.18 = 120.40
            CalculationResult res = BillingService.calculate(items, 0.0, 8.25, 150.00);

            assertEquals(111.22, res.getSubtotal(), 0.0001);
            assertEquals(0.00, res.getDiscountPercent(), 0.0001);
            assertEquals(0.00, res.getDiscountAmount(), 0.0001);
            assertEquals(111.22, res.getTaxableAmount(), 0.0001);
            assertEquals(9.18, res.getTaxAmount(), 0.0001);
            assertEquals(120.40, res.getNetTotal(), 0.0001);
            assertEquals(29.60, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("Boundary: 100% discount zeroes out taxable amount, tax, and net total")
        void testOneHundredPercentDiscount() {
            List<OrderItem> items = Arrays.asList(
                    new OrderItem(1, "Promotional Book", "Books", 2, 50.00, "BK-P1"),
                    new OrderItem(2, "Gift Pen", "Stationery", 1, 25.50, "ST-G1")
            );
            // subtotal = 125.50
            // discount = 125.50
            // taxable = 0.00
            // tax = 0.00
            // net = 0.00
            CalculationResult res = BillingService.calculate(items, 100.0, 15.0, 0.00);

            assertEquals(125.50, res.getSubtotal(), 0.0001);
            assertEquals(100.0, res.getDiscountPercent(), 0.0001);
            assertEquals(125.50, res.getDiscountAmount(), 0.0001);
            assertEquals(0.00, res.getTaxableAmount(), 0.0001);
            assertEquals(0.00, res.getTaxAmount(), 0.0001);
            assertEquals(0.00, res.getNetTotal(), 0.0001);
            assertEquals(0.00, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());

            // If customer still tenders cash on 100% discount, change is full cash tendered
            CalculationResult resWithCash = BillingService.calculate(items, 100.0, 15.0, 50.00);
            assertEquals(0.00, resWithCash.getNetTotal(), 0.0001);
            assertEquals(50.00, resWithCash.getChangeDue(), 0.0001);
            assertTrue(resWithCash.isCashSufficient());
        }

        @Test
        @DisplayName("Stress: Large quantities and seven-figure totals ($1,000,000+) without overflow or precision loss")
        void testLargeQuantitiesAndSevenFigureTotals() {
            // Enterprise institutional order: 50,000 textbooks @ $85.50 each
            // subtotal = 50,000 * 85.50 = 4,275,000.00
            // discount 12.5% = round(4,275,000.00 * 0.125) = 534,375.00
            // taxable = 4,275,000.00 - 534,375.00 = 3,740,625.00
            // tax 8.25% = round(3,740,625.00 * 0.0825) = round(308,601.5625) = 308,601.56
            // net total = 3,740,625.00 + 308,601.56 = 4,049,226.56
            // Cash tendered: 5,000,000.00
            // change due = 5,000,000.00 - 4,049,226.56 = 950,773.44
            OrderItem bulkBooks = new OrderItem(1, "Enterprise Textbook Lot", "Books", 50000, 85.50, "ENT-01");
            CalculationResult res = BillingService.calculate(
                    Collections.singletonList(bulkBooks), 12.5, 8.25, 5000000.00);

            assertEquals(4275000.00, res.getSubtotal(), 0.0001);
            assertEquals(534375.00, res.getDiscountAmount(), 0.0001);
            assertEquals(3740625.00, res.getTaxableAmount(), 0.0001);
            assertEquals(308601.56, res.getTaxAmount(), 0.0001);
            assertEquals(4049226.56, res.getNetTotal(), 0.0001);
            assertEquals(950773.44, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("Stress: Multiple items summing to over $10,000,000.00")
        void testTenMillionDollarOrder() {
            List<OrderItem> megaCart = Arrays.asList(
                    new OrderItem(1, "Rare Collector Manuscript", "Books", 10, 500000.00, "RARE-1"),
                    new OrderItem(2, "University Library Archive", "Books", 100, 50000.00, "ARCH-1"),
                    new OrderItem(3, "Custom Stationery Set", "Stationery", 20000, 25.00, "ST-BULK")
            );
            // 10 * 500,000 = 5,000,000
            // 100 * 50,000  = 5,000,000
            // 20,000 * 25   = 500,000
            // Subtotal = 10,500,000.00
            // Discount 0%, Tax 5% (UAE)
            // Tax = 525,000.00, Net = 11,025,000.00
            CalculationResult res = BillingService.calculate(megaCart, 0.0, 5.0, 12000000.00);

            assertEquals(10500000.00, res.getSubtotal(), 0.0001);
            assertEquals(525000.00, res.getTaxAmount(), 0.0001);
            assertEquals(11025000.00, res.getNetTotal(), 0.0001);
            assertEquals(975000.00, res.getChangeDue(), 0.0001);
        }

        @Test
        @DisplayName("Boundary: Sub-cent fractional unit prices and line vs order rounding")
        void testSubCentFractionalPrices() {
            // Bulk stationery components priced at sub-cent rates:
            // Item 1: Paperclips @ $0.005 each, qty 100 -> raw = 0.50
            // Item 2: Staples @ $0.0025 each, qty 200 -> raw = 0.50
            // Item 3: Rubber bands @ $0.004 each, qty 1 -> raw = 0.004 -> rounded line subtotal = 0.00
            OrderItem clips = new OrderItem(1, "Paperclip bulk", "Stationery", 100, 0.005, "PC-1");
            OrderItem staples = new OrderItem(2, "Staples bulk", "Stationery", 200, 0.0025, "ST-1");
            OrderItem singleBand = new OrderItem(3, "Single Band", "Stationery", 1, 0.004, "RB-1");

            assertEquals(0.50, clips.getLineSubtotal(), 0.0001);
            assertEquals(0.50, staples.getLineSubtotal(), 0.0001);
            assertEquals(0.00, singleBand.getLineSubtotal(), 0.0001);

            // In total calculation: raw = 0.50 + 0.50 + 0.004 = 1.004 -> rounded subtotal = 1.00
            CalculationResult res = BillingService.calculate(
                    Arrays.asList(clips, staples, singleBand), 0.0, 0.0, 2.00);
            assertEquals(1.00, res.getSubtotal(), 0.0001);
            assertEquals(1.00, res.getNetTotal(), 0.0001);
            assertEquals(1.00, res.getChangeDue(), 0.0001);

            // Test sub-cent rounding up at 0.005 boundary:
            OrderItem tieBreak = new OrderItem(4, "Tie Break Item", "Stationery", 1, 0.005, "TB-1");
            CalculationResult resTie = BillingService.calculate(
                    Collections.singletonList(tieBreak), 0.0, 0.0, 1.00);
            assertEquals(0.01, resTie.getSubtotal(), 0.0001);
        }

        @Test
        @DisplayName("Boundary: Fractional tax rates and HALF_UP rounding tie-breaking")
        void testFractionalTaxRates() {
            // Unusual or municipal fractional tax rates
            // Subtotal = 100.00, Tax rate = 8.875% -> tax = 8.875 -> 8.88
            OrderItem item1 = new OrderItem(1, "Item", "General", 1, 100.00, "SKU-1");
            CalculationResult res1 = BillingService.calculate(
                    Collections.singletonList(item1), 0.0, 8.875, 110.00);
            assertEquals(8.88, res1.getTaxAmount(), 0.0001);
            assertEquals(108.88, res1.getNetTotal(), 0.0001);

            // Tax rate 7.125% on 33.33 = 2.3747625 -> 2.37
            OrderItem item2 = new OrderItem(2, "Item 2", "General", 1, 33.33, "SKU-2");
            CalculationResult res2 = BillingService.calculate(
                    Collections.singletonList(item2), 0.0, 7.125, 40.00);
            assertEquals(2.37, res2.getTaxAmount(), 0.0001);
            assertEquals(35.70, res2.getNetTotal(), 0.0001);

            // Extreme tiny fractional rate: 0.001% on $10,000.00 = $0.10
            OrderItem item3 = new OrderItem(3, "Item 3", "General", 1, 10000.00, "SKU-3");
            CalculationResult res3 = BillingService.calculate(
                    Collections.singletonList(item3), 0.0, 0.001, 10001.00);
            assertEquals(0.10, res3.getTaxAmount(), 0.0001);
            assertEquals(10000.10, res3.getNetTotal(), 0.0001);
        }

        @Test
        @DisplayName("Boundary: Exact change due calculation down to individual cents")
        void testExactChangeDue() {
            OrderItem item = new OrderItem(1, "Notebook", "Stationery", 7, 19.99, "NB-7");
            // subtotal = 7 * 19.99 = 139.93
            // discount 7.5% = round(139.93 * 0.075) = round(10.49475) = 10.49
            // taxable = 139.93 - 10.49 = 129.44
            // tax 8.25% = round(129.44 * 0.0825) = round(10.6788) = 10.68
            // net total = 129.44 + 10.68 = 140.12
            CalculationResult res = BillingService.calculate(
                    Collections.singletonList(item), 7.5, 8.25, 140.12);

            assertEquals(140.12, res.getNetTotal(), 0.0001);
            assertEquals(0.00, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("Boundary: Insufficient cash tendered rejection and negative change due")
        void testInsufficientCashRejection() {
            OrderItem item = new OrderItem(1, "Expensive Reference Book", "Books", 1, 299.99, "REF-1");
            // Net Total with 0% discount and 8.25% tax:
            // Subtotal = 299.99, Tax = 24.75, Net Total = 324.74

            // Case A: 1 cent short
            CalculationResult res1CentShort = BillingService.calculate(
                    Collections.singletonList(item), 0.0, 8.25, 324.73);
            assertEquals(324.74, res1CentShort.getNetTotal(), 0.0001);
            assertEquals(-0.01, res1CentShort.getChangeDue(), 0.0001);
            assertFalse(res1CentShort.isCashSufficient(), "1 cent short must NOT be sufficient");

            // Case B: Zero cash tendered
            CalculationResult resZeroCash = BillingService.calculate(
                    Collections.singletonList(item), 0.0, 8.25, 0.00);
            assertEquals(324.74, resZeroCash.getNetTotal(), 0.0001);
            assertEquals(-324.74, resZeroCash.getChangeDue(), 0.0001);
            assertFalse(resZeroCash.isCashSufficient(), "Zero cash must NOT be sufficient");
        }

        @Test
        @DisplayName("Validation: Negative inputs throw IllegalArgumentException")
        void testNegativeValidationRejections() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderItem(1, "Bad Qty", "Cat", -1, 10.0, "SKU"));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderItem(1, "Bad Price", "Cat", 1, -0.01, "SKU"));

            OrderItem validItem = new OrderItem(1, "Good", "Cat", 1, 10.0, "SKU");
            assertThrows(IllegalArgumentException.class, () ->
                    BillingService.calculate(Collections.singletonList(validItem), -0.01, 5.0, 20.0));
            assertThrows(IllegalArgumentException.class, () ->
                    BillingService.calculate(Collections.singletonList(validItem), 100.01, 5.0, 20.0));
            assertThrows(IllegalArgumentException.class, () ->
                    BillingService.calculate(Collections.singletonList(validItem), 10.0, -0.01, 20.0));
            assertThrows(IllegalArgumentException.class, () ->
                    BillingService.calculate(Collections.singletonList(validItem), 10.0, 5.0, -0.01));
        }
    }

    // =========================================================================
    // PART 2: MULTI-REGIONAL PRESET MATH VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("Multi-Regional Preset Math Verification")
    class MultiRegionalPresetMathTests {

        private List<OrderItem> createTestBasket() {
            return Arrays.asList(
                    new OrderItem(1, "Design Patterns", "Books", 2, 45.00, "BK-DP"),       // 90.00
                    new OrderItem(2, "Fountain Pen Ink", "Stationery", 3, 12.50, "ST-INK"), // 37.50
                    new OrderItem(3, "Sticky Notes Pad", "Stationery", 4, 3.75, "ST-SN")    // 15.00
            );
            // Basket Subtotal = 90.00 + 37.50 + 15.00 = 142.50
        }

        @Test
        @DisplayName("USA Preset: Taxable = Subtotal - Discount, Sales Tax = Taxable * 8.25%, Net = Taxable + Tax")
        void testUsaPresetMath() {
            List<OrderItem> cart = createTestBasket();
            // Subtotal = 142.50
            // Discount = 10% = round(14.25) = 14.25
            // Taxable = 142.50 - 14.25 = 128.25
            // Tax (8.25%) = round(128.25 * 0.0825) = round(10.580625) = 10.58
            // Net Total = 128.25 + 10.58 = 138.83
            // Cash = 150.00 -> Change = 11.17
            CalculationResult res = BillingService.calculateForRegion(cart, 10.0, RegionalPreset.USA, 150.00);

            assertEquals(142.50, res.getSubtotal(), 0.0001);
            assertEquals(14.25, res.getDiscountAmount(), 0.0001);
            assertEquals(128.25, res.getTaxableAmount(), 0.0001);
            assertEquals(8.25, res.getTaxRate(), 0.0001);
            assertEquals(10.58, res.getTaxAmount(), 0.0001);
            assertEquals(138.83, res.getNetTotal(), 0.0001);
            assertEquals(11.17, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("UK Preset: Taxable = Subtotal - Discount, VAT = Taxable * 20.00%, Net = Taxable + Tax")
        void testUkPresetMath() {
            List<OrderItem> cart = createTestBasket();
            // Subtotal = 142.50
            // Discount = 20% = round(28.50) = 28.50
            // Taxable = 142.50 - 28.50 = 114.00
            // Tax (20.00%) = round(114.00 * 0.20) = 22.80
            // Net Total = 114.00 + 22.80 = 136.80
            // Cash = 140.00 -> Change = 3.20
            CalculationResult res = BillingService.calculateForRegion(cart, 20.0, RegionalPreset.UK, 140.00);

            assertEquals(142.50, res.getSubtotal(), 0.0001);
            assertEquals(28.50, res.getDiscountAmount(), 0.0001);
            assertEquals(114.00, res.getTaxableAmount(), 0.0001);
            assertEquals(20.00, res.getTaxRate(), 0.0001);
            assertEquals(22.80, res.getTaxAmount(), 0.0001);
            assertEquals(136.80, res.getNetTotal(), 0.0001);
            assertEquals(3.20, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("UAE Preset: Taxable = Subtotal - Discount, VAT = Taxable * 5.00%, Net = Taxable + Tax")
        void testUaePresetMath() {
            List<OrderItem> cart = createTestBasket();
            // Subtotal = 142.50
            // Discount = 5% = round(142.50 * 0.05) = round(7.125) = 7.13
            // Taxable = 142.50 - 7.13 = 135.37
            // Tax (5.00%) = round(135.37 * 0.05) = round(6.7685) = 6.77
            // Net Total = 135.37 + 6.77 = 142.14
            // Cash = 150.00 -> Change = 7.86
            CalculationResult res = BillingService.calculateForRegion(cart, 5.0, RegionalPreset.GULF_UAE, 150.00);

            assertEquals(142.50, res.getSubtotal(), 0.0001);
            assertEquals(7.13, res.getDiscountAmount(), 0.0001);
            assertEquals(135.37, res.getTaxableAmount(), 0.0001);
            assertEquals(5.00, res.getTaxRate(), 0.0001);
            assertEquals(6.77, res.getTaxAmount(), 0.0001);
            assertEquals(142.14, res.getNetTotal(), 0.0001);
            assertEquals(7.86, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("Saudi Arabia Preset: Taxable = Subtotal - Discount, VAT = Taxable * 15.00%, Net = Taxable + Tax")
        void testSaudiArabiaPresetMath() {
            List<OrderItem> cart = createTestBasket();
            // Subtotal = 142.50
            // Discount = 15% = round(142.50 * 0.15) = round(21.375) = 21.38
            // Taxable = 142.50 - 21.38 = 121.12
            // Tax (15.00%) = round(121.12 * 0.15) = round(18.168) = 18.17
            // Net Total = 121.12 + 18.17 = 139.29
            // Cash = 200.00 -> Change = 60.71
            CalculationResult res = BillingService.calculateForRegion(cart, 15.0, RegionalPreset.GULF_SAUDI, 200.00);

            assertEquals(142.50, res.getSubtotal(), 0.0001);
            assertEquals(21.38, res.getDiscountAmount(), 0.0001);
            assertEquals(121.12, res.getTaxableAmount(), 0.0001);
            assertEquals(15.00, res.getTaxRate(), 0.0001);
            assertEquals(18.17, res.getTaxAmount(), 0.0001);
            assertEquals(139.29, res.getNetTotal(), 0.0001);
            assertEquals(60.71, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("Qatar Preset: Taxable = Subtotal - Discount, VAT = Taxable * 0.00%, Net = Taxable")
        void testQatarPresetMath() {
            List<OrderItem> cart = createTestBasket();
            // Subtotal = 142.50
            // Discount = 10% = 14.25
            // Taxable = 128.25
            // Tax (0.00%) = 0.00
            // Net Total = 128.25
            // Cash = 130.00 -> Change = 1.75
            CalculationResult res = BillingService.calculateForRegion(cart, 10.0, RegionalPreset.GULF_QATAR, 130.00);

            assertEquals(142.50, res.getSubtotal(), 0.0001);
            assertEquals(14.25, res.getDiscountAmount(), 0.0001);
            assertEquals(128.25, res.getTaxableAmount(), 0.0001);
            assertEquals(0.00, res.getTaxRate(), 0.0001);
            assertEquals(0.00, res.getTaxAmount(), 0.0001);
            assertEquals(128.25, res.getNetTotal(), 0.0001);
            assertEquals(1.75, res.getChangeDue(), 0.0001);
            assertTrue(res.isCashSufficient());
        }

        @Test
        @DisplayName("StoreSettings Integration: Switching presets and applying custom tax rate")
        void testStoreSettingsPresetApplication() {
            StoreSettings settings = StoreSettings.defaultSettings();
            assertEquals("USA", settings.getRegionPreset());
            assertEquals(8.25, settings.getTaxRate(), 0.0001);

            settings.applyPreset(RegionalPreset.GULF_SAUDI);
            assertEquals("GULF_SAUDI", settings.getRegionPreset());
            assertEquals("SAR", settings.getCurrencyCode());
            assertEquals(15.0, settings.getTaxRate(), 0.0001);
            assertEquals("BILINGUAL_AR_EN", settings.getReceiptLanguage());

            settings.applyPreset(RegionalPreset.UK);
            assertEquals("GBP", settings.getCurrencyCode());
            assertEquals(20.0, settings.getTaxRate(), 0.0001);
            assertEquals("EN", settings.getReceiptLanguage());

            // Custom configuration
            settings.setTaxRate(11.25);
            List<OrderItem> items = Collections.singletonList(new OrderItem(1, "Item", "C", 1, 100.0, "SKU"));
            CalculationResult res = BillingService.calculateOrder(items, 0.0, settings, 120.0);
            assertEquals(11.25, res.getTaxAmount(), 0.0001);
            assertEquals(111.25, res.getNetTotal(), 0.0001);
        }

        @Test
        @DisplayName("RegionalPreset parsing aliases and case-insensitivity")
        void testPresetParsingRobustness() {
            assertEquals(RegionalPreset.USA, RegionalPreset.fromString("usa"));
            assertEquals(RegionalPreset.USA, RegionalPreset.fromString("US"));
            assertEquals(RegionalPreset.USA, RegionalPreset.fromString("United States"));

            assertEquals(RegionalPreset.UK, RegionalPreset.fromString("uk"));
            assertEquals(RegionalPreset.UK, RegionalPreset.fromString("GB"));
            assertEquals(RegionalPreset.UK, RegionalPreset.fromString("United Kingdom"));

            assertEquals(RegionalPreset.GULF_UAE, RegionalPreset.fromString("uae"));
            assertEquals(RegionalPreset.GULF_UAE, RegionalPreset.fromString("AED"));
            assertEquals(RegionalPreset.GULF_UAE, RegionalPreset.fromString("Emirates"));

            assertEquals(RegionalPreset.GULF_SAUDI, RegionalPreset.fromString("saudi"));
            assertEquals(RegionalPreset.GULF_SAUDI, RegionalPreset.fromString("KSA"));
            assertEquals(RegionalPreset.GULF_SAUDI, RegionalPreset.fromString("SAR"));

            assertEquals(RegionalPreset.GULF_QATAR, RegionalPreset.fromString("qatar"));
            assertEquals(RegionalPreset.GULF_QATAR, RegionalPreset.fromString("QAR"));

            assertEquals(RegionalPreset.CUSTOM, RegionalPreset.fromString("Custom"));
            assertEquals(RegionalPreset.CUSTOM, RegionalPreset.fromString("Germany")); // unknown maps to CUSTOM
            assertEquals(RegionalPreset.USA, RegionalPreset.fromString(null)); // null falls back to USA
            assertEquals(RegionalPreset.USA, RegionalPreset.fromString("   ")); // blank falls back to USA
        }
    }

    // =========================================================================
    // PART 3: CSV SYNCHRONIZATION RESILIENCE TESTING
    // =========================================================================

    @Nested
    @DisplayName("CSV Synchronization - RFC 4180 Stress & Resilience")
    class CsvSyncResilienceTests {

        @Test
        @DisplayName("Quotes inside quotes: Escaping (\"\"Hello\"\") and round-trip fidelity")
        void testQuotesInsideQuotes() {
            // String containing quotes: He said "Hello"
            String original = "He said \"Hello\"";
            String escaped = CsvSyncService.escapeField(original);
            assertEquals("\"He said \"\"Hello\"\"\"", escaped);

            List<List<String>> parsed = CsvSyncService.parseCsv(escaped);
            assertEquals(1, parsed.size());
            assertEquals(1, parsed.get(0).size());
            assertEquals(original, parsed.get(0).get(0));

            // Complex nested quote patterns
            List<String> complexRow = Arrays.asList(
                    "SKU-Q1",
                    "Book \"The \"Grand\" Finale\"",
                    "\"Already Surrounded By Quotes\"",
                    "Triple \"\"\" Quotes",
                    "39.99"
            );
            String formattedRow = CsvSyncService.formatRow(complexRow);
            List<List<String>> parsedRow = CsvSyncService.parseCsv(formattedRow);

            assertEquals(1, parsedRow.size());
            assertEquals(complexRow.size(), parsedRow.get(0).size());
            for (int i = 0; i < complexRow.size(); i++) {
                assertEquals(complexRow.get(i), parsedRow.get(0).get(i), "Mismatch at index " + i);
            }
        }

        @Test
        @DisplayName("Multi-line descriptions with CRLF (\\r\\n) and LF (\\n) inside quotes")
        void testMultiLineDescriptionsWithCrlf() {
            String multilineDesc = "Line 1: Summary of book\r\nLine 2: Table of Contents\r\nLine 3: Index";
            List<String> row1 = Arrays.asList("BK-ML-1", multilineDesc, "49.95");
            List<String> row2 = Arrays.asList("BK-SL-2", "Standard Single Line Description", "19.99");

            String csvText = CsvSyncService.formatRow(row1) + "\r\n" + CsvSyncService.formatRow(row2) + "\r\n";

            List<List<String>> parsedRows = CsvSyncService.parseCsv(csvText);
            assertEquals(2, parsedRows.size(), "Multi-line field must NOT create additional CSV rows");

            List<String> parsedRow1 = parsedRows.get(0);
            assertEquals("BK-ML-1", parsedRow1.get(0));
            assertEquals(multilineDesc, parsedRow1.get(1), "CRLF characters inside quotes must remain intact");
            assertEquals("49.95", parsedRow1.get(2));

            List<String> parsedRow2 = parsedRows.get(1);
            assertEquals("BK-SL-2", parsedRow2.get(0));
            assertEquals("Standard Single Line Description", parsedRow2.get(1));
            assertEquals("19.99", parsedRow2.get(2));

            // Also test UNIX newline \n inside quotes
            String unixMultiline = "First\nSecond\nThird";
            String unixCsv = CsvSyncService.formatRow(Arrays.asList("UNIX-1", unixMultiline));
            List<List<String>> parsedUnix = CsvSyncService.parseCsv(unixCsv);
            assertEquals(1, parsedUnix.size());
            assertEquals(unixMultiline, parsedUnix.get(0).get(1));
        }

        @Test
        @DisplayName("Commas inside fields: Quoted properly per RFC 4180 and preserved without splitting")
        void testCommasInsideFields() {
            List<String> row = Arrays.asList(
                    "SKU-COMMA",
                    "Calculus, Volume 1: Single Variable, 8th Edition",
                    "Author: Stewart, James",
                    "Publisher, City, Year: Cengage, Boston, 2020",
                    "125.00"
            );
            String formatted = CsvSyncService.formatRow(row);
            List<List<String>> parsed = CsvSyncService.parseCsv(formatted);

            assertEquals(1, parsed.size());
            assertEquals(5, parsed.get(0).size());
            assertEquals("Calculus, Volume 1: Single Variable, 8th Edition", parsed.get(0).get(1));
            assertEquals("Author: Stewart, James", parsed.get(0).get(2));
            assertEquals("Publisher, City, Year: Cengage, Boston, 2020", parsed.get(0).get(3));

            // Mixed commas, quotes, and newlines in a single field
            String combined = "Field with , comma, \"quotes\",\r\nand CRLF line break";
            String formattedCombined = CsvSyncService.formatRow(Collections.singletonList(combined));
            List<List<String>> parsedCombined = CsvSyncService.parseCsv(formattedCombined);
            assertEquals(1, parsedCombined.size());
            assertEquals(combined, parsedCombined.get(0).get(0));
        }

        @Test
        @DisplayName("Unicode and Arabic names: Complete UTF-8 round-trip fidelity in memory and file export/import")
        void testUnicodeAndArabicFidelity() throws IOException {
            Product p1 = new Product(1, "رواية قواعد العشق الأربعون", "Books", "AR-BK-001",
                    "إليف ش Shafak", "دار الآداب", "الطبعة الرابعة", 30.0, 55.0, 45, 5);
            Product p2 = new Product(2, "دفتر ملاحظات فاخر جلد طبيعي", "Stationery", "AR-ST-002",
                    "صناعة يدوية", "قرطاسية الرياض", "2026", 15.0, 28.5, 120, 10);
            Product p3 = new Product(3, "ديوان المتنبي - دراسة وتحقيق", "Books", "AR-BK-003",
                    "أبو الطيب المتنبي", "دار المعارف", "مجلد فاخر", 40.0, 75.0, 15, 3);
            Product p4 = new Product(4, "Special Symbols & Currency £, €, ¥, ﷼, د.إ", "Stationery", "SYM-001",
                    "Global Author — “Best Edition” ©", "World Press ™", "1st", 10.0, 19.99, 50, 5);

            List<Product> catalog = Arrays.asList(p1, p2, p3, p4);

            // Export to temporary file
            Path csvFile = tempDir.resolve("arabic_catalog.csv");
            CsvSyncService.exportProductsToFile(catalog, csvFile);
            assertTrue(Files.exists(csvFile));

            // Verify file bytes are valid UTF-8
            String rawContent = Files.readString(csvFile, StandardCharsets.UTF_8);
            assertTrue(rawContent.contains("رواية قواعد العشق الأربعون"));
            assertTrue(rawContent.contains("إليف ش Shafak"));
            assertTrue(rawContent.contains("دفتر ملاحظات فاخر جلد طبيعي"));
            assertTrue(rawContent.contains("أبو الطيب المتنبي"));
            assertTrue(rawContent.contains("£, €, ¥, ﷼, د.إ"));

            // Import back from file and verify 100% round-trip match
            List<Product> imported = CsvSyncService.importProductsFromFile(csvFile);
            assertEquals(4, imported.size());

            assertEquals("AR-BK-001", imported.get(0).getSku());
            assertEquals("رواية قواعد العشق الأربعون", imported.get(0).getProductName());
            assertEquals(55.0, imported.get(0).getPrice(), 0.0001);
            assertEquals(45, imported.get(0).getQuantity());
            assertEquals("إليف ش Shafak", imported.get(0).getAuthor());

            assertEquals("AR-ST-002", imported.get(1).getSku());
            assertEquals("دفتر ملاحظات فاخر جلد طبيعي", imported.get(1).getProductName());
            assertEquals(28.5, imported.get(1).getPrice(), 0.0001);
            assertEquals(120, imported.get(1).getQuantity());

            assertEquals("AR-BK-003", imported.get(2).getSku());
            assertEquals("ديوان المتنبي - دراسة وتحقيق", imported.get(2).getProductName());

            assertEquals("SYM-001", imported.get(3).getSku());
            assertEquals("Special Symbols & Currency £, €, ¥, ﷼, د.إ", imported.get(3).getProductName());
        }

        @Test
        @DisplayName("Empty fields in leading, middle, and trailing positions")
        void testEmptyFields() {
            // Row with empty middle fields
            String csv1 = "SKU-1,,35.00,,Author Name\n";
            List<List<String>> parsed1 = CsvSyncService.parseCsv(csv1);
            assertEquals(1, parsed1.size());
            assertEquals(Arrays.asList("SKU-1", "", "35.00", "", "Author Name"), parsed1.get(0));

            // Row with all empty fields
            String csv2 = ",,,\n";
            List<List<String>> parsed2 = CsvSyncService.parseCsv(csv2);
            assertEquals(1, parsed2.size());
            assertEquals(Arrays.asList("", "", "", ""), parsed2.get(0));

            // Quoted empty strings
            String csv3 = "\"\",\"\",val1,\"\"\n";
            List<List<String>> parsed3 = CsvSyncService.parseCsv(csv3);
            assertEquals(1, parsed3.size());
            assertEquals(Arrays.asList("", "", "val1", ""), parsed3.get(0));

            // Leading empty field
            String csv4 = ",second,third\n";
            List<List<String>> parsed4 = CsvSyncService.parseCsv(csv4);
            assertEquals(1, parsed4.size());
            assertEquals(Arrays.asList("", "second", "third"), parsed4.get(0));
        }

        @Test
        @DisplayName("Trailing commas resilience: Preserves empty trailing columns without dropping")
        void testTrailingCommas() {
            // Trailing comma at end of line without newline
            String csv1 = "a,b,c,";
            List<List<String>> parsed1 = CsvSyncService.parseCsv(csv1);
            assertEquals(1, parsed1.size());
            assertEquals(Arrays.asList("a", "b", "c", ""), parsed1.get(0));

            // Trailing comma followed by CRLF
            String csv2 = "a,b,c,\r\n";
            List<List<String>> parsed2 = CsvSyncService.parseCsv(csv2);
            assertEquals(1, parsed2.size());
            assertEquals(Arrays.asList("a", "b", "c", ""), parsed2.get(0));

            // Multiple trailing commas followed by CRLF
            String csv3 = "a,b,,,\r\n";
            List<List<String>> parsed3 = CsvSyncService.parseCsv(csv3);
            assertEquals(1, parsed3.size());
            assertEquals(Arrays.asList("a", "b", "", "", ""), parsed3.get(0));

            // Multiple rows with varying trailing commas
            String csv4 = "row1,col1,\r\nrow2,col1,col2\r\n";
            List<List<String>> parsed4 = CsvSyncService.parseCsv(csv4);
            assertEquals(2, parsed4.size());
            assertEquals(Arrays.asList("row1", "col1", ""), parsed4.get(0));
            assertEquals(Arrays.asList("row2", "col1", "col2"), parsed4.get(1));
        }

        @Test
        @DisplayName("Product CSV Import resilience: Malformed rows safely skipped, headers detected")
        void testImportProductsResilience() {
            String malformedCsv = "sku,name,price,stock_quantity,author\n" +
                    "BK-OK,Valid Book,25.00,10,Valid Author\n" +
                    "CORRUPT_ROW_TOO_FEW_COLUMNS\n" +
                    "BK-BAD-PRICE,Bad Price,NOT_A_NUMBER,5,Author\n" +
                    "BK-BAD-QTY,Bad Qty,15.00,NOT_AN_INT,Author\n" + // handled by catch or defaults
                    ",,,,\n" +
                    "BK-OK-2,Second Valid Book,40.00,20,Author 2\n";

            List<Product> products = CsvSyncService.importProductsFromCsv(malformedCsv);
            // BK-OK and BK-OK-2 must be imported
            assertTrue(products.size() >= 2, "Must import at least valid products without crashing");
            assertEquals("BK-OK", products.get(0).getSku());
            assertEquals("Valid Book", products.get(0).getProductName());
            assertEquals(25.00, products.get(0).getPrice(), 0.0001);

            Product last = products.get(products.size() - 1);
            assertEquals("BK-OK-2", last.getSku());
            assertEquals("Second Valid Book", last.getProductName());
            assertEquals(40.00, last.getPrice(), 0.0001);
        }

        @Test
        @DisplayName("Invoice Ledger Appending: Multiple sequential appends preserve header and data rows")
        void testLedgerAppendSequentialIntegrity() throws IOException {
            Path ledger = tempDir.resolve("invoice_history_adversarial.csv");

            Invoice inv1 = new Invoice(1001, 1001, "محمد عبد الرحمن (Muhammad Abdul Rahman)",
                    LocalDate.of(2026, 10, 19), null, 1500.00, 150.00, 202.50, 1552.50);
            Invoice inv2 = new Invoice(1002, 1002, "Store \"VIP\" Client, Riyadh Branch",
                    LocalDate.of(2026, 10, 20), null, 450.00, 0.00, 22.50, 472.50);
            Invoice inv3 = new Invoice(1003, 1003, null, // null customer name defaults to Walk-in Customer
                    LocalDate.of(2026, 10, 20), null, 99.00, 0.00, 8.17, 107.17);

            // Append 1st -> creates file with header
            CsvSyncService.appendInvoiceToLedger(inv1, ledger);
            assertTrue(Files.exists(ledger));

            // Append 2nd and 3rd -> must NOT write additional headers
            CsvSyncService.appendInvoiceToLedger(inv2, ledger);
            CsvSyncService.appendInvoiceToLedger(inv3, ledger);

            String fileContent = Files.readString(ledger, StandardCharsets.UTF_8);
            List<List<String>> parsed = CsvSyncService.parseCsv(fileContent);

            // 1 header + 3 invoices = 4 rows
            assertEquals(4, parsed.size(), "Ledger must contain exactly 1 header + 3 invoice rows");
            assertEquals("Invoice ID", parsed.get(0).get(0));
            assertEquals("Invoice Date", parsed.get(0).get(1));
            assertEquals("Customer Name", parsed.get(0).get(2));

            // Row 1: Arabic name preserved
            assertEquals("1001", parsed.get(1).get(0));
            assertEquals("محمد عبد الرحمن (Muhammad Abdul Rahman)", parsed.get(1).get(2));

            // Row 2: Quotes and commas preserved
            assertEquals("1002", parsed.get(2).get(0));
            assertEquals("Store \"VIP\" Client, Riyadh Branch", parsed.get(2).get(2));

            // Row 3: Null customer name defaults to "Walk-in Customer"
            assertEquals("1003", parsed.get(3).get(0));
            assertEquals("Walk-in Customer", parsed.get(3).get(2));
        }
    }
}
