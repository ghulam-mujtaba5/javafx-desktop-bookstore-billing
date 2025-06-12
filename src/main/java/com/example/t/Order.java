package com.example.t;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Order {
    // Add a static method to read orders from file (stub for now)
    public static ObservableList<Order> readOrdersFromFile() {
        // TODO: Implement actual file reading logic
        // For now, return mock data for testing
        ObservableList<Order> orders = FXCollections.observableArrayList();
        LocalDate today = LocalDate.now();
        
        // Add some mock orders
        List<Product> products1 = new ArrayList<>();
        products1.add(new Product(1, "Java Programming", 2, 29.99, 20.00));
        products1.add(new Product(2, "Python Basics", 1, 24.99, 15.00));
        orders.add(new Order(1, today, "John Doe", products1));

        List<Product> products2 = new ArrayList<>();
        products2.add(new Product(3, "Web Development", 3, 39.99, 25.00));
        orders.add(new Order(2, today.minusDays(1), "Jane Smith", products2));

        return orders;
    }

    private final IntegerProperty orderId;
    private final ObjectProperty<LocalDate> orderDate;
    private final StringProperty customerName;
    private final ListProperty<Product> products;
    private final DoubleProperty totalAmount;

    public Order(int orderId, LocalDate orderDate, String customerName, List<Product> products) {
        this.orderId = new SimpleIntegerProperty(orderId);
        this.orderDate = new SimpleObjectProperty<>(orderDate);
        this.customerName = new SimpleStringProperty(customerName);
        this.products = new SimpleListProperty<>(FXCollections.observableArrayList(products));
        this.totalAmount = new SimpleDoubleProperty(calculateTotalOrderPrice());
    }

    public int getOrderId() {
        return orderId.get();
    }

    public IntegerProperty orderIdProperty() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId.set(orderId);
    }

    public LocalDate getOrderDate() {
        return orderDate.get();
    }

    public ObjectProperty<LocalDate> orderDateProperty() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate.set(orderDate);
    }

    public String getCustomerName() {
        return customerName.get();
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName.set(customerName);
    }

    public ObservableList<Product> getProducts() {
        return products.get();
    }

    public ListProperty<Product> productsProperty() {
        return products;
    }

    public void setProducts(ObservableList<Product> products) {
        this.products.set(products);
    }

    public double getTotalAmount() {
        return totalAmount.get();
    }

    public DoubleProperty totalAmountProperty() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount.set(totalAmount);
    }

    public boolean processOrder(List<Product> stock) {
        boolean orderAccepted = true;
        for (Product p : getProducts()) {
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
        double total = 0.0;
        for (Product product : getProducts()) {
            total += product.getPrice() * product.getQuantity();
        }
        setTotalAmount(total);
    }

    private double calculateTotalOrderPrice() {
        double totalPrice = 0;
        for (Product product : getProducts()) {
            totalPrice += product.getPrice() * product.getQuantity();
        }
        return totalPrice;
    }
}
