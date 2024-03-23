package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;

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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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

        table.getColumns().addAll(idColumn, nameColumn, quantityColumn, priceColumn, purchasePriceColumn);

        Stock stock = new Stock();
        List<Product> stockList = stock.readStockFromFile();
        data = FXCollections.observableArrayList(stockList);
        table.setItems(data);

        VBox vbox = new VBox();
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10));

        searchField = new TextField();
        searchField.setPromptText("Search by Name...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterStock(newValue);
        });

        Button sortButton = new Button("Sort by Price");
        sortButton.setOnAction(event -> sortStockByPrice());

        vbox.getChildren().addAll(searchField, sortButton, table);

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(vbox);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10));
        gridPane.add(borderPane, 0, 0);

        // Set the background image using a URL path
        String imageUrl = FilePathManager.getBackgroundImagePath();
        Image backgroundImage = new Image(imageUrl);
        BackgroundImage background = new BackgroundImage(
                backgroundImage,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, true)
        );

        gridPane.setBackground(new Background(background));

        double screenWidth = Screen.getPrimary().getBounds().getWidth()-200 ;
        double screenHeight = Screen.getPrimary().getBounds().getHeight() -200;

        Scene scene = new Scene(gridPane, screenWidth, screenHeight);
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
