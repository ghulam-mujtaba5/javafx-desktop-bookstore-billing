package com.example.t.model;

import java.sql.Timestamp;

/**
 * End-of-day financial Z-Report and fiscal closure record persisted in financial_z_reports.
 */
public class ZReport {
    private int zReportId;
    private int zSequenceNumber;
    private Timestamp reportTimestamp;
    private Timestamp periodStart;
    private Timestamp periodEnd;
    private double openingFloat;
    private double totalCashSales;
    private double totalCardSales;
    private double totalCreditSales;
    private double grossSales;
    private double totalDiscount;
    private double taxableSales;
    private double totalTax;
    private double netSales;
    private int transactionCount;
    private double expectedDrawerCash;
    private double actualDrawerCash;
    private double cashOverShort;
    private double cumulativeGrandTotal;
    private String closedByUser;
    private String notes;

    public ZReport() {
        this.closedByUser = "admin";
    }

    public ZReport(int zSequenceNumber, Timestamp periodStart, Timestamp periodEnd,
                   double openingFloat, double totalCashSales, double totalCardSales,
                   double totalCreditSales, double grossSales, double totalDiscount,
                   double taxableSales, double totalTax, double netSales,
                   int transactionCount, double expectedDrawerCash, double actualDrawerCash,
                   double cashOverShort, double cumulativeGrandTotal,
                   String closedByUser, String notes) {
        this.zSequenceNumber = zSequenceNumber;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.openingFloat = openingFloat;
        this.totalCashSales = totalCashSales;
        this.totalCardSales = totalCardSales;
        this.totalCreditSales = totalCreditSales;
        this.grossSales = grossSales;
        this.totalDiscount = totalDiscount;
        this.taxableSales = taxableSales;
        this.totalTax = totalTax;
        this.netSales = netSales;
        this.transactionCount = transactionCount;
        this.expectedDrawerCash = expectedDrawerCash;
        this.actualDrawerCash = actualDrawerCash;
        this.cashOverShort = cashOverShort;
        this.cumulativeGrandTotal = cumulativeGrandTotal;
        this.closedByUser = closedByUser != null ? closedByUser : "admin";
        this.notes = notes;
    }

    public int getZReportId() {
        return zReportId;
    }

    public void setZReportId(int zReportId) {
        this.zReportId = zReportId;
    }

    public int getZSequenceNumber() {
        return zSequenceNumber;
    }

    public void setZSequenceNumber(int zSequenceNumber) {
        this.zSequenceNumber = zSequenceNumber;
    }

    public Timestamp getReportTimestamp() {
        return reportTimestamp;
    }

    public void setReportTimestamp(Timestamp reportTimestamp) {
        this.reportTimestamp = reportTimestamp;
    }

    public Timestamp getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Timestamp periodStart) {
        this.periodStart = periodStart;
    }

    public Timestamp getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Timestamp periodEnd) {
        this.periodEnd = periodEnd;
    }

    public double getOpeningFloat() {
        return openingFloat;
    }

    public void setOpeningFloat(double openingFloat) {
        this.openingFloat = openingFloat;
    }

    public double getTotalCashSales() {
        return totalCashSales;
    }

    public void setTotalCashSales(double totalCashSales) {
        this.totalCashSales = totalCashSales;
    }

    public double getTotalCardSales() {
        return totalCardSales;
    }

    public void setTotalCardSales(double totalCardSales) {
        this.totalCardSales = totalCardSales;
    }

    public double getTotalCreditSales() {
        return totalCreditSales;
    }

    public void setTotalCreditSales(double totalCreditSales) {
        this.totalCreditSales = totalCreditSales;
    }

    public double getGrossSales() {
        return grossSales;
    }

    public void setGrossSales(double grossSales) {
        this.grossSales = grossSales;
    }

    public double getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(double totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public double getTaxableSales() {
        return taxableSales;
    }

    public void setTaxableSales(double taxableSales) {
        this.taxableSales = taxableSales;
    }

    public double getTotalTax() {
        return totalTax;
    }

    public void setTotalTax(double totalTax) {
        this.totalTax = totalTax;
    }

    public double getNetSales() {
        return netSales;
    }

    public void setNetSales(double netSales) {
        this.netSales = netSales;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public double getExpectedDrawerCash() {
        return expectedDrawerCash;
    }

    public void setExpectedDrawerCash(double expectedDrawerCash) {
        this.expectedDrawerCash = expectedDrawerCash;
    }

    public double getActualDrawerCash() {
        return actualDrawerCash;
    }

    public void setActualDrawerCash(double actualDrawerCash) {
        this.actualDrawerCash = actualDrawerCash;
    }

    public double getCashOverShort() {
        return cashOverShort;
    }

    public void setCashOverShort(double cashOverShort) {
        this.cashOverShort = cashOverShort;
    }

    public double getCumulativeGrandTotal() {
        return cumulativeGrandTotal;
    }

    public void setCumulativeGrandTotal(double cumulativeGrandTotal) {
        this.cumulativeGrandTotal = cumulativeGrandTotal;
    }

    public String getClosedByUser() {
        return closedByUser;
    }

    public void setClosedByUser(String closedByUser) {
        this.closedByUser = closedByUser;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "ZReport{" +
                "zReportId=" + zReportId +
                ", zSequenceNumber=" + zSequenceNumber +
                ", reportTimestamp=" + reportTimestamp +
                ", netSales=" + netSales +
                ", transactionCount=" + transactionCount +
                ", cumulativeGrandTotal=" + cumulativeGrandTotal +
                '}';
    }
}
