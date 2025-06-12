package com.example.t;

import java.time.LocalDate;
import java.util.List;
// ...existing code...

public class Order {
    private int orderId;
    private LocalDate orderDate;
    private String customerName;
    private List<Product> products;
    private double totalAmount;

    public Order(int orderId, LocalDate orderDate, String customerName, List<Product> products) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.customerName = customerName;
        this.products = products;
        this.totalAmount = calculateTotalOrderPrice();
    }

    public int getOrderId() {
        return orderId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public String getCustomerName() {
        return customerName;
    }

    public List<Product> getProducts() {
        return products;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public boolean processOrder(List<Product> stock) {
        boolean orderAccepted = true;
        for (Product p : products) {
            int productId = p.getProductId();
            int orderQuantity = p.getQuantity();
            boolean foundInStock = false;
            for (Product stockProduct : stock) {
                if (stockProduct.getProductId() == productId) {
                    int stockQuantity = stockProduct.getQuantity();
                    if (orderQuantity > stockQuantity) {
                        orderAccepted = false;
                        Logger.warn("Order rejected: not enough stock for " + p.getProductName());
                    } else {
                        stockProduct.setQuantity(stockQuantity - orderQuantity);
                    }
                    foundInStock = true;
                    break;
                }
            }
            if (!foundInStock) {
                orderAccepted = false;
                Logger.warn("Order rejected: product not found in stock for " + p.getProductName());
            }
        }
        return orderAccepted;
    }
    public void calculateTotalAmount() {
        totalAmount = 0.0;
        for (Product product : products) {
            totalAmount += product.getPrice() * product.getQuantity();
        }
    }


    private double calculateTotalOrderPrice() {
        double totalPrice = 0;
        for (Product product : products) {
            totalPrice += product.getPrice() * product.getQuantity();
        }
        return totalPrice;
    }
}
