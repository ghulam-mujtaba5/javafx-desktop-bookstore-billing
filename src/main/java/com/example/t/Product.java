package com.example.t;

import javafx.beans.property.*;

public class Product {
    private final IntegerProperty productId;
    private final StringProperty productName;
    private final IntegerProperty quantity;
    private final DoubleProperty price;
    private final DoubleProperty discount;
    private final BooleanProperty status;
    private final DoubleProperty purchasePrice;
    private final DoubleProperty discountedPrice;

    public Product(int productId, String productName, int quantity, double price, double purchasePrice) {
        this.productId = new SimpleIntegerProperty(productId);
        this.productName = new SimpleStringProperty(productName);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.price = new SimpleDoubleProperty(price);
        this.discount = new SimpleDoubleProperty(0.0);
        this.status = new SimpleBooleanProperty(true);
        this.purchasePrice = new SimpleDoubleProperty(purchasePrice);
        this.discountedPrice = new SimpleDoubleProperty(0.0);
    }

    public int getProductId() {
        return productId.get();
    }

    public IntegerProperty productIdProperty() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId.set(productId);
    }

    public String getProductName() {
        return productName.get();
    }

    public StringProperty productNameProperty() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName.set(productName);
    }

    public int getQuantity() {
        return quantity.get();
    }

    public IntegerProperty quantityProperty() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity.set(quantity);
    }

    public double getPrice() {
        return price.get();
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public void setPrice(double price) {
        this.price.set(price);
    }

    public double getDiscount() {
        return discount.get();
    }

    public DoubleProperty discountProperty() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount.set(discount);
    }

    public boolean getStatus() {
        return status.get();
    }

    public BooleanProperty statusProperty() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status.set(status);
    }

    public double getPurchasePrice() {
        return purchasePrice.get();
    }

    public DoubleProperty purchasePriceProperty() {
        return purchasePrice;
    }

    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice.set(purchasePrice);
    }

    public double getDiscountedPrice() {
        return discountedPrice.get();
    }

    public DoubleProperty discountedPriceProperty() {
        return discountedPrice;
    }

    public void setDiscountedPrice(double discountedPrice) {
        this.discountedPrice.set(discountedPrice);
    }

    public void calculateDiscountedPrice() {
        double price = getPrice();
        double discount = getDiscount();
        double calculatedDiscountedPrice = price * (1 - discount / 100);
        setDiscountedPrice(calculatedDiscountedPrice);
    }

    public void updateProduct(int productId, String productName, double price, int quantity, boolean status, double purchasePrice) {
        setProductId(productId);
        setProductName(productName);
        setQuantity(quantity);
        setPrice(price);
        setStatus(status);
        setPurchasePrice(purchasePrice);
    }
}
