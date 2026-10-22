package com.example.t.controller;

import com.example.t.dao.InvoiceDao;
import com.example.t.dao.SettingsDao;
import com.example.t.dao.ZReportDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.StoreSettings;
import com.example.t.model.ZReport;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ShiftManagementControllerTest {

    private static final String TEST_DB = "shift_controller_test.db";
    private ZReportDao zReportDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;

    @BeforeAll
    public void setupDb() throws SQLException {
        File f = new File(TEST_DB);
        if (f.exists()) f.delete();
        DatabaseManager.setCustomDatabasePath(java.nio.file.Path.of(TEST_DB));

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS financial_z_reports (" +
                    "z_report_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "z_sequence_number INTEGER UNIQUE NOT NULL, " +
                    "report_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "period_start TIMESTAMP NOT NULL, " +
                    "period_end TIMESTAMP NOT NULL, " +
                    "opening_float REAL DEFAULT 0.0, " +
                    "total_cash_sales REAL NOT NULL, " +
                    "total_card_sales REAL NOT NULL, " +
                    "total_credit_sales REAL NOT NULL, " +
                    "gross_sales REAL NOT NULL, " +
                    "total_discount REAL NOT NULL, " +
                    "taxable_sales REAL NOT NULL, " +
                    "total_tax REAL NOT NULL, " +
                    "net_sales REAL NOT NULL, " +
                    "transaction_count INTEGER NOT NULL, " +
                    "expected_drawer_cash REAL NOT NULL, " +
                    "actual_drawer_cash REAL NOT NULL, " +
                    "cash_over_short REAL NOT NULL, " +
                    "cumulative_grand_total REAL NOT NULL, " +
                    "closed_by_user TEXT DEFAULT 'admin', " +
                    "notes TEXT);");
        }

        zReportDao = new ZReportDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
    }

    @AfterAll
    public void tearDown() {
        DatabaseManager.setCustomDatabasePath(null);
        File f = new File(TEST_DB);
        if (f.exists()) f.delete();
    }

    @Test
    @DisplayName("Verify ShiftManagementController default shift state & opening float")
    public void testControllerInitialization() {
        ShiftManagementController controller = new ShiftManagementController();
        controller.setZReportDao(zReportDao);
        controller.setInvoiceDao(invoiceDao);
        controller.setSettingsDao(settingsDao);

        assertTrue(controller.isShiftOpen(), "New shift must start in OPEN state");
        assertEquals(100.0, controller.getOpeningFloat(), 0.01, "Default starting float is $100.00");
        assertEquals(0.0, controller.getTotalPayIn(), 0.01);
        assertEquals(0.0, controller.getTotalPayOut(), 0.01);
    }

    @Test
    @DisplayName("Verify Z-Report historical loading and sequence continuity")
    public void testZReportPersistenceAndHistory() throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ZReport r1 = new ZReport(1, now, now, 100.0, 250.0, 150.0, 0.0,
                400.0, 20.0, 380.0, 31.35, 411.35, 12, 350.0, 350.0, 0.0, 411.35, "admin", "Balanced");
        zReportDao.saveZReport(r1);

        List<ZReport> list = zReportDao.getAllZReports();
        assertFalse(list.isEmpty(), "Historical reports must not be empty");
        assertEquals(1, list.get(0).getZSequenceNumber());
        assertEquals(411.35, list.get(0).getCumulativeGrandTotal(), 0.01);

        int nextSeq = zReportDao.getNextSequenceNumber();
        assertEquals(2, nextSeq, "Next consecutive fiscal sequence number must be 2");
    }
}
