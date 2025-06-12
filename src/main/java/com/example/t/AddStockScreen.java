package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
// import javafx.scene.control.cell.PropertyValueFactory;
import java.util.List;

public class AddStockScreen {
    @FXML private TextField productNameField;
    @FXML private TextField quantityField;
    @FXML private TextField priceField;
    @FXML private TextField purchasePriceField;
    @FXML private CheckBox statusCheckBox;
    @FXML private Button addButton;
    @FXML private Label messageLabel;

    private ObservableList<Product> data;

    @FXML
    public void initialize() {
        Stock stock = new Stock();
        List<Product> stockList = stock.readStockFromFile();
        data = FXCollections.observableArrayList(stockList);

        // Real-time validation listeners
        addValidationListeners();
        addButton.setOnAction(e -> addProduct());
    }

    private void addValidationListeners() {
        productNameField.textProperty().addListener((obs, oldVal, newVal) -> validateFields());
        quantityField.textProperty().addListener((obs, oldVal, newVal) -> validateFields());
        priceField.textProperty().addListener((obs, oldVal, newVal) -> validateFields());
        purchasePriceField.textProperty().addListener((obs, oldVal, newVal) -> validateFields());
    }

    private boolean validateFields() {
        boolean valid = true;
        StringBuilder error = new StringBuilder();
        if (productNameField.getText().trim().isEmpty()) {
            productNameField.setStyle("-fx-border-color: #e53935;");
            valid = false;
            error.append("Product name required. ");
        } else {
            productNameField.setStyle("");
        }
        if (quantityField.getText().trim().isEmpty() || !quantityField.getText().matches("\\d+")) {
            quantityField.setStyle("-fx-border-color: #e53935;");
            valid = false;
            error.append("Quantity must be a positive integer. ");
        } else {
            quantityField.setStyle("");
        }
        if (priceField.getText().trim().isEmpty() || !priceField.getText().matches("\\d+(\\.\\d{1,2})?")) {
            priceField.setStyle("-fx-border-color: #e53935;");
            valid = false;
            error.append("Price must be a valid number. ");
        } else {
            priceField.setStyle("");
        }
        if (purchasePriceField.getText().trim().isEmpty() || !purchasePriceField.getText().matches("\\d+(\\.\\d{1,2})?")) {
            purchasePriceField.setStyle("-fx-border-color: #e53935;");
            valid = false;
            error.append("Purchase price must be a valid number. ");
        } else {
            purchasePriceField.setStyle("");
        }
        if (!valid) {
            messageLabel.setText(error.toString());
        } else {
            messageLabel.setText("");
        }
        return valid;
    }

    private void addProduct() {
        if (!validateFields()) {
            return;
        }
        String name = productNameField.getText();
        String quantityText = quantityField.getText();
        String priceText = priceField.getText();
        String purchasePriceText = purchasePriceField.getText();
        boolean status = statusCheckBox.isSelected();
        try {
            int quantity = Integer.parseInt(quantityText);
            double price = Double.parseDouble(priceText);
            double purchasePrice = Double.parseDouble(purchasePriceText);
            Product product = new Product(0, name, quantity, price, purchasePrice); // ID is set to 0, adjust as needed
            product.setStatus(status);
            data.add(product);
            Stock stock = new Stock();
            stock.saveStockToFile(data);
            messageLabel.setText("Product added successfully.");
            productNameField.clear();
            quantityField.clear();
            priceField.clear();
            purchasePriceField.clear();
            statusCheckBox.setSelected(false);
        } catch (NumberFormatException ex) {
            messageLabel.setText("Invalid input.");
        }
    }
}
