package com.example.t;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileHandler {
    private static final String STOCK_FILE_NAME = "stock.txt";
    private static final String STOCK_FILE_PATH = "src/main";
    private static final String INVOICE_FILE_NAME = "invoice.csv";

    static String getStockFilePath() {
        String filePath = STOCK_FILE_PATH + File.separator + STOCK_FILE_NAME;
        File file = new File(filePath);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("Failed to create stock file: " + e.getMessage());
            }
        }

        return filePath;
    }

    static String getInvoiceFilePath() {
        String filePath = STOCK_FILE_PATH + File.separator + INVOICE_FILE_NAME;
        File file = new File(filePath);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("Failed to create invoice file: " + e.getMessage());
            }
        }

        return filePath;
    }

    public static List<Product> loadStock() {
        List<Product> stock = new ArrayList<>();
        String filePath = getStockFilePath();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int productId = Integer.parseInt(parts[0]);
                String productName = parts[1];
                int quantity = Integer.parseInt(parts[2]);
                double price = Double.parseDouble(parts[3]);
                double purchasePrice = Double.parseDouble(parts[4]);
                boolean status = Boolean.parseBoolean(parts[5]);
                Product product = new Product(productId, productName, quantity, price, purchasePrice);
                product.setStatus(status);
                stock.add(product);
            }
        } catch (IOException e) {
            System.out.println("Failed to load stock from file: " + e.getMessage());
        }
        return stock;
    }

    public static void saveStock(List<Product> stock) {
        String filePath = getStockFilePath();
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(filePath));

            for (Product product : stock) {
                writer.println(product.getProductId() + "," + product.getProductName() + ","
                        + product.getQuantity() + "," + product.getPrice() + "," + product.getPurchasePrice() + "," + product.getStatus());
            }

            writer.close();
            System.out.println("Stock saved successfully.");
        } catch (IOException e) {
            System.out.println("Failed to save stock to file: " + e.getMessage());
        }
    }

    public static void saveInvoice(Invoice invoice) {
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
            System.out.println("Failed to save invoice to file: " + e.getMessage());
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
//            System.out.println("Failed to load invoice from file: " + e.getMessage());
//        }
//
//        return invoice;
//    }
}
