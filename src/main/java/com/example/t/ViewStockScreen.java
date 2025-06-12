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
    @FXML private TableColumn<Product, Integer> bookIdColumn;
    @FXML private TableColumn<Product, String> bookNameColumn;
    @FXML private TableColumn<Product, String> authorColumn;
    @FXML private TableColumn<Product, String> publisherColumn;
    @FXML private TableColumn<Product, String> editionColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, Integer> quantityColumn;
    // Removed unused refreshButton and exportButton to match FXML
    @FXML private Label messageLabel;

    private ObservableList<Product> data;

    @FXML
    public void initialize() {
        bookIdColumn.setCellValueFactory(new PropertyValueFactory<>("productId"));
        bookNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        authorColumn.setCellValueFactory(new PropertyValueFactory<>("author"));
        publisherColumn.setCellValueFactory(new PropertyValueFactory<>("publisher"));
        editionColumn.setCellValueFactory(new PropertyValueFactory<>("edition"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        Stock stock = new Stock();
        List<Product> stockList = stock.readStockFromFile();
        data = FXCollections.observableArrayList(stockList);
        stockTable.setItems(data);
        stockTable.setPlaceholder(new Label("No products in stock. Add new stock to get started!"));

        // Button event handlers are now set via FXML onAction attributes
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

    @FXML
    private void handleExport() {
        exportStockToCSV();
    }

    @FXML
    private void handleUpdateStock() {
        // TODO: Implement update stock logic or show a dialog
        messageLabel.setText("Update Stock action triggered.");
    }

    @FXML
    private void handleAddStock() {
        // TODO: Implement add stock logic or show a dialog
        messageLabel.setText("Add Stock action triggered.");
    }
}
