package com.example.t;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class Invoice {
    private final SimpleIntegerProperty invoiceId;
    private final SimpleIntegerProperty orderNum;
    private final SimpleStringProperty customerName;
    private final SimpleObjectProperty<LocalDate> invoiceDate;
    private final SimpleListProperty<Product> products;
    private final SimpleDoubleProperty orderTotal;
    private final SimpleDoubleProperty discountPercentage;
    private final SimpleDoubleProperty discount;
    private final SimpleDoubleProperty netPrice;

    public Invoice(int invoiceId, int orderNum, String customerName, LocalDate invoiceDate,
                   List<Product> products, double orderTotal, double discountPercentage,
                   double discount, double netPrice) {
        this.invoiceId = new SimpleIntegerProperty(invoiceId);
        this.orderNum = new SimpleIntegerProperty(orderNum);
        this.customerName = new SimpleStringProperty(customerName);
        this.invoiceDate = new SimpleObjectProperty<>(invoiceDate);
        this.products = new SimpleListProperty<>(FXCollections.observableArrayList(products));
        this.orderTotal = new SimpleDoubleProperty(orderTotal);
        this.discountPercentage = new SimpleDoubleProperty(discountPercentage);
        this.discount = new SimpleDoubleProperty(discount);
        this.netPrice = new SimpleDoubleProperty(netPrice);
    }

    // Property accessors
    public IntegerProperty invoiceIdProperty() {
        return invoiceId;
    }

    public IntegerProperty orderNumProperty() {
        return orderNum;
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public ObjectProperty<LocalDate> invoiceDateProperty() {
        return invoiceDate;
    }

    public ListProperty<Product> productsProperty() {
        return products;
    }

    public DoubleProperty orderTotalProperty() {
        return orderTotal;
    }

    public DoubleProperty discountPercentageProperty() {
        return discountPercentage;
    }

    public DoubleProperty discountProperty() {
        return discount;
    }

    public DoubleProperty netPriceProperty() {
        return netPrice;
    }

    // Getter methods
    public int getInvoiceId() {
        return invoiceId.get();
    }

    public int getOrderNum() {
        return orderNum.get();
    }

    public String getCustomerName() {
        return customerName.get();
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate.get();
    }

    public ObservableList<Product> getProducts() {
        return products.get();
    }

    public double getOrderTotal() {
        return orderTotal.get();
    }

    public double getDiscountPercentage() {
        return discountPercentage.get();
    }

    public double getDiscount() {
        return discount.get();
    }

    public double getNetPrice() {
        return netPrice.get();
    }

    // Setter methods
    public void setInvoiceId(int value) {
        invoiceId.set(value);
    }

    public void setOrderNum(int value) {
        orderNum.set(value);
    }

    public void setCustomerName(String value) {
        customerName.set(value);
    }

    public void setInvoiceDate(LocalDate value) {
        invoiceDate.set(value);
    }

    public void setProducts(List<Product> value) {
        products.setAll(value);
    }

    public void setOrderTotal(double value) {
        orderTotal.set(value);
    }

    public void setDiscountPercentage(double value) {
        discountPercentage.set(value);
    }

    public void setDiscount(double value) {
        discount.set(value);
    }

    public void setNetPrice(double value) {
        netPrice.set(value);
    }

    // Static method to read invoices from file
    public static List<Invoice> readInvoicesFromFile() {
        // TODO: Implement actual file reading logic
        List<Invoice> mockData = new ArrayList<>();
        mockData.add(new Invoice(1, 1001, "John Doe", LocalDate.now(),
            new ArrayList<>(), 299.99, 0.0, 0.0, 299.99));
        mockData.add(new Invoice(2, 1002, "Jane Smith", LocalDate.now().minusDays(1),
            new ArrayList<>(), 499.99, 0.0, 0.0, 499.99));
        return mockData;
    }
}
