package com.example.t;

import com.example.t.controller.SettingsController;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4 - Empirical Challenge: OpenPDF Invoice Generation & Regional Settings")
public class Milestone4PdfAndRegionalEmpiricalChallengeTest {

    @TempDir
    Path tempDir;

    private PdfInvoiceService pdfService;
    private SettingsDao settingsDao;
    private SettingsController controller;

    @BeforeAll
    public static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already running
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        Path testDb = tempDir.resolve("challenge_bookstore.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();

        pdfService = new PdfInvoiceService(tempDir.resolve("test_invoices"));
        settingsDao = new SettingsDao();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/Settings.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "Settings FXML must load cleanly");
        controller = loader.getController();
        assertNotNull(controller, "SettingsController instance must be present");
        controller.setSettingsDao(settingsDao);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    // =========================================================================
    // SECTION 1: PdfInvoiceService Empirical Stress & Robustness Tests
    // =========================================================================

    @Test
    @DisplayName("PDF-CHALLENGE-01: Stress test with 100 line items across multiple A4 pages")
    public void testPdfGenerationWithHugeNumberOfLineItems100A4() throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        double subtotal = 0.0;
        for (int i = 1; i <= 100; i++) {
            double price = 10.0 + (i % 15);
            int qty = (i % 5) + 1;
            double lineTotal = Math.round(price * qty * 100.0) / 100.0;
            items.add(new InvoiceItem(i, 5001, i, "Bulk Textbook Volume #" + i + " - Edition 2026", "SKU-TXT-" + i, qty, price, lineTotal));
            subtotal += lineTotal;
        }

        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.USA);

        double tax = Math.round(subtotal * 0.0825 * 100.0) / 100.0;
        double net = Math.round((subtotal + tax) * 100.0) / 100.0;
        Invoice invoice = new Invoice(5001, 5001, "University Library Acquisitions", LocalDate.now(),
                Collections.emptyList(), subtotal, 0.0, 0.0, net);

        Path pdfFile = tempDir.resolve("invoices_out").resolve("stress_100_items.pdf");
        Path generated = pdfService.generateReceiptPdf(invoice, items, settings, false, pdfFile);

        assertNotNull(generated);
        assertTrue(Files.exists(generated), "100-item A4 PDF must be created on disk");
        assertTrue(Files.size(generated) > 5000, "100-item A4 PDF size should exceed 5KB (multi-page)");
        assertValidPdfHeader(generated);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-02: Stress test with 250 line items in 80mm thermal receipt format")
    public void testPdfGenerationWithMassiveLineItems250Thermal80mm() throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        double subtotal = 0.0;
        for (int i = 1; i <= 250; i++) {
            double price = 2.50 + (i % 3);
            int qty = 1;
            double lineTotal = price;
            items.add(new InvoiceItem(i, 5002, i, "Item " + i + " Standard Lead Pencil Grade 2B", "SKU-PENCIL-" + i, qty, price, lineTotal));
            subtotal += lineTotal;
        }

        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.UK); // 20% VAT

        double tax = Math.round(subtotal * 0.20 * 100.0) / 100.0;
        double net = Math.round((subtotal + tax) * 100.0) / 100.0;
        Invoice invoice = new Invoice(5002, 5002, "Wholesale Stationer Ltd", LocalDate.now(),
                Collections.emptyList(), subtotal, 0.0, 0.0, net);

        Path thermalFile = tempDir.resolve("invoices_out").resolve("stress_250_thermal.pdf");
        Path generated = pdfService.generateReceiptPdf(invoice, items, settings, true, thermalFile);

        assertNotNull(generated);
        assertTrue(Files.exists(generated), "250-item 80mm thermal PDF must be created on disk");
        assertTrue(Files.size(generated) > 15000, "250-item thermal PDF size should exceed 15KB");
        assertValidPdfHeader(generated);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-03: Robustness against special characters, HTML/XML tags, and punctuation")
    public void testPdfGenerationWithSpecialCharactersAndMarkup() throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(1, 5003, 1, "<script>alert('xss')</script> &amp; \"Quotes\"", "SKU<>&\"", 2, 15.99, 31.98));
        items.add(new InvoiceItem(2, 5003, 2, "Math Book: {x | x > 0} ∩ [0, ∞) & π ≈ 3.14159", "SKU-#@!$", 1, 45.00, 45.00));
        items.add(new InvoiceItem(3, 5003, 3, "Symbols: @#$%^&*()_+~`|\\:;'<>,.?/", "SYM-01", 1, 10.00, 10.00));

        StoreSettings settings = StoreSettings.defaultSettings();
        settings.setStoreName("<<B&N Books & Stationery \"Special Edition\">>");
        settings.setStoreAddress("Unit #42, O'Connor Road & 5th Ave / Suite #100");

        Invoice invoice = new Invoice(5003, 5003, "Mr. O'Reilly & Sons <buyer@example.com>", LocalDate.now(),
                Collections.emptyList(), 86.98, 0.0, 0.0, 94.16);

        Path pdfPath = pdfService.generateInvoicePdf(invoice, items, settings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 1000);
        assertValidPdfHeader(pdfPath);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-04: Robustness with Arabic text, Unicode characters, and European accents")
    public void testPdfGenerationWithArabicAndUnicodeStrings() throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(1, 5004, 1, "دفتر ملاحظات مقاس A4 مسطر 80 صفحة", "AR-NOTE-01", 3, 12.50, 37.50));
        items.add(new InvoiceItem(2, 5004, 2, "قلم حبر جاف أزرق فاخر 0.7 ملم", "AR-PEN-02", 5, 4.00, 20.00));
        items.add(new InvoiceItem(3, 5004, 3, "Café Müller & Söhne - Prêt-à-porter Bücher", "EU-003", 1, 50.00, 50.00));

        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.GULF_UAE);
        settings.setStoreName("مكتبة النور والقرطاسية الحديثة");
        settings.setStoreAddress("شارع الشيخ زايد، دبي، الإمارات العربية المتحدة");

        Invoice invoice = new Invoice(5004, 5004, "سعادة أحمد بن محمد آل مكتوم", LocalDate.now(),
                Collections.emptyList(), 107.50, 0.0, 0.0, 112.88);

        Path pdfPath = pdfService.generateInvoicePdf(invoice, items, settings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 1000);
        assertValidPdfHeader(pdfPath);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-05: Robustness against extreme string lengths (500+ character customer name & address)")
    public void testPdfGenerationWithExtremeLongStringsWrapping() throws IOException {
        String longCustomer = "Dr. VeryLongFirstName " + "Supercalifragilisticexpialidocious_".repeat(15) + " Esq.";
        String longAddress = "Building 404, Complex Alpha, Sector 99, Highway Junction Outer Ring Road, District North, "
                .repeat(10);
        String longItemName = "Exhaustive Encyclopedia of Historical Cartography and Global Topographical Analysis "
                .repeat(5);

        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(1, 5005, 1, longItemName, "LONG-SKU-99999999999999999999", 1, 150.00, 150.00));

        StoreSettings settings = StoreSettings.defaultSettings();
        settings.setStoreName("Metropolitan Mega-Superstore Bookstore & Cultural Artifacts Distribution Center Incorporated");
        settings.setStoreAddress(longAddress);

        Invoice invoice = new Invoice(5005, 5005, longCustomer, LocalDate.now(),
                Collections.emptyList(), 150.00, 0.0, 0.0, 162.38);

        Path pdfPath = pdfService.generateInvoicePdf(invoice, items, settings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 1000);
        assertValidPdfHeader(pdfPath);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-06: Zero-tax invoice generation (Qatar 0% VAT preset)")
    public void testPdfGenerationZeroTaxInvoice() throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        items.add(new InvoiceItem(1, 5006, 1, "School Backpack Large", "BAG-01", 1, 150.00, 150.00));
        items.add(new InvoiceItem(2, 5006, 2, "Geometry Compass Set", "GEO-02", 2, 25.00, 50.00));

        StoreSettings qatarSettings = StoreSettings.defaultSettings();
        qatarSettings.applyPreset(RegionalPreset.GULF_QATAR); // 0.0% VAT

        assertEquals(0.0, qatarSettings.getTaxRate(), 0.001);
        assertEquals("QAR", qatarSettings.getCurrencyCode());

        Invoice invoice = new Invoice(5006, 5006, "Hamad Al-Thani", LocalDate.now(),
                Collections.emptyList(), 200.00, 0.0, 0.0, 200.00);

        Path pdfPath = pdfService.generateInvoicePdf(invoice, items, qatarSettings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 1000);
        assertValidPdfHeader(pdfPath);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-07: Boundary discounts: 0% discount and 100% full-order complimentary discount")
    public void testPdfGenerationExtremeDiscountsZeroAndHundredPercent() throws IOException {
        InvoiceItem item = new InvoiceItem(1, 5007, 1, "Promotional Free Bookmark Sample", "PR-01", 10, 1.00, 10.00);
        StoreSettings settings = StoreSettings.defaultSettings();

        // 100% discount
        Invoice freeInvoice = new Invoice(5007, 5007, "Contest Winner", LocalDate.now(),
                Collections.emptyList(), 10.00, 100.0, 10.00, 0.00);

        Path freePdf = pdfService.generateInvoicePdf(freeInvoice, Collections.singletonList(item), settings);
        assertNotNull(freePdf);
        assertTrue(Files.exists(freePdf));
        assertValidPdfHeader(freePdf);

        // 0% discount
        Invoice standardInvoice = new Invoice(5008, 5008, "Standard Buyer", LocalDate.now(),
                Collections.emptyList(), 10.00, 0.0, 0.00, 10.83);

        Path stdPdf = pdfService.generateInvoicePdf(standardInvoice, Collections.singletonList(item), settings);
        assertNotNull(stdPdf);
        assertTrue(Files.exists(stdPdf));
        assertValidPdfHeader(stdPdf);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-08: Graceful fallback when invoice contains null or empty line items")
    public void testPdfGenerationWithEmptyLineItemsFallback() throws IOException {
        StoreSettings settings = StoreSettings.defaultSettings();
        Invoice emptyInvoice = new Invoice(5009, 5009, "Empty Cart", LocalDate.now(),
                Collections.emptyList(), 0.0, 0.0, 0.0, 0.0);

        Path pdfPath = pdfService.generateInvoicePdf(emptyInvoice, Collections.emptyList(), settings);
        assertNotNull(pdfPath);
        assertTrue(Files.exists(pdfPath));
        assertValidPdfHeader(pdfPath);
    }

    @Test
    @DisplayName("PDF-CHALLENGE-09: Rejection of null invoice argument")
    public void testPdfGenerationRejectsNullInvoice() {
        StoreSettings settings = StoreSettings.defaultSettings();
        // Overload with target file enforces explicit IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            pdfService.generateReceiptPdf(null, Collections.emptyList(), settings, false, tempDir.resolve("null_test.pdf"));
        });
        // 3-arg overload dereferences invoice.getInvoiceId() before checking null, throwing NullPointerException
        assertThrows(NullPointerException.class, () -> {
            pdfService.generateInvoicePdf(null, Collections.emptyList(), settings);
        });
    }

    @Test
    @DisplayName("SECURITY-05: Empirical verification of admin fallback persistence after password change")
    public void testAdminFallbackBehaviorAfterPasswordChange() throws Exception {
        controller.unlockScreen();

        Field currProp = SettingsController.class.getDeclaredField("currentPasswordField");
        currProp.setAccessible(true);
        PasswordField curr = (PasswordField) currProp.get(controller);

        Field newProp = SettingsController.class.getDeclaredField("newPasswordField");
        newProp.setAccessible(true);
        PasswordField newPass = (PasswordField) newProp.get(controller);

        Field confProp = SettingsController.class.getDeclaredField("confirmPasswordField");
        confProp.setAccessible(true);
        PasswordField conf = (PasswordField) confProp.get(controller);

        curr.setText("admin");
        newPass.setText("BrandNewSecret#2026");
        conf.setText("BrandNewSecret#2026");
        controller.handleChangePassword(new ActionEvent());

        // Lock screen
        controller.lockScreen();
        assertFalse(controller.isUnlocked());

        Field unlockPassProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        unlockPassProp.setAccessible(true);
        PasswordField unlockPass = (PasswordField) unlockPassProp.get(controller);

        // Verify random wrong password fails
        unlockPass.setText("randomwrongpass");
        controller.handleUnlock(new ActionEvent());
        assertFalse(controller.isUnlocked(), "Random invalid password must fail");

        // Verify newly set password succeeds
        unlockPass.setText("BrandNewSecret#2026");
        controller.handleUnlock(new ActionEvent());
        assertTrue(controller.isUnlocked(), "Newly set password must unlock");

        // Relock
        controller.lockScreen();
        assertFalse(controller.isUnlocked());

        // Verify that after changing password, entering old password 'admin' fails to unlock
        unlockPass.setText("admin");
        controller.handleUnlock(new ActionEvent());
        assertFalse(controller.isUnlocked(), "Old admin password must NOT unlock after password change");
    }

    // =========================================================================
    // SECTION 2: SettingsDao SQLite Multi-Regional Persistence Round-Trips
    // =========================================================================

    @Test
    @DisplayName("REGIONAL-DAO-01: Round-trip persistence for USA Preset in SQLite")
    public void testSettingsDaoRoundTripUSA() throws SQLException {
        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.USA);
        settings.setStoreName("Liberty Books & Stationery");
        settings.setStoreAddress("742 Evergreen Terrace, Springfield, OR");
        settings.setStorePhone("555-0199");

        assertTrue(settingsDao.saveSettings(settings));

        StoreSettings loaded = settingsDao.getSettings();
        assertEquals("Liberty Books & Stationery", loaded.getStoreName());
        assertEquals("USA", loaded.getRegionPreset());
        assertEquals("USD", loaded.getCurrencyCode());
        assertEquals("$", loaded.getCurrencySymbol());
        assertEquals("Sales Tax", loaded.getTaxName());
        assertEquals(8.25, loaded.getTaxRate(), 0.001);
        assertEquals("EN", loaded.getReceiptLanguage());
    }

    @Test
    @DisplayName("REGIONAL-DAO-02: Round-trip persistence for UK Preset in SQLite")
    public void testSettingsDaoRoundTripUK() throws SQLException {
        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.UK);
        settings.setStoreName("Covent Garden Stationery Ltd");
        settings.setStoreAddress("10 Downing St, London, UK");
        settings.setStorePhone("+44 20 7946 0912");

        assertTrue(settingsDao.saveSettings(settings));

        StoreSettings loaded = settingsDao.getSettings();
        assertEquals("Covent Garden Stationery Ltd", loaded.getStoreName());
        assertEquals("UK", loaded.getRegionPreset());
        assertEquals("GBP", loaded.getCurrencyCode());
        assertEquals("£", loaded.getCurrencySymbol());
        assertEquals("VAT", loaded.getTaxName());
        assertEquals(20.00, loaded.getTaxRate(), 0.001);
        assertEquals("EN", loaded.getReceiptLanguage());
    }

    @Test
    @DisplayName("REGIONAL-DAO-03: Round-trip persistence for GULF_UAE Preset in SQLite")
    public void testSettingsDaoRoundTripGulfUae() throws SQLException {
        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.GULF_UAE);
        settings.setStoreName("Emirates Stationery House");
        settings.setCurrencySymbol("د.إ"); // Arabic symbol representation

        assertTrue(settingsDao.saveSettings(settings));

        StoreSettings loaded = settingsDao.getSettings();
        assertEquals("Emirates Stationery House", loaded.getStoreName());
        assertEquals("GULF_UAE", loaded.getRegionPreset());
        assertEquals("AED", loaded.getCurrencyCode());
        assertEquals("د.إ", loaded.getCurrencySymbol());
        assertEquals("VAT", loaded.getTaxName());
        assertEquals(5.00, loaded.getTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", loaded.getReceiptLanguage());
    }

    @Test
    @DisplayName("REGIONAL-DAO-04: Round-trip persistence for GULF_SAUDI Preset in SQLite")
    public void testSettingsDaoRoundTripGulfSaudi() throws SQLException {
        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.GULF_SAUDI);
        settings.setStoreName("Riyadh Book & Paper World");
        settings.setCurrencySymbol("ر.س");

        assertTrue(settingsDao.saveSettings(settings));

        StoreSettings loaded = settingsDao.getSettings();
        assertEquals("Riyadh Book & Paper World", loaded.getStoreName());
        assertEquals("GULF_SAUDI", loaded.getRegionPreset());
        assertEquals("SAR", loaded.getCurrencyCode());
        assertEquals("ر.س", loaded.getCurrencySymbol());
        assertEquals("VAT", loaded.getTaxName());
        assertEquals(15.00, loaded.getTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", loaded.getReceiptLanguage());
    }

    @Test
    @DisplayName("REGIONAL-DAO-05: Round-trip persistence for GULF_QATAR Preset in SQLite")
    public void testSettingsDaoRoundTripGulfQatar() throws SQLException {
        StoreSettings settings = StoreSettings.defaultSettings();
        settings.applyPreset(RegionalPreset.GULF_QATAR);
        settings.setStoreName("Doha Academic Bookstore");
        settings.setCurrencySymbol("ر.ق");

        assertTrue(settingsDao.saveSettings(settings));

        StoreSettings loaded = settingsDao.getSettings();
        assertEquals("Doha Academic Bookstore", loaded.getStoreName());
        assertEquals("GULF_QATAR", loaded.getRegionPreset());
        assertEquals("QAR", loaded.getCurrencyCode());
        assertEquals("ر.ق", loaded.getCurrencySymbol());
        assertEquals("VAT", loaded.getTaxName());
        assertEquals(0.00, loaded.getTaxRate(), 0.001);
        assertEquals("BILINGUAL_AR_EN", loaded.getReceiptLanguage());
    }

    @Test
    @DisplayName("REGIONAL-DAO-06: Sequential cycling through all 5 presets maintains singleton row constraint")
    public void testSequentialPresetCyclingMaintainsSingletonRow() throws SQLException {
        RegionalPreset[] presets = {
                RegionalPreset.USA,
                RegionalPreset.UK,
                RegionalPreset.GULF_UAE,
                RegionalPreset.GULF_SAUDI,
                RegionalPreset.GULF_QATAR,
                RegionalPreset.USA
        };

        for (RegionalPreset preset : presets) {
            StoreSettings s = settingsDao.getSettings();
            s.applyPreset(preset);
            assertTrue(settingsDao.saveSettings(s), "Saving preset " + preset.name() + " must succeed");

            StoreSettings reloaded = settingsDao.getSettings();
            assertEquals(preset.name(), reloaded.getRegionPreset());
            assertEquals(preset.getCurrencyCode(), reloaded.getCurrencyCode());
            assertEquals(preset.getDefaultTaxRate(), reloaded.getTaxRate(), 0.001);
        }

        // Verify SQLite database has strictly one row in store_settings
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM store_settings;")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "store_settings table MUST maintain exactly one row (setting_id = 1)");
        }
    }

    // =========================================================================
    // SECTION 3: SettingsController Admin Password & UI Preset Switching
    // =========================================================================

    @Test
    @DisplayName("SECURITY-01: SettingsController initializes in locked state")
    public void testSettingsControllerStartsLocked() {
        assertFalse(controller.isUnlocked(), "Settings screen must be locked on startup");
    }

    @Test
    @DisplayName("SECURITY-02: SettingsController rejects invalid passwords and remains locked")
    public void testSettingsControllerRejectsInvalidPasswords() throws Exception {
        Field passFieldProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        passFieldProp.setAccessible(true);
        PasswordField passField = (PasswordField) passFieldProp.get(controller);

        String[] badPasswords = {
                "",
                "wrong_password",
                "ADMIN",          // Case mismatch
                "Admin",
                "123456",
                "' OR 1=1 --",    // SQL injection attempt
                "root",
                "toor"
        };

        for (String bad : badPasswords) {
            passField.setText(bad);
            controller.handleUnlock(new ActionEvent());
            assertFalse(controller.isUnlocked(), "Password '" + bad + "' must NOT unlock the screen");
        }
    }

    @Test
    @DisplayName("SECURITY-03: SettingsController unlocks with valid admin password")
    public void testSettingsControllerUnlocksWithValidAdminPassword() throws Exception {
        Field passFieldProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        passFieldProp.setAccessible(true);
        PasswordField passField = (PasswordField) passFieldProp.get(controller);

        passField.setText("admin");
        controller.handleUnlock(new ActionEvent());
        assertTrue(controller.isUnlocked(), "Password 'admin' must unlock the screen");

        // Verify locking works
        controller.handleLock(new ActionEvent());
        assertFalse(controller.isUnlocked(), "Lock button must relock screen");
    }

    @Test
    @DisplayName("SECURITY-04: Admin password update hashes credential, saves to SQLite, and updates verification")
    public void testAdminPasswordUpdateAndVerification() throws Exception {
        controller.unlockScreen();

        Field currProp = SettingsController.class.getDeclaredField("currentPasswordField");
        currProp.setAccessible(true);
        PasswordField curr = (PasswordField) currProp.get(controller);

        Field newProp = SettingsController.class.getDeclaredField("newPasswordField");
        newProp.setAccessible(true);
        PasswordField newPass = (PasswordField) newProp.get(controller);

        Field confProp = SettingsController.class.getDeclaredField("confirmPasswordField");
        confProp.setAccessible(true);
        PasswordField conf = (PasswordField) confProp.get(controller);

        // 1. Test mismatched confirmation
        curr.setText("admin");
        newPass.setText("ComplexPass2026!");
        conf.setText("MismatchPass2026!");
        controller.handleChangePassword(new ActionEvent());

        // Password in DB must still be default
        StoreSettings s1 = settingsDao.getSettings();
        assertFalse(PasswordUtil.verifyPassword("ComplexPass2026!", s1.getAdminPasswordHash()));

        // 2. Test wrong current password
        curr.setText("wrongcurrent");
        newPass.setText("ComplexPass2026!");
        conf.setText("ComplexPass2026!");
        controller.handleChangePassword(new ActionEvent());

        StoreSettings s2 = settingsDao.getSettings();
        assertFalse(PasswordUtil.verifyPassword("ComplexPass2026!", s2.getAdminPasswordHash()));

        // 3. Test successful password change
        curr.setText("admin");
        newPass.setText("ComplexPass2026!");
        conf.setText("ComplexPass2026!");
        controller.handleChangePassword(new ActionEvent());

        // Verify new hash persisted to database
        StoreSettings updated = settingsDao.getSettings();
        String storedHash = updated.getAdminPasswordHash();
        assertNotNull(storedHash);
        assertTrue(storedHash.contains(":"), "Updated password hash must be in salt:hash format");
        assertTrue(PasswordUtil.verifyPassword("ComplexPass2026!", storedHash), "New password must verify against stored hash");

        // Verify that screen can be unlocked with new password
        controller.lockScreen();
        assertFalse(controller.isUnlocked());

        Field unlockPassProp = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
        unlockPassProp.setAccessible(true);
        PasswordField unlockPass = (PasswordField) unlockPassProp.get(controller);

        // Try with wrong password
        unlockPass.setText("wrongpassword");
        controller.handleUnlock(new ActionEvent());
        assertFalse(controller.isUnlocked());

        // Try with new password
        unlockPass.setText("ComplexPass2026!");
        controller.handleUnlock(new ActionEvent());
        assertTrue(controller.isUnlocked(), "Screen must unlock with newly configured password");
    }

    @Test
    @DisplayName("UI-PRESET-01: UI Preset switching updates form fields and persists correctly to SQLite across all 5 regions")
    public void testUiPresetSwitchingAndPersistenceAllFiveRegions() throws Exception {
        controller.unlockScreen();

        Field presetProp = SettingsController.class.getDeclaredField("presetComboBox");
        presetProp.setAccessible(true);
        ComboBox<String> presetCombo = (ComboBox<String>) presetProp.get(controller);

        Field currCodeProp = SettingsController.class.getDeclaredField("currencyCodeField");
        currCodeProp.setAccessible(true);
        TextField currCode = (TextField) currCodeProp.get(controller);

        Field currSymProp = SettingsController.class.getDeclaredField("currencySymbolField");
        currSymProp.setAccessible(true);
        TextField currSym = (TextField) currSymProp.get(controller);

        Field taxNameProp = SettingsController.class.getDeclaredField("taxNameField");
        taxNameProp.setAccessible(true);
        TextField taxName = (TextField) taxNameProp.get(controller);

        Field taxRateProp = SettingsController.class.getDeclaredField("taxRateField");
        taxRateProp.setAccessible(true);
        TextField taxRate = (TextField) taxRateProp.get(controller);

        AtomicBoolean callbackFired = new AtomicBoolean(false);
        controller.setOnSettingsSaved(() -> callbackFired.set(true));

        // 1. Test USA
        presetCombo.setValue("USA (USD $, 8.25% Sales Tax)");
        controller.handlePresetChange(new ActionEvent());
        assertEquals("USD", currCode.getText());
        assertEquals("$", currSym.getText());
        assertEquals("8.25", taxRate.getText());
        controller.handleSaveSettings(new ActionEvent());
        assertTrue(callbackFired.getAndSet(false));
        assertEquals("USA", settingsDao.getSettings().getRegionPreset());

        // 2. Test UK
        presetCombo.setValue("UK (GBP £, 20.0% VAT)");
        controller.handlePresetChange(new ActionEvent());
        assertEquals("GBP", currCode.getText());
        assertEquals("£", currSym.getText());
        assertEquals("20.00", taxRate.getText());
        controller.handleSaveSettings(new ActionEvent());
        assertTrue(callbackFired.getAndSet(false));
        assertEquals("UK", settingsDao.getSettings().getRegionPreset());

        // 3. Test UAE
        presetCombo.setValue("UAE (AED د.إ, 5.0% VAT, Bilingual)");
        controller.handlePresetChange(new ActionEvent());
        assertEquals("AED", currCode.getText());
        assertEquals("د.إ", currSym.getText());
        assertEquals("5.00", taxRate.getText());
        controller.handleSaveSettings(new ActionEvent());
        assertTrue(callbackFired.getAndSet(false));
        assertEquals("GULF_UAE", settingsDao.getSettings().getRegionPreset());

        // 4. Test Saudi Arabia
        presetCombo.setValue("Saudi Arabia (SAR ر.س, 15.0% VAT, Bilingual)");
        controller.handlePresetChange(new ActionEvent());
        assertEquals("SAR", currCode.getText());
        assertEquals("ر.س", currSym.getText());
        assertEquals("15.00", taxRate.getText());
        controller.handleSaveSettings(new ActionEvent());
        assertTrue(callbackFired.getAndSet(false));
        assertEquals("GULF_SAUDI", settingsDao.getSettings().getRegionPreset());

        // 5. Test Qatar
        presetCombo.setValue("Qatar (QAR ر.ق, 0.0% VAT, Bilingual)");
        controller.handlePresetChange(new ActionEvent());
        assertEquals("QAR", currCode.getText());
        assertEquals("ر.ق", currSym.getText());
        assertEquals("0.00", taxRate.getText());
        controller.handleSaveSettings(new ActionEvent());
        assertTrue(callbackFired.getAndSet(false));
        assertEquals("GULF_QATAR", settingsDao.getSettings().getRegionPreset());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void assertValidPdfHeader(Path pdfPath) throws IOException {
        byte[] magic = new byte[5];
        try (InputStream in = Files.newInputStream(pdfPath)) {
            int read = in.read(magic);
            assertEquals(5, read, "Should read 5 header bytes from PDF");
            assertEquals("%PDF-", new String(magic), "PDF file must start with '%PDF-' magic bytes");
        }
    }
}
