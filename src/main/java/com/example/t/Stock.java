package com.example.t;

import javafx.collections.ObservableList;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Stock {
    private List<Product> stock;
    private String stockFilePath;

    public Stock() {
        stockFilePath = FilePathManager.getStockFilePath();
        stock = readStockFromFile();
    }

    public void addProduct(Product product) {
        stock.add(product);
        saveStockToFile();
    }

    public List<Product> getStock() {
        return stock;
    }

    public boolean isProductIdExists(int productId) {
        for (Product product : stock) {
            if (product.getProductId() == productId) {
                return true;
            }
        }
        return false;
    }

    public void updateProductQuantity(int productId, int quantity) {
        for (Product product : stock) {
            if (product.getProductId() == productId) {
                product.setQuantity(quantity);
                break;
            }
        }
        saveStockToFile();
    }
    public List<Product> readStockFromFile() {
        List<Product> stockList = new ArrayList<>();

        File file = new File(stockFilePath);

        if (!file.exists()) {
            try {
                // Create a new file if it does not exist
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length != 6) {
                    // Skip lines that don't have the expected format (6 elements)
                    continue;
                }
                try {
                    int id = Integer.parseInt(data[0]);
                    String name = data[1];
                    int quantity = Integer.parseInt(data[2]);
                    double price = Double.parseDouble(data[3]);
                    double purchasePrice = Double.parseDouble(data[4]);
                    boolean status = Boolean.parseBoolean(data[5]);

                    Product product = new Product(id, name, quantity, price, purchasePrice);
                    product.setStatus(status);
                    stockList.add(product);
                } catch (NumberFormatException e) {
                    // Handle the case where data cannot be parsed into numbers
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stockList;
    }
    public void saveStockToFile() {
        try {
            PrintWriter writer = new PrintWriter(stockFilePath);
            for (Product product : stock) {
                writer.println(
                        product.getProductId() + "," +
                                product.getProductName() + "," +
                                product.getQuantity() + "," +
                                product.getPrice() + "," +
                                product.getPurchasePrice() + "," +
                                product.getStatus()
                );
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveStockToFile(ObservableList<Product> productList) {
        try {
            PrintWriter writer = new PrintWriter(stockFilePath);
            for (Product product : productList) {
                writer.println(
                        product.getProductId() + "," +
                                product.getProductName() + "," +
                                product.getQuantity() + "," +
                                product.getPrice() + "," +
                                product.getPurchasePrice() + "," +
                                product.getStatus()
                );
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
