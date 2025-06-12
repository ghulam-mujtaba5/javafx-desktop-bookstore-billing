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

        addButton.setOnAction(e -> addProduct());
    }

    private void addProduct() {
        String name = productNameField.getText();
        String quantityText = quantityField.getText();
        String priceText = priceField.getText();
        String purchasePriceText = purchasePriceField.getText();
        boolean status = statusCheckBox.isSelected();

        if (name.isEmpty() || quantityText.isEmpty() || priceText.isEmpty() || purchasePriceText.isEmpty()) {
            messageLabel.setText("Please fill in all fields.");
            return;
        }
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
