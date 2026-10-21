package com.example.t.database;

import com.example.t.PasswordUtil;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.model.StoreSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - LegacyDataImporter Unit Tests")
public class LegacyDataImporterTest {

    @TempDir
    Path tempDir;

    private Path legacyDir;

    @BeforeEach
    public void setUp() throws IOException {
        Path testDb = tempDir.resolve("legacy_test.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();

        legacyDir = tempDir.resolve("legacy_source");
        Files.createDirectories(legacyDir);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Legacy Data Importer migrates stock, shop data, password, and invoices")
    public void testFullLegacyImport() throws IOException, SQLException {
        // 1. Create mock stock.txt
        String stockContent = "0,Gel Pen Blue,50,2.5,1.2,true\n" +
                "0,Advanced Math Book,15,45.0,30.0,true\n";
        Files.writeString(legacyDir.resolve("stock.txt"), stockContent);

        // 2. Create mock Shop_Data.txt
        String shopContent = "Legacy Heritage Bookstore\nOld Town High Street 44\n+1 555-4321\n";
        Files.writeString(legacyDir.resolve("Shop_Data.txt"), shopContent);

        // 3. Create mock password.txt
        String passwordContent = "legacy_secret_123\n";
        Files.writeString(legacyDir.resolve("password.txt"), passwordContent);

        // 4. Create mock invoice_history.txt
        String invoiceContent = "Invoice ID: 88\n" +
                "Order Date: 2024-05-10\n" +
                "Customer Name: Sherlock Holmes\n" +
                "Total Amount: $5.00\n" +
                "Order Details:\n" +
                "Product Name\tQuantity\tPrice\tDiscount %\n" +
                "Gel Pen Blue\t2\t2.50\t0.0\n\n";
        Files.writeString(legacyDir.resolve("invoice_history.txt"), invoiceContent);

        // Execute import
        try (Connection conn = DatabaseManager.getConnection()) {
            int imported = LegacyDataImporter.importFromDirectory(conn, legacyDir);
            assertTrue(imported > 0, "Importer should report positive components/records imported");
        }

        // Verify Shop Settings
        SettingsDao settingsDao = new SettingsDao();
        StoreSettings settings = settingsDao.getSettings();
        assertEquals("Legacy Heritage Bookstore", settings.getStoreName());
        assertEquals("Old Town High Street 44", settings.getStoreAddress());
        assertEquals("+1 555-4321", settings.getStorePhone());
        assertTrue(PasswordUtil.verifyPassword("legacy_secret_123", settings.getAdminPasswordHash()),
                "Hashed password must verify against legacy plain text");

        // Verify Stock
        ProductDao productDao = new ProductDao();
        var pen = productDao.searchByName("Gel Pen Blue");
        assertFalse(pen.isEmpty(), "Gel Pen Blue must be present in database");
        assertEquals(50, pen.get(0).getQuantity());

        var book = productDao.searchByName("Advanced Math Book");
        assertFalse(book.isEmpty(), "Advanced Math Book must be present in database");
        assertEquals(15, book.get(0).getQuantity());

        // Verify Invoices
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM invoices WHERE customer_name = 'Sherlock Holmes';")) {
            assertTrue(rs.next(), "Imported invoice must be in database");
            assertEquals("INV-LEGACY-88", rs.getString("invoice_number"));
        }
    }

    @Test
    @DisplayName("Verify Legacy Importer Idempotency - duplicate imports do not duplicate products")
    public void testIdempotentImport() throws IOException, SQLException {
        String stockContent = "0,Unique Eraser,100,1.0,0.5,true\n";
        Files.writeString(legacyDir.resolve("stock.txt"), stockContent);

        try (Connection conn = DatabaseManager.getConnection()) {
            int firstRun = LegacyDataImporter.importStock(conn, legacyDir);
            assertEquals(1, firstRun);

            // Re-run
            int secondRun = LegacyDataImporter.importStock(conn, legacyDir);
            assertEquals(0, secondRun, "Second import run must skip existing product");
        }

        ProductDao productDao = new ProductDao();
        var matches = productDao.searchByName("Unique Eraser");
        assertEquals(1, matches.size(), "Product must exist exactly once");
    }
}
