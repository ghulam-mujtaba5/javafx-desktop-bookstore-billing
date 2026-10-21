package com.example.t;

import com.example.t.dao.ProductDao;
import com.example.t.database.DatabaseManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * FileHandler adapted to resolve files under AppData and optionally synchronize with DatabaseManager.
 */
public class FileHandler {

    static String getStockFilePath() {
        return FilePathManager.getStockFilePath();
    }

    static String getInvoiceFilePath() {
        Path invFile = DatabaseManager.getDataDirectory().resolve("invoice.csv");
        if (!Files.exists(invFile)) {
            try {
                if (invFile.getParent() != null && !Files.exists(invFile.getParent())) {
                    Files.createDirectories(invFile.getParent());
                }
                Files.createFile(invFile);
            } catch (IOException e) {
                Logger.warn("Failed to create invoice file: " + e.getMessage());
            }
        }
        return invFile.toAbsolutePath().toString();
    }

    public static List<Product> loadStock() {
        // First try loading from DatabaseManager/ProductDao
        try {
            ProductDao productDao = new ProductDao();
            List<Product> products = productDao.getAllProducts();
            if (!products.isEmpty()) {
                return products;
            }
        } catch (SQLException ignored) {}

        // Fallback to reading flat file
        List<Product> stock = new ArrayList<>();
        String filePath = getStockFilePath();
        File file = new File(filePath);
        if (!file.exists()) {
            return stock;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    try {
                        int productId = Integer.parseInt(parts[0].trim());
                        String productName = parts[1].trim();
                        int quantity = Integer.parseInt(parts[2].trim());
                        double price = Double.parseDouble(parts[3].trim());
                        double purchasePrice = Double.parseDouble(parts[4].trim());
                        boolean status = parts.length > 5 ? Boolean.parseBoolean(parts[5].trim()) : true;
                        Product product = new Product(productId, productName, quantity, price, purchasePrice);
                        product.setStatus(status);
                        stock.add(product);
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            Logger.warn("Failed to load stock from file: " + e.getMessage());
        }
        return stock;
    }

    public static void saveStock(List<Product> stock) {
        String filePath = getStockFilePath();
        try {
            File f = new File(filePath);
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(f))) {
                for (Product product : stock) {
                    writer.println(product.getProductId() + "," + product.getProductName() + ","
                            + product.getQuantity() + "," + product.getPrice() + "," + product.getPurchasePrice() + "," + product.getStatus());
                }
            }
            Logger.info("Stock saved successfully to " + filePath);
        } catch (IOException e) {
            Logger.warn("Failed to save stock to file: " + e.getMessage());
        }
    }

    public static void saveInvoice(Invoice invoice) {
        String filePath = getInvoiceFilePath();
        try {
            File f = new File(filePath);
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(f, true))) {
                writer.println(invoice.getInvoiceId() + "," + invoice.getOrderNum() + ","
                        + invoice.getCustomerName() + "," + invoice.getInvoiceDate() + ","
                        + invoice.getOrderTotal() + "," + invoice.getDiscountPercentage() + ","
                        + invoice.getDiscount() + "," + invoice.getNetPrice());

                if (invoice.getProducts() != null) {
                    for (Product product : invoice.getProducts()) {
                        writer.println(product.getProductId() + "," + product.getProductName() + ","
                                + product.getQuantity() + "," + product.getPrice());
                    }
                }
            }
        } catch (IOException e) {
            Logger.warn("Failed to save invoice to file: " + e.getMessage());
        }
    }
}
