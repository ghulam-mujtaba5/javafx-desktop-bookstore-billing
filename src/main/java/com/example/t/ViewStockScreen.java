package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.util.List;

public class ViewStockScreen {
    @FXML private TableView<Product> stockTable;
    @FXML private TableColumn<Product, Integer> bookIdColumn;
    @FXML private TableColumn<Product, String> bookNameColumn;
    @FXML private TableColumn<Product, String> authorColumn;
    @FXML private TableColumn<Product, String> publisherColumn;
    @FXML private TableColumn<Product, String> editionColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, Integer> quantityColumn;
    @FXML private Button exportButton;
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

        try {
            List<Product> products = new com.example.t.dao.ProductDao().getAllProducts();
            if (!products.isEmpty()) {
                data = FXCollections.observableArrayList(products);
            } else {
                Stock stock = new Stock();
                data = FXCollections.observableArrayList(stock.readStockFromFile());
            }
        } catch (Exception e) {
            Stock stock = new Stock();
            data = FXCollections.observableArrayList(stock.readStockFromFile());
        }
        stockTable.setItems(data);
        stockTable.setPlaceholder(new Label("No products in stock. Add new stock to get started!"));
    }

    public void show() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/ViewStockScreen.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Stock Inventory");
            stage.setScene(new Scene(root, 900, 600));
            stage.show();
        } catch (Exception e) {
            Logger.error("Failed to show ViewStockScreen", e);
        }
    }

    private void refreshStock() {
        try {
            List<Product> products = new com.example.t.dao.ProductDao().getAllProducts();
            data.setAll(products);
        } catch (Exception e) {
            Stock stock = new Stock();
            data.setAll(stock.readStockFromFile());
        }
        if (messageLabel != null) {
            messageLabel.setText("Stock refreshed");
        }
    }

    private void exportStockToCSV() {
        try {
            java.nio.file.Path exportPath = com.example.t.database.DatabaseManager.getExportsDirectory().resolve("view_stock_export.csv");
            com.example.t.service.CsvSyncService.exportProductsToFile(data, exportPath);
            if (messageLabel != null) {
                messageLabel.setText("Exported " + data.size() + " products to: " + exportPath.getFileName());
            }
        } catch (Exception e) {
            if (messageLabel != null) {
                messageLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExport() {
        exportStockToCSV();
    }

    @FXML
    private void handleUpdateStock() {
        if (messageLabel != null) {
            messageLabel.setText("Update Stock action triggered.");
        }
    }

    @FXML
    private void handleAddStock() {
        if (messageLabel != null) {
            messageLabel.setText("Add Stock action triggered.");
        }
    }
}
