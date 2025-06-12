package com.example.t;
import java.time.LocalDate;
import java.util.List;

public class Invoice {
    private int invoiceId;
    private int orderNum;
    private String customerName;
    private LocalDate invoiceDate;
    private List<Product> products;
    private double orderTotal;
    private double discountPercentage;
    private double discount;
    private double netPrice;
    // Removed unused field neprice

    public Invoice(int invoiceId, int orderNum, String customerName, LocalDate invoiceDate,
                   List<Product> products, double orderTotal, double discountPercentage,
                   double discount, double netPrice) {
        this.invoiceId = invoiceId;
        this.orderNum = orderNum;
        this.customerName = customerName;
        this.invoiceDate = invoiceDate;
        this.products = products;
        this.orderTotal = orderTotal;
        this.discountPercentage = discountPercentage;
        this.discount = discount;
        this.netPrice = netPrice;
    }


    public int getInvoiceId() {
        return invoiceId;
    }

    public int getOrderNum() {
        return orderNum;
    }

    public String getCustomerName() {
        return customerName;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public List<Product> getProducts() {
        return products;
    }

    public double getOrderTotal() {
        return orderTotal;
    }

    public double getDiscountPercentage() {
        return discountPercentage;
    }

    public double getDiscount() {
        return discount;
    }

    public double getNetPrice() {
        return netPrice;
    }
}
