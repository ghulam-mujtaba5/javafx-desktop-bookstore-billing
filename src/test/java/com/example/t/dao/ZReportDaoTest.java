package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.ZReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-A - ZReportDao Financial Z-Report Persistence Tests")
public class ZReportDaoTest {

    @TempDir
    Path tempDir;

    private ZReportDao zReportDao;

    @BeforeEach
    public void setUp() throws SQLException {
        Path testDb = tempDir.resolve("test_z_report.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();
        zReportDao = new ZReportDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify getNextSequenceNumber starts at 1 and increments consecutively")
    public void testSequenceNumberIncrement() throws SQLException {
        assertEquals(1, zReportDao.getNextSequenceNumber(), "First Z-Report sequence number must be 1");

        ZReport report1 = createSampleReport(1, 1500.0, 1500.0);
        zReportDao.saveZReport(report1);

        assertEquals(2, zReportDao.getNextSequenceNumber(), "Next Z-Report sequence number must be 2");

        ZReport report2 = createSampleReport(2, 2300.0, 3800.0);
        zReportDao.saveZReport(report2);

        assertEquals(3, zReportDao.getNextSequenceNumber(), "Next Z-Report sequence number must be 3");
    }

    @Test
    @DisplayName("Verify saveZReport, getLatestZReport, and getZReportBySequence")
    public void testSaveAndRetrieveZReport() throws SQLException {
        Timestamp start = Timestamp.valueOf("2026-10-20 08:00:00");
        Timestamp end = Timestamp.valueOf("2026-10-20 20:00:00");

        ZReport report = new ZReport();
        report.setZSequenceNumber(1);
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setOpeningFloat(200.0);
        report.setTotalCashSales(450.0);
        report.setTotalCardSales(800.0);
        report.setTotalCreditSales(50.0);
        report.setGrossSales(1300.0);
        report.setTotalDiscount(50.0);
        report.setTaxableSales(1250.0);
        report.setTotalTax(103.13);
        report.setNetSales(1353.13);
        report.setTransactionCount(24);
        report.setExpectedDrawerCash(650.0); // openingFloat (200) + totalCash (450)
        report.setActualDrawerCash(655.0);   // Cashier counted $655
        report.setCashOverShort(5.0);        // $5 over
        report.setCumulativeGrandTotal(1353.13);
        report.setClosedByUser("manager_bob");
        report.setNotes("Register 1 EOD closure. Minor $5 cash overage.");

        zReportDao.saveZReport(report);
        assertTrue(report.getZReportId() > 0, "Generated Z-Report ID must be assigned");

        // Verify latest retrieval
        Optional<ZReport> latestOpt = zReportDao.getLatestZReport();
        assertTrue(latestOpt.isPresent(), "Latest Z-Report must be present");
        ZReport latest = latestOpt.get();
        assertEquals(1, latest.getZSequenceNumber());
        assertEquals(200.0, latest.getOpeningFloat(), 0.001);
        assertEquals(450.0, latest.getTotalCashSales(), 0.001);
        assertEquals(800.0, latest.getTotalCardSales(), 0.001);
        assertEquals(1353.13, latest.getNetSales(), 0.001);
        assertEquals(24, latest.getTransactionCount());
        assertEquals(650.0, latest.getExpectedDrawerCash(), 0.001);
        assertEquals(655.0, latest.getActualDrawerCash(), 0.001);
        assertEquals(5.0, latest.getCashOverShort(), 0.001);
        assertEquals(1353.13, latest.getCumulativeGrandTotal(), 0.001);
        assertEquals("manager_bob", latest.getClosedByUser());
        assertEquals("Register 1 EOD closure. Minor $5 cash overage.", latest.getNotes());

        // Verify retrieval by sequence
        Optional<ZReport> bySeq = zReportDao.getZReportBySequence(1);
        assertTrue(bySeq.isPresent());
        assertEquals(report.getZReportId(), bySeq.get().getZReportId());

        // Non-existent sequence
        Optional<ZReport> notFound = zReportDao.getZReportBySequence(99);
        assertFalse(notFound.isPresent());
    }

    @Test
    @DisplayName("Verify cumulative grand total tracks non-resettable fiscal ledger across days")
    public void testCumulativeGrandTotalLedger() throws SQLException {
        assertEquals(0.0, zReportDao.getLatestCumulativeGrandTotal(), 0.001,
                "Initial cumulative grand total must be 0.0");

        // Day 1: Net Sales = $1,000.00 -> Cumulative = $1,000.00
        ZReport day1 = createSampleReport(1, 1000.0, 1000.0);
        zReportDao.saveZReport(day1);
        assertEquals(1000.0, zReportDao.getLatestCumulativeGrandTotal(), 0.001);

        // Day 2: Net Sales = $1,500.00 -> Cumulative = $2,500.00
        ZReport day2 = createSampleReport(2, 1500.0, 2500.0);
        zReportDao.saveZReport(day2);
        assertEquals(2500.0, zReportDao.getLatestCumulativeGrandTotal(), 0.001);

        // Day 3: Net Sales = $2,000.00 -> Cumulative = $4,500.00
        ZReport day3 = createSampleReport(3, 2000.0, 4500.0);
        zReportDao.saveZReport(day3);
        assertEquals(4500.0, zReportDao.getLatestCumulativeGrandTotal(), 0.001);

        List<ZReport> all = zReportDao.getAllZReports();
        assertEquals(3, all.size());
        assertEquals(3, all.get(0).getZSequenceNumber(), "Latest sequence should be first in DESC order");
    }

    private ZReport createSampleReport(int seq, double netSales, double cumulativeGrandTotal) {
        ZReport r = new ZReport();
        r.setZSequenceNumber(seq);
        r.setPeriodStart(new Timestamp(System.currentTimeMillis() - 86400000));
        r.setPeriodEnd(new Timestamp(System.currentTimeMillis()));
        r.setOpeningFloat(100.0);
        r.setTotalCashSales(netSales * 0.4);
        r.setTotalCardSales(netSales * 0.6);
        r.setTotalCreditSales(0.0);
        r.setGrossSales(netSales);
        r.setTotalDiscount(0.0);
        r.setTaxableSales(netSales);
        r.setTotalTax(0.0);
        r.setNetSales(netSales);
        r.setTransactionCount(10);
        r.setExpectedDrawerCash(100.0 + (netSales * 0.4));
        r.setActualDrawerCash(100.0 + (netSales * 0.4));
        r.setCashOverShort(0.0);
        r.setCumulativeGrandTotal(cumulativeGrandTotal);
        r.setClosedByUser("admin");
        return r;
    }
}
