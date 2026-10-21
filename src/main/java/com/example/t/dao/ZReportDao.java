package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.ZReport;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for End-of-Day Financial Z-Reports (financial_z_reports table).
 * Manages consecutive fiscal sequence numbers and non-resettable cumulative audit totals.
 */
public class ZReportDao {

    public void saveZReport(ZReport report) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            saveZReport(report, conn);
        }
    }

    public void saveZReport(ZReport report, Connection conn) throws SQLException {
        if (report == null) {
            throw new IllegalArgumentException("Cannot save null ZReport");
        }

        if (report.getZSequenceNumber() <= 0) {
            report.setZSequenceNumber(getNextSequenceNumber(conn));
        }

        String sql = "INSERT INTO financial_z_reports (" +
                "z_sequence_number, report_timestamp, period_start, period_end, opening_float, " +
                "total_cash_sales, total_card_sales, total_credit_sales, gross_sales, total_discount, " +
                "taxable_sales, total_tax, net_sales, transaction_count, expected_drawer_cash, " +
                "actual_drawer_cash, cash_over_short, cumulative_grand_total, closed_by_user, notes" +
                ") VALUES (?, COALESCE(?, CURRENT_TIMESTAMP), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, report.getZSequenceNumber());
            if (report.getReportTimestamp() != null) {
                pstmt.setTimestamp(2, report.getReportTimestamp());
            } else {
                pstmt.setNull(2, Types.TIMESTAMP);
            }
            pstmt.setTimestamp(3, report.getPeriodStart() != null ? report.getPeriodStart() : new Timestamp(System.currentTimeMillis()));
            pstmt.setTimestamp(4, report.getPeriodEnd() != null ? report.getPeriodEnd() : new Timestamp(System.currentTimeMillis()));
            pstmt.setDouble(5, report.getOpeningFloat());
            pstmt.setDouble(6, report.getTotalCashSales());
            pstmt.setDouble(7, report.getTotalCardSales());
            pstmt.setDouble(8, report.getTotalCreditSales());
            pstmt.setDouble(9, report.getGrossSales());
            pstmt.setDouble(10, report.getTotalDiscount());
            pstmt.setDouble(11, report.getTaxableSales());
            pstmt.setDouble(12, report.getTotalTax());
            pstmt.setDouble(13, report.getNetSales());
            pstmt.setInt(14, report.getTransactionCount());
            pstmt.setDouble(15, report.getExpectedDrawerCash());
            pstmt.setDouble(16, report.getActualDrawerCash());
            pstmt.setDouble(17, report.getCashOverShort());
            pstmt.setDouble(18, report.getCumulativeGrandTotal());
            pstmt.setString(19, report.getClosedByUser() != null ? report.getClosedByUser() : "admin");
            pstmt.setString(20, report.getNotes());

            pstmt.executeUpdate();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    report.setZReportId(generatedKeys.getInt(1));
                }
            }
        }
    }

    public Optional<ZReport> getLatestZReport() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getLatestZReport(conn);
        }
    }

    public Optional<ZReport> getLatestZReport(Connection conn) throws SQLException {
        String sql = "SELECT * FROM financial_z_reports ORDER BY z_sequence_number DESC LIMIT 1;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<ZReport> getZReportBySequence(int sequenceNumber) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getZReportBySequence(sequenceNumber, conn);
        }
    }

    public Optional<ZReport> getZReportBySequence(int sequenceNumber, Connection conn) throws SQLException {
        String sql = "SELECT * FROM financial_z_reports WHERE z_sequence_number = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sequenceNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<ZReport> getAllZReports() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getAllZReports(conn);
        }
    }

    public List<ZReport> getAllZReports(Connection conn) throws SQLException {
        List<ZReport> list = new ArrayList<>();
        String sql = "SELECT * FROM financial_z_reports ORDER BY z_sequence_number DESC;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int getNextSequenceNumber() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getNextSequenceNumber(conn);
        }
    }

    public int getNextSequenceNumber(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(z_sequence_number), 0) + 1 FROM financial_z_reports;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1;
    }

    public double getLatestCumulativeGrandTotal() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getLatestCumulativeGrandTotal(conn);
        }
    }

    public double getLatestCumulativeGrandTotal(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(cumulative_grand_total, 0.0) FROM financial_z_reports ORDER BY z_sequence_number DESC LIMIT 1;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    private ZReport mapRow(ResultSet rs) throws SQLException {
        ZReport r = new ZReport();
        r.setZReportId(rs.getInt("z_report_id"));
        r.setZSequenceNumber(rs.getInt("z_sequence_number"));
        r.setReportTimestamp(rs.getTimestamp("report_timestamp"));
        r.setPeriodStart(rs.getTimestamp("period_start"));
        r.setPeriodEnd(rs.getTimestamp("period_end"));
        r.setOpeningFloat(rs.getDouble("opening_float"));
        r.setTotalCashSales(rs.getDouble("total_cash_sales"));
        r.setTotalCardSales(rs.getDouble("total_card_sales"));
        r.setTotalCreditSales(rs.getDouble("total_credit_sales"));
        r.setGrossSales(rs.getDouble("gross_sales"));
        r.setTotalDiscount(rs.getDouble("total_discount"));
        r.setTaxableSales(rs.getDouble("taxable_sales"));
        r.setTotalTax(rs.getDouble("total_tax"));
        r.setNetSales(rs.getDouble("net_sales"));
        r.setTransactionCount(rs.getInt("transaction_count"));
        r.setExpectedDrawerCash(rs.getDouble("expected_drawer_cash"));
        r.setActualDrawerCash(rs.getDouble("actual_drawer_cash"));
        r.setCashOverShort(rs.getDouble("cash_over_short"));
        r.setCumulativeGrandTotal(rs.getDouble("cumulative_grand_total"));
        r.setClosedByUser(rs.getString("closed_by_user"));
        r.setNotes(rs.getString("notes"));
        return r;
    }
}
