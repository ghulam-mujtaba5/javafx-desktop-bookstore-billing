package com.example.t;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileHandler {
    private static final String STOCK_FILE_NAME = "stock.txt";
    private static final String STOCK_FILE_PATH = "src/main";
    private static final String INVOICE_FILE_NAME = "invoice.csv";
    
    private static FileHandler instance;
    private List<Product> stock;
    private List<Invoice> invoices;
    
    private FileHandler() {
        // Private constructor to enforce singleton pattern
        stock = new ArrayList<>();
        invoices = new ArrayList<>();
    }
    
    public static FileHandler getInstance() {
        if (instance == null) {
            instance = new FileHandler();
        }
        return instance;
    }

    public String getStockFilePath() {
        String filePath = STOCK_FILE_PATH + File.separator + STOCK_FILE_NAME;
        File file = new File(filePath);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Logger.error("Failed to create stock file: " + e.getMessage());
            }
        }

        return filePath;
    }

    public String getInvoiceFilePath() {
        String filePath = STOCK_FILE_PATH + File.separator + INVOICE_FILE_NAME;
        File file = new File(filePath);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Logger.error("Failed to create invoice file: " + e.getMessage());
            }
        }

        return filePath;
    }

    public List<Product> loadStock() {
        stock = new ArrayList<>();
        String filePath = getStockFilePath();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    int id = Integer.parseInt(parts[0].trim());
                    String name = parts[1].trim();
                    int quantity = Integer.parseInt(parts[2].trim());
                    double price = Double.parseDouble(parts[3].trim());
                    double purchasePrice = Double.parseDouble(parts[4].trim());
                    stock.add(new Product(id, name, quantity, price, purchasePrice));
                }
            }
        } catch (IOException | NumberFormatException e) {
            Logger.error("Error loading stock: " + e.getMessage());
        }

        return stock;
    }

    public void saveStock(List<Product> stock) {
        String filePath = getStockFilePath();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Product product : stock) {
                writer.write(String.format("%d,%s,%d,%.2f,%.2f%n", 
                    product.getProductId(), 
                    product.getProductName(), 
                    product.getQuantity(), 
                    product.getPrice(),
                    product.getPurchasePrice()));
            }
        } catch (IOException e) {
            Logger.error("Error saving stock: " + e.getMessage());
        }
    }
    
    public void saveAllChanges() {
        if (stock != null) {
            saveStock(stock);
        }
    }

    public void saveInvoice(Invoice invoice) {
        String filePath = getInvoiceFilePath();
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(filePath, true));

            writer.println(invoice.getInvoiceId() + "," + invoice.getOrderNum() + ","
                    + invoice.getCustomerName() + "," + invoice.getInvoiceDate() + ","
                    + invoice.getOrderTotal() + "," + invoice.getDiscountPercentage() + ","
                    + invoice.getDiscount() + "," + invoice.getNetPrice());

            for (Product product : invoice.getProducts()) {
                writer.println(product.getProductId() + "," + product.getProductName() + ","
                        + product.getQuantity() + "," + product.getPrice());
            }

            writer.close();
        } catch (IOException e) {
            Logger.error("Failed to save invoice to file: " + e.getMessage());
        }
    }

//    public static Invoice loadInvoice() {
//        Invoice invoice = null;
//        String filePath = getInvoiceFilePath();
//        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
//            String line = reader.readLine();
//            String[] parts = line.split(",");
//            int invoiceId = Integer.parseInt(parts[0]);
//            int orderNum = Integer.parseInt(parts[1]);
//            String customerName = parts[2];
//            LocalDate invoiceDate = LocalDate.parses(parts[3]);
//            double orderTotal = Double.parseDouble(parts[4]);
//            double discountPercentage = Double.parseDouble(parts[5]);
//            double discount = Double.parseDouble(parts[6]);
//            double netPrice = Double.parseDouble(parts[7]);
//
//            List<Product> products = new ArrayList<>();
//            while ((line = reader.readLine()) != null) {
//                parts = line.split(",");
//                int productId = Integer.parseInt(parts[0]);
//                String productName = parts[1];
//                int quantity = Integer.parseInt(parts[2]);
//                double price = Double.parseDouble(parts[3]);
//                Product product = new Product(productId, productName, quantity, price);
//                products.add(product);
//            }
//
//            invoice = new Invoice(invoiceId, orderNum, customerName, invoiceDate, products, orderTotal, discountPercentage, discount, netPrice);
//
//        } catch (IOException e) {
//            Logger.error("Failed to load invoice from file: " + e.getMessage());
//        }
//
//        return invoice;
//    }
}
