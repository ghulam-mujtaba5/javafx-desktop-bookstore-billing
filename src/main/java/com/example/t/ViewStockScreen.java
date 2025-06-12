package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.geometry.Pos;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class ViewStockScreen {

    private TableView<Product> table;
    private ObservableList<Product> data;
    private TextField searchField;
    private Stage stage;

    public void show() {
        stage = new Stage();
        stage.setTitle("View Stock");
        stage.setMaximized(true);

        table = new TableView<>();
        // Use UNCONSTRAINED_RESIZE_POLICY as CONSTRAINED_RESIZE_POLICY is deprecated in Java 20+
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(600); // Set preferred height of the table

        TableColumn<Product, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("productId"));

        TableColumn<Product, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<Product, Integer> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<Product, Double> priceColumn = new TableColumn<>("Sale Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Product, Double> purchasePriceColumn = new TableColumn<>("Purchase Price");
        purchasePriceColumn.setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));

        // Suppress type safety warning for varargs TableColumn
        @SuppressWarnings("unchecked")
        TableColumn<Product, ?>[] columns = new TableColumn[] {idColumn, nameColumn, quantityColumn, priceColumn, purchasePriceColumn};
        table.getColumns().addAll(columns);

        Stock stock = new Stock();
        List<Product> stockList = stock.readStockFromFile();
        data = FXCollections.observableArrayList(stockList);
        table.setItems(data);

        StackPane root = new StackPane();
        Image backgroundImage = new Image(FilePathManager.getBackgroundImagePath());
        BackgroundImage background = new BackgroundImage(
                backgroundImage,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(1.0, 1.0, true, true, false, false)
        );
        root.setBackground(new Background(background));

        VBox overlay = new VBox(24);
        overlay.setAlignment(Pos.CENTER);
        overlay.setMaxWidth(600);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 18px; -fx-padding: 30 30 30 30;");

        Label title = new Label("View Stock");
        title.getStyleClass().add("title-label");

        searchField = new TextField();
        searchField.setPromptText("Search by Name...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterStock(newValue));

        Button sortButton = new Button("Sort by Price");
        sortButton.setOnAction(event -> sortStockByPrice());

        overlay.getChildren().addAll(title, searchField, sortButton, table);
        root.getChildren().add(overlay);

        double screenWidth = Screen.getPrimary().getBounds().getWidth() - 200;
        double screenHeight = Screen.getPrimary().getBounds().getHeight() - 200;

        Scene scene = new Scene(root, screenWidth, screenHeight);
        scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void filterStock(String searchTerm) {
        Predicate<Product> filterPredicate = product -> product.getProductName().toLowerCase().contains(searchTerm.toLowerCase());
        ObservableList<Product> filteredData = data.filtered(filterPredicate);
        table.setItems(filteredData);
    }

    private void sortStockByPrice() {
        Comparator<Product> priceComparator = Comparator.comparing(Product::getPrice);
        FXCollections.sort(data, priceComparator);
    }
}
