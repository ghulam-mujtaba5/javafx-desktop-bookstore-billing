package com.example.t.model;

/**
 * Represents a single tender applied toward an invoice total in a split or single payment transaction.
 */
public class PaymentTender {
    private PaymentType paymentType;
    private double amount;
    private double tenderedAmount;
    private double changeDue;
    private String referenceInfo;

    public PaymentTender() {
        this.paymentType = PaymentType.CASH;
    }

    public PaymentTender(PaymentType paymentType, double amount) {
        this.paymentType = paymentType;
        this.amount = amount;
        this.tenderedAmount = amount;
        this.changeDue = 0.0;
        this.referenceInfo = null;
    }

    public PaymentTender(PaymentType paymentType, double amount, double tenderedAmount, double changeDue) {
        this.paymentType = paymentType;
        this.amount = amount;
        this.tenderedAmount = tenderedAmount;
        this.changeDue = changeDue;
        this.referenceInfo = null;
    }

    public PaymentTender(PaymentType paymentType, double amount, double tenderedAmount, double changeDue, String referenceInfo) {
        this.paymentType = paymentType;
        this.amount = amount;
        this.tenderedAmount = tenderedAmount;
        this.changeDue = changeDue;
        this.referenceInfo = referenceInfo;
    }

    public PaymentTender(String paymentType, double amount, double tenderedAmount, double changeDue, String referenceInfo) {
        this(PaymentType.fromString(paymentType), amount, tenderedAmount, changeDue, referenceInfo);
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
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

    @Override
    public String toString() {
        return "PaymentTender{" +
                "type=" + paymentType +
                ", amount=" + amount +
                ", tendered=" + tenderedAmount +
                ", change=" + changeDue +
                (referenceInfo != null ? ", ref='" + referenceInfo + '\'' : "") +
                '}';
    }
}
