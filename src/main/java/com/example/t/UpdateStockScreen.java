package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.List;

public class UpdateStockScreen {
    @FXML private TableView<Product> stockTable;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, Integer> colQuantity;
    @FXML private TableColumn<Product, Double> colPrice;
    @FXML private TableColumn<Product, Double> colPurchasePrice;
    @FXML private TableColumn<Product, Boolean> colStatus;
    @FXML private TextField quantityField;
    @FXML private Button updateButton;
    @FXML private Label messageLabel;

    private ObservableList<Product> data;
    private Product selectedProduct;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colPurchasePrice.setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        Stock stock = new Stock();
        List<Product> stockList = stock.readStockFromFile();
        data = FXCollections.observableArrayList(stockList);
        stockTable.setItems(data);
        stockTable.setPlaceholder(new Label("No products in stock. Add new stock to get started!"));

        stockTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedProduct = newSelection;
                quantityField.setText(String.valueOf(selectedProduct.getQuantity()));
            }
        });

        updateButton.setOnAction(e -> updateQuantity());
    }

    private void updateQuantity() {
        if (selectedProduct == null) {
            messageLabel.setText("No product selected.");
            return;
        }
        try {
            int newQuantity = Integer.parseInt(quantityField.getText());
            selectedProduct.setQuantity(newQuantity);
            Stock stock = new Stock();
            stock.saveStockToFile(data);
            messageLabel.setText("Quantity updated.");
            stockTable.refresh();
        } catch (NumberFormatException ex) {
            messageLabel.setText("Invalid quantity.");
        }
    }
}