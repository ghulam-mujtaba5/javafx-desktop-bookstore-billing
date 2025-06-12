package com.example.t;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AddStockScreen {
    private Stock stock;
    private Stage primaryStage;
    private TextField idField;
    private TextField nameTextField;
    private TextField purchasePriceField;
    private TextField priceField;
    private TextField quantityField;
    private ObservableList<String> productNames;
    private List<Product> productList;
    private TableView<Product> productTable;

    public AddStockScreen(Stock stock) {
        this.stock = stock;
        this.productList = stock.getStock();
    }

    public void show() {
        primaryStage = new Stage();
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);
        primaryStage.setTitle("Add Stock");

        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));
        gridPane.setStyle("-fx-background-color: transparent;");

        Label idLabel = new Label("Product ID:");
        idField = new TextField();
        Label nameLabel = new Label("Product Name (Type to Search):");
        nameTextField = new TextField();
        Label purchasePriceLabel = new Label("Purchase Price:");
        purchasePriceField = new TextField();
        Label priceLabel = new Label("Product Sale Price:");
        priceField = new TextField();
        Label quantityLabel = new Label("Product Quantity:");
        quantityField = new TextField();
        Button addButton = new Button("Add");

        // Create a list of strings containing the product IDs and names
        productNames = FXCollections.observableArrayList();

        // Set the items of the nameTextField to the productNames list
        nameTextField.setOnKeyReleased(event -> {
            String enteredText = nameTextField.getText().toLowerCase();
            filterStock(enteredText);
        });

        // Add ChangeListener for the nameTextField
        nameTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                // Extract the product ID from the selected item
                String[] parts = newValue.split(" - ");
                int productId = Integer.parseInt(parts[0]);

                // Find the product with the selected ID from the productList
                Product selectedProduct = productList.stream()
                        .filter(product -> product.getProductId() == productId)
                        .findFirst()
                        .orElse(null);

                if (selectedProduct != null) {
                    idField.setText(String.valueOf(selectedProduct.getProductId()));
                    purchasePriceField.setText(String.valueOf(selectedProduct.getPurchasePrice()));
                    priceField.setText(String.valueOf(selectedProduct.getPrice()));
                    quantityField.setText(String.valueOf(selectedProduct.getQuantity()));
                }
            }
        });

        // Add Enter key press event handler for the ID field
        idField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                int id = Integer.parseInt(idField.getText());

                // Check if the ID already exists in the stock
                if (stock.isProductIdExists(id)) {
                    showAlert("Error", "Product ID Already Exists",
                            "A product with ID " + id + " already exists in the stock.");
                    idField.clear(); // Clear the product ID field
                    return; // Stop execution of adding the product
                }

                nameTextField.requestFocus(); // Transfer focus to the nameTextField
            }
        });

        // Add Enter key press event handlers for other fields
        nameTextField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                purchasePriceField.requestFocus();
            }
        });

        purchasePriceField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                priceField.requestFocus();
            }
        });

        priceField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                quantityField.requestFocus();
            }
        });

        quantityField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addButton.fire();
            }
        });

        gridPane.add(idLabel, 0, 0);
        gridPane.add(idField, 1, 0);
        gridPane.add(nameLabel, 0, 1);
        gridPane.add(nameTextField, 1, 1);
        gridPane.add(purchasePriceLabel, 0, 2);
        gridPane.add(purchasePriceField, 1, 2);
        gridPane.add(priceLabel, 0, 3);
        gridPane.add(priceField, 1, 3);
        gridPane.add(quantityLabel, 0, 4);
        gridPane.add(quantityField, 1, 4);
        gridPane.add(addButton, 0, 5);

        productTable = new TableView<>();
        TableColumn<Product, Integer> productIdColumn = new TableColumn<>("Product ID");
        productIdColumn.setCellValueFactory(new PropertyValueFactory<>("productId"));
        TableColumn<Product, String> productNameColumn = new TableColumn<>("Product Name");
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        TableColumn<Product, Double> purchasePriceColumn = new TableColumn<>("Purchase Price");
        purchasePriceColumn.setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));
        TableColumn<Product, Double> priceColumn = new TableColumn<>("Sale Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        TableColumn<Product, Integer> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        productTable.getColumns().addAll(productIdColumn, productNameColumn, purchasePriceColumn, priceColumn, quantityColumn);
        productTable.setItems(FXCollections.observableArrayList(productList));
        productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        productTable.setPlaceholder(new Label("No products"));

        VBox formContainer = new VBox(gridPane);
        formContainer.setAlignment(Pos.CENTER);
        formContainer.setSpacing(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(gridPane, Priority.ALWAYS);

        VBox.setVgrow(productTable, Priority.ALWAYS);

        VBox leftContainer = new VBox(formContainer, productTable);
        leftContainer.setAlignment(Pos.CENTER);
        leftContainer.setSpacing(11); // Increase spacing by 1 unit (10% of the default 10 spacing)
        leftContainer.setPadding(new Insets(20));
        leftContainer.setStyle("-fx-background-color: transparent;");

        VBox rightContainer = new VBox();
        rightContainer.setAlignment(Pos.CENTER);
        rightContainer.setSpacing(10);
        rightContainer.setPadding(new Insets(20));
        rightContainer.setStyle("-fx-background-color: transparent;");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(40));

        // Set background image
        Image backgroundImage = new Image(FilePathManager.getBackgroundImagePath());
        BackgroundImage background = new BackgroundImage(backgroundImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true, true));
        root.setBackground(new Background(background));

        double screenWidth = Screen.getPrimary().getBounds().getWidth()-100;
        double screenHeight = Screen.getPrimary().getBounds().getHeight()-100;

        Scene scene = new Scene(root, screenWidth, screenHeight);
        scene.setFill(null); // Make the scene background transparent

        root.setLeft(leftContainer);

        Button closeButton = new Button("Close");
        HBox closeBox = new HBox(closeButton);
        closeBox.setAlignment(Pos.TOP_RIGHT);
        closeBox.setPadding(new Insets(10));
        root.setTop(closeBox);

        // Title Bar
        StackPane titleBar = new StackPane();
        titleBar.setStyle("-fx-background-color: #212121;");
        titleBar.setPrefHeight(30);
        Label titleLabel = new Label("Add Stock");
        titleLabel.setStyle("-fx-text-fill: white;");
        titleBar.getChildren().add(titleLabel);
        StackPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        StackPane.setMargin(titleLabel, new Insets(0, 0, 0, 10));
        root.setTop(titleBar);

        addButton.setOnAction(event -> {
            try {
                int id = Integer.parseInt(idField.getText());
                String name = nameTextField.getText();
                double purchasePrice = Double.parseDouble(purchasePriceField.getText());
                double price = Double.parseDouble(priceField.getText());
                int quantity = Integer.parseInt(quantityField.getText());

                // Check if the ID already exists in the stock
                if (stock.isProductIdExists(id)) {
                    showAlert("Error", "Product ID Already Exists",
                            "A product with ID " + id + " already exists in the stock.");
                    return; // Stop execution of adding the product
                }

                Product product = new Product(id, name, quantity, price, purchasePrice);
                stock.addProduct(product);

                // Clear the input fields after adding the product
                idField.clear();
                nameTextField.clear();
                purchasePriceField.clear();
                priceField.clear();
                quantityField.clear();

                // Update the product list and table view
                productList.add(product);
                productTable.setItems(FXCollections.observableArrayList(productList));

                showAlert("Success", "Product Added", "Product has been successfully added to the stock.");
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid Input", "Please enter valid numeric values for ID, Purchase Price, Price, and Quantity.");
            }
        });

        closeButton.setOnAction(event -> {
            primaryStage.close();
        });
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                primaryStage.setIconified(true);
            }
        });
        primaryStage.setScene(scene);
        primaryStage.show();
        // Set focus to the ID field
        Platform.runLater(() -> idField.requestFocus());
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void filterStock(String searchTerm) {
        Predicate<Product> filterPredicate = product -> product.getProductName().toLowerCase().contains(searchTerm.toLowerCase());
        ObservableList<Product> filteredData = productList.stream().filter(filterPredicate).collect(Collectors.toCollection(FXCollections::observableArrayList));
        productTable.setItems(filteredData);
    }

}
