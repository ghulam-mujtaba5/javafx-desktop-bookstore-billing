package com.example.t.model;

import java.sql.Timestamp;

/**
 * Persisted payment tender record associated with an invoice in SQLite table invoice_payments.
 */
public class InvoicePayment {
    private int paymentId;
    private int invoiceId;
    private String paymentType;
    private double amount;
    private double tenderedAmount;
    private double changeDue;
    private String referenceInfo;
    private Timestamp createdAt;

    public InvoicePayment() {
    }

    public InvoicePayment(int invoiceId, String paymentType, double amount,
                          double tenderedAmount, double changeDue, String referenceInfo) {
        this.invoiceId = invoiceId;
        this.paymentType = paymentType;
        this.amount = amount;
        this.tenderedAmount = tenderedAmount;
        this.changeDue = changeDue;
        this.referenceInfo = referenceInfo;
    }

    public InvoicePayment(int paymentId, int invoiceId, String paymentType, double amount,
                          double tenderedAmount, double changeDue, String referenceInfo, Timestamp createdAt) {
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.paymentType = paymentType;
        this.amount = amount;
        this.tenderedAmount = tenderedAmount;
        this.changeDue = changeDue;
        this.referenceInfo = referenceInfo;
        this.createdAt = createdAt;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public int getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(int invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getTenderedAmount() {
        return tenderedAmount;
    }

    public void setTenderedAmount(double tenderedAmount) {
        this.tenderedAmount = tenderedAmount;
    }

    public double getChangeDue() {
        return changeDue;
    }

    public void setChangeDue(double changeDue) {
        this.changeDue = changeDue;
    }

    public String getReferenceInfo() {
        return referenceInfo;
    }

    public void setReferenceInfo(String referenceInfo) {
        this.referenceInfo = referenceInfo;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public PaymentTender toPaymentTender() {
        return new PaymentTender(paymentType, amount, tenderedAmount, changeDue, referenceInfo);
    }

    @Override
    public String toString() {
        return "InvoicePayment{" +
                "paymentId=" + paymentId +
                ", invoiceId=" + invoiceId +
                ", paymentType='" + paymentType + '\'' +
                ", amount=" + amount +
                ", tendered=" + tenderedAmount +
                ", change=" + changeDue +
                '}';
    }
}
