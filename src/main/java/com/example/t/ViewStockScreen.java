package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
// import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
// import javafx.scene.image.Image;
// import javafx.scene.layout.*;
// import javafx.stage.Screen;
// import javafx.stage.Stage;
// import javafx.geometry.Pos;

// import java.util.Comparator;
import java.util.List;
// import java.util.function.Predicate;

import javafx.fxml.FXML;

public class ViewStockScreen {
    @FXML private TableView<Product> stockTable;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, Integer> colQuantity;
    @FXML private TableColumn<Product, Double> colPrice;
    @FXML private TableColumn<Product, Double> colPurchasePrice;
    @FXML private TableColumn<Product, Boolean> colStatus;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;
    @FXML private Label messageLabel;

    private ObservableList<Product> data;

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
        stockTable.setPlaceholder(new Label("No products available"));

        refreshButton.setOnAction(e -> refreshStock());
        exportButton.setOnAction(e -> exportStockToCSV());
    }

    private void refreshStock() {
        Stock stock = new Stock();
        List<Product> stockList = stock.readStockFromFile();
        data.setAll(stockList);
        messageLabel.setText("Stock refreshed");
    }

    private void exportStockToCSV() {
        // TODO: Implement export logic
        messageLabel.setText("Exported to CSV (not implemented)");
    }
}
