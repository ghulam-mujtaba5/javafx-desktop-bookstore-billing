package com.example.t;

import javafx.beans.property.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice model representing historical and newly generated sales transactions.
 * Exposes JavaFX observable properties for TableView bindings and reporting.
 */
public class Invoice {

    private final IntegerProperty invoiceId;
    private final IntegerProperty orderNum;
    private final StringProperty customerName;
    private final ObjectProperty<LocalDate> invoiceDate;
    private List<Product> products;
    private final DoubleProperty orderTotal;
    private final DoubleProperty discountPercentage;
    private final DoubleProperty discount;
    private final DoubleProperty netPrice;

    public Invoice() {
        this(0, 0, "Walk-in Customer", LocalDate.now(), new ArrayList<>(), 0.0, 0.0, 0.0, 0.0);
    }

    public Invoice(int invoiceId, int orderNum, String customerName, LocalDate invoiceDate,
                   List<Product> products, double orderTotal, double discountPercentage,
                   double discount, double netPrice) {
        this.invoiceId = new SimpleIntegerProperty(invoiceId);
        this.orderNum = new SimpleIntegerProperty(orderNum);
        this.customerName = new SimpleStringProperty(customerName != null ? customerName : "Walk-in Customer");
        this.invoiceDate = new SimpleObjectProperty<>(invoiceDate != null ? invoiceDate : LocalDate.now());
        this.products = products != null ? products : new ArrayList<>();
        this.orderTotal = new SimpleDoubleProperty(orderTotal);
        this.discountPercentage = new SimpleDoubleProperty(discountPercentage);
        this.discount = new SimpleDoubleProperty(discount);
        this.netPrice = new SimpleDoubleProperty(netPrice);
    }

    // --- Property Accessors ---

    public int getInvoiceId() {
        return invoiceId.get();
    }

    public IntegerProperty invoiceIdProperty() {
        return invoiceId;
    }

    public void setInvoiceId(int invoiceId) {
        this.invoiceId.set(invoiceId);
    }

    public int getOrderNum() {
        return orderNum.get();
    }

    public IntegerProperty orderNumProperty() {
        return orderNum;
    }

    public void setOrderNum(int orderNum) {
        this.orderNum.set(orderNum);
    }

    public String getCustomerName() {
        return customerName.get();
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName.set(customerName != null ? customerName : "");
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate.get();
    }

    public ObjectProperty<LocalDate> invoiceDateProperty() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate.set(invoiceDate != null ? invoiceDate : LocalDate.now());
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products != null ? products : new ArrayList<>();
    }

    public double getOrderTotal() {
        return orderTotal.get();
    }

    public DoubleProperty orderTotalProperty() {
        return orderTotal;
    }

    public void setOrderTotal(double orderTotal) {
        this.orderTotal.set(orderTotal);
    }

    public double getDiscountPercentage() {
        return discountPercentage.get();
    }

    public DoubleProperty discountPercentageProperty() {
        return discountPercentage;
    }

    public void setDiscountPercentage(double discountPercentage) {
        this.discountPercentage.set(discountPercentage);
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

    public double getNetPrice() {
        return netPrice.get();
    }

    public DoubleProperty netPriceProperty() {
        return netPrice;
    }

    public void setNetPrice(double netPrice) {
        this.netPrice.set(netPrice);
    }

    /**
     * Reads legacy invoice records from disk (invoice_history.txt).
     * Returns an empty list safely if the file does not exist.
     */
    public static List<Invoice> readInvoicesFromFile() {
        List<Invoice> invoiceList = new ArrayList<>();

        // Potential paths for legacy invoice file
        String[] candidatePaths = new String[]{
                FilePathManager.getInvoiceHistoryFilePath(),
                "src/main/invoice_history.txt",
                "invoice_history.txt"
        };

        File invoiceFile = null;
        for (String path : candidatePaths) {
            if (path != null) {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    invoiceFile = f;
                    break;
                }
            }
        }

        if (invoiceFile == null) {
            return invoiceList;
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try (BufferedReader reader = new BufferedReader(new FileReader(invoiceFile))) {
            String line;
            int invoiceId = 0;
            LocalDate invoiceDate = LocalDate.now();
            String customerName = "Walk-in Customer";
            double totalAmount = 0.0;
            List<Product> items = new ArrayList<>();
            boolean inDetails = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    if (invoiceId > 0 || !items.isEmpty()) {
                        Invoice inv = new Invoice(invoiceId, invoiceId, customerName, invoiceDate,
                                new ArrayList<>(items), totalAmount, 0.0, 0.0, totalAmount);
                        invoiceList.add(inv);
                        invoiceId = 0;
                        customerName = "Walk-in Customer";
                        totalAmount = 0.0;
                        items.clear();
                        inDetails = false;
                    }
                    continue;
                }

                if (line.startsWith("Invoice ID:")) {
                    try {
                        invoiceId = Integer.parseInt(line.replace("Invoice ID:", "").trim());
                    } catch (NumberFormatException ignored) {}
                } else if (line.startsWith("Order Date:")) {
                    try {
                        String dateStr = line.replace("Order Date:", "").trim();
                        invoiceDate = LocalDate.parse(dateStr, dateFormatter);
                    } catch (Exception ignored) {}
                } else if (line.startsWith("Customer Name:")) {
                    customerName = line.replace("Customer Name:", "").trim();
                } else if (line.startsWith("Total Amount:")) {
                    try {
                        String raw = line.replace("Total Amount:", "").replace("Rs", "").replace("$", "").trim();
                        totalAmount = Double.parseDouble(raw);
                    } catch (NumberFormatException ignored) {}
                } else if (line.startsWith("Order Details:") || line.startsWith("Product Name\t")) {
                    inDetails = true;
                } else if (inDetails) {
                    // Item row format: Name \t Quantity \t Price \t Discount
                    String[] tokens = line.split("\t+");
                    if (tokens.length >= 3) {
                        try {
                            String name = tokens[0].trim();
                            int qty = Integer.parseInt(tokens[1].trim());
                            double price = Double.parseDouble(tokens[2].trim());
                            double disc = tokens.length > 3 ? Double.parseDouble(tokens[3].trim()) : 0.0;
                            Product p = new Product(0, name, qty, price, price);
                            p.setDiscount(disc);
                            items.add(p);
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Flush trailing record if file ended without trailing newline
            if (invoiceId > 0 || !items.isEmpty()) {
                Invoice inv = new Invoice(invoiceId, invoiceId, customerName, invoiceDate,
                        items, totalAmount, 0.0, 0.0, totalAmount);
                invoiceList.add(inv);
            }
        } catch (IOException e) {
            Logger.warn("Failed to read invoices from file: " + e.getMessage());
        }

        return invoiceList;
    }
}
