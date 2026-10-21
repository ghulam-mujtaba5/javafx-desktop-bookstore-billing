package com.example.t;

import javafx.beans.property.*;

/**
 * Product data model representing inventory items (both Books and Stationery).
 * Exposes full JavaFX observable properties for TableView and UI data binding.
 */
public class Product {

    private final IntegerProperty productId;
    private final StringProperty productName;
    private final StringProperty category;
    private final StringProperty sku;
    private final StringProperty author;
    private final StringProperty publisher;
    private final StringProperty edition;
    private final DoubleProperty purchasePrice;
    private final DoubleProperty price;
    private final IntegerProperty quantity;
    private final IntegerProperty minStockThreshold;
    private final DoubleProperty discount;
    private final BooleanProperty status;
    private final DoubleProperty discountedPrice;

    /**
     * Default constructor for serialization and reflection.
     */
    public Product() {
        this(0, "", 0, 0.0, 0.0);
    }

    /**
     * Standard 5-argument constructor matching legacy semester project usage.
     */
    public Product(int productId, String productName, int quantity, double price, double purchasePrice) {
        this.productId = new SimpleIntegerProperty(productId);
        this.productName = new SimpleStringProperty(productName != null ? productName : "");
        this.category = new SimpleStringProperty("General");
        this.sku = new SimpleStringProperty(productId > 0 ? "SKU-" + productId : "");
        this.author = new SimpleStringProperty("");
        this.publisher = new SimpleStringProperty("");
        this.edition = new SimpleStringProperty("");
        this.purchasePrice = new SimpleDoubleProperty(purchasePrice);
        this.price = new SimpleDoubleProperty(price);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.minStockThreshold = new SimpleIntegerProperty(5);
        this.discount = new SimpleDoubleProperty(0.0);
        this.status = new SimpleBooleanProperty(true);
        this.discountedPrice = new SimpleDoubleProperty(price);
    }

    /**
     * Full constructor covering Book and Stationery specific attributes.
     */
    public Product(int productId, String productName, String category, String sku,
                   String author, String publisher, String edition,
                   double purchasePrice, double price, int quantity, int minStockThreshold) {
        this.productId = new SimpleIntegerProperty(productId);
        this.productName = new SimpleStringProperty(productName != null ? productName : "");
        this.category = new SimpleStringProperty(category != null ? category : "General");
        this.sku = new SimpleStringProperty(sku != null ? sku : "");
        this.author = new SimpleStringProperty(author != null ? author : "");
        this.publisher = new SimpleStringProperty(publisher != null ? publisher : "");
        this.edition = new SimpleStringProperty(edition != null ? edition : "");
        this.purchasePrice = new SimpleDoubleProperty(purchasePrice);
        this.price = new SimpleDoubleProperty(price);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.minStockThreshold = new SimpleIntegerProperty(minStockThreshold);
        this.discount = new SimpleDoubleProperty(0.0);
        this.status = new SimpleBooleanProperty(true);
        this.discountedPrice = new SimpleDoubleProperty(price);
    }

    // --- Property Accessors, Getters & Setters ---

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
        this.productName.set(productName != null ? productName : "");
    }

    public String getCategory() {
        return category.get();
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public void setCategory(String category) {
        this.category.set(category != null ? category : "");
    }

    public String getSku() {
        return sku.get();
    }

    public StringProperty skuProperty() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku.set(sku != null ? sku : "");
    }

    public String getAuthor() {
        return author.get();
    }

    public StringProperty authorProperty() {
        return author;
    }

    public void setAuthor(String author) {
        this.author.set(author != null ? author : "");
    }

    public String getPublisher() {
        return publisher.get();
    }

    public StringProperty publisherProperty() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher.set(publisher != null ? publisher : "");
    }

    public String getEdition() {
        return edition.get();
    }

    public StringProperty editionProperty() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition.set(edition != null ? edition : "");
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

    public double getPrice() {
        return price.get();
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public void setPrice(double price) {
        this.price.set(price);
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

    public int getMinStockThreshold() {
        return minStockThreshold.get();
    }

    public IntegerProperty minStockThresholdProperty() {
        return minStockThreshold;
    }

    public void setMinStockThreshold(int minStockThreshold) {
        this.minStockThreshold.set(minStockThreshold);
    }

    public double getDiscount() {
        return discount.get();
    }

    public DoubleProperty discountProperty() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount.set(discount);
        calculateDiscountedPrice();
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
        double currentPrice = getPrice();
        double currentDiscount = getDiscount();
        double calculated = currentPrice * (1.0 - (currentDiscount / 100.0));
        setDiscountedPrice(calculated);
    }

    public void updateProduct(int productId, String productName, double price, int quantity, boolean status, double purchasePrice) {
        setProductId(productId);
        setProductName(productName);
        setPrice(price);
        setQuantity(quantity);
        setStatus(status);
        setPurchasePrice(purchasePrice);
    }

    @Override
    public String toString() {
        return getProductName();
    }
}
