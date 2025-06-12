package com.example.t;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class UpdateStockScreen {

    private TableView<Product> table;
    private ObservableList<Product> data;
    private TextField searchField;
    private Stage stage;
    private Product selectedProduct;

    public void show() {
        stage = new Stage();
        stage.setTitle("Update Stock");
        stage.setMaximized(true);

        table = new TableView<>();
        // Use UNCONSTRAINED_RESIZE_POLICY as CONSTRAINED_RESIZE_POLICY is deprecated in Java 20+
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setEditable(true);
        table.setPrefHeight(600); // Set preferred height of the table

        TableColumn<Product, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("productId"));

        TableColumn<Product, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<Product, Integer> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<Product, Double> priceColumn = new TableColumn<>("Sale Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        priceColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            double newPrice = event.getNewValue();
            product.setPrice(newPrice);
            Stock stock = new Stock();
            stock.saveStockToFile(data);
        });
        priceColumn.setEditable(true);

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

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedProduct = newSelection;
            }
        });

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
        overlay.setMaxWidth(700);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 18px; -fx-padding: 30 30 30 30;");

        Label title = new Label("Update Stock");
        title.getStyleClass().add("title-label");

        searchField = new TextField();
        searchField.setPromptText("Search by Name...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterStock(newValue));

        Button sortButton = new Button("Sort by Price");
        sortButton.setOnAction(event -> sortStockByPrice());

        Button sortByIdButton = new Button("Sort by ID");
        sortByIdButton.setOnAction(event -> sortStockById());

        Button changePriceButton = new Button("Change Price");
        changePriceButton.setOnAction(event -> showPriceChangeDialog());

        Button addStockButton = new Button("Add Stock");
        addStockButton.setOnAction(event -> showAddStockDialog());

        HBox buttonContainer = new HBox(12);
        buttonContainer.setAlignment(Pos.CENTER);
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(event -> {
            if (selectedProduct != null) {
                showDeleteConfirmation(selectedProduct);
            } else {
                showAlert("No product selected.", Alert.AlertType.ERROR);
            }
        });
        buttonContainer.getChildren().addAll(deleteButton, changePriceButton, addStockButton);

        overlay.getChildren().addAll(title, searchField, sortButton, sortByIdButton, buttonContainer, table);
        root.getChildren().add(overlay);

        double screenWidth = Screen.getPrimary().getBounds().getWidth() - 200;
        double screenHeight = Screen.getPrimary().getBounds().getHeight() - 200;

        Scene scene = new Scene(root, screenWidth, screenHeight);
        scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                if (selectedProduct != null) {
                    showDeleteConfirmation(selectedProduct);
                } else {
                    showAlert("No product selected.", Alert.AlertType.ERROR);
                }
            }
        });

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

    private void sortStockById() {
        Comparator<Product> idComparator = Comparator.comparing(Product::getProductId);
        FXCollections.sort(data, idComparator);
    }

    private void showDeleteConfirmation(Product product) {
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Confirmation");
        confirmationDialog.setHeaderText(null);
        confirmationDialog.setContentText("Are you sure you want to delete this product?");

        Optional<ButtonType> result = confirmationDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            data.remove(product);
            Stock stock = new Stock();
            stock.saveStockToFile(data);
        }
    }

    private void showAlert(String message, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle("Alert");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showPriceChangeDialog() {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Change Price");
        dialog.setHeaderText("Select a product and enter the new price");

        TextField searchTextField = new TextField();
        searchTextField.setPromptText("Search by Product Name");

        TableView<Product> searchResultsTable = new TableView<>();
        // Use UNCONSTRAINED_RESIZE_POLICY as CONSTRAINED_RESIZE_POLICY is deprecated in Java 20+
        searchResultsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Product, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<Product, Double> priceColumn = new TableColumn<>("Sale Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));

        // Suppress type safety warning for varargs TableColumn
        @SuppressWarnings("unchecked")
        TableColumn<Product, ?>[] searchColumns = new TableColumn[] {nameColumn, priceColumn};
        searchResultsTable.getColumns().addAll(searchColumns);

        TextField newPriceTextField = new TextField();
        newPriceTextField.setPromptText("Enter new price");

        ButtonType changeButtonType = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);

        VBox dialogContent = new VBox(10);
        dialogContent.getChildren().addAll(searchTextField, searchResultsTable, newPriceTextField);
        dialog.getDialogPane().setContent(dialogContent);

        // Create a filtered list to hold the search results
        FilteredList<Product> filteredList = new FilteredList<>(data, p -> true);
        searchResultsTable.setItems(filteredList);

        // Apply the search filter when the text in the search field changes
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(product -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String searchTerm = newValue.toLowerCase();
                return product.getProductName().toLowerCase().contains(searchTerm);
            });
        });

        searchResultsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Product selectedProduct = searchResultsTable.getSelectionModel().getSelectedItem();
                if (selectedProduct != null) {
                    dialog.setResult(selectedProduct.getPrice());
                    dialog.close();
                }
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == changeButtonType) {
                String newPriceString = newPriceTextField.getText();
                try {
                    double newPrice = Double.parseDouble(newPriceString);
                    return newPrice;
                } catch (NumberFormatException e) {
                    showAlert("Invalid price format", Alert.AlertType.ERROR);
                }
            }
            return null;
        });

        Optional<Double> result = dialog.showAndWait();
        result.ifPresent(newPrice -> {
            selectedProduct.setPrice(newPrice);
            Stock stock = new Stock();
            stock.saveStockToFile(data);

            // Show a notification without closing the dialog
            showAlert("Price changed successfully.", Alert.AlertType.INFORMATION);

            // Clear the fields and move focus to the next field and button
            searchTextField.clear();
            newPriceTextField.clear();
            searchTextField.requestFocus();
        });
    }

    private void showAddStockDialog() {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Add Stock");
        dialog.setHeaderText("Select a product and enter the new quantity");

        TextField searchTextField = new TextField();
        searchTextField.setPromptText("Search by Product Name");

        TableView<Product> searchResultsTable = new TableView<>();
        // Use UNCONSTRAINED_RESIZE_POLICY as CONSTRAINED_RESIZE_POLICY is deprecated in Java 20+
        searchResultsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Product, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<Product, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("productId"));

        TableColumn<Product, Double> priceColumn = new TableColumn<>("Sale Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Product, Integer> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        // Suppress type safety warning for varargs TableColumn
        @SuppressWarnings("unchecked")
        TableColumn<Product, ?>[] searchColumns2 = new TableColumn[] {nameColumn, idColumn, priceColumn, quantityColumn};
        searchResultsTable.getColumns().addAll(searchColumns2);

        TextField newQuantityTextField = new TextField();
        newQuantityTextField.setPromptText("Enter new quantity");

        TextField salePriceTextField = new TextField();
        salePriceTextField.setPromptText("Enter sale price");

        TextField purchasePriceTextField = new TextField();
        purchasePriceTextField.setPromptText("Enter purchase price");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        VBox dialogContent = new VBox(10);
        dialogContent.getChildren().addAll(searchTextField, searchResultsTable, newQuantityTextField, salePriceTextField, purchasePriceTextField);
        dialog.getDialogPane().setContent(dialogContent);

        // Create a filtered list to hold the search results
        FilteredList<Product> filteredList = new FilteredList<>(data, p -> true);
        searchResultsTable.setItems(filteredList);

        // Apply the search filter when the text in the search field changes
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(product -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String searchTerm = newValue.toLowerCase();
                return product.getProductName().toLowerCase().contains(searchTerm);
            });
        });

        // Focus on the searchTextField when the dialog is shown
        Platform.runLater(searchTextField::requestFocus);

        searchResultsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Product selectedProduct = searchResultsTable.getSelectionModel().getSelectedItem();
                if (selectedProduct != null) {
                    dialog.setResult(selectedProduct.getProductId());
                    salePriceTextField.setText(String.valueOf(selectedProduct.getPrice()));
                    purchasePriceTextField.setText(String.valueOf(selectedProduct.getPurchasePrice()));
                    dialog.close();
                }
            }
        });

        // Clear the fields when focused
        dialog.setOnShown(event -> {
            searchTextField.clear();
            newQuantityTextField.clear();
            salePriceTextField.clear();
            purchasePriceTextField.clear();
            searchTextField.requestFocus();
        });

        // Enable navigation between fields using keyboard keys

        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Node focusOwner = dialog.getDialogPane().lookup(".button");
                if (focusOwner instanceof Button) {
                    Button button = (Button) focusOwner;
                    if (button.getText().equals("Add")) {
                        Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
                        addButton.fire();
                    }
                }
            } else if (event.getCode() == KeyCode.TAB) {
                // Handle tab key navigation between fields (same as before)
                if (searchTextField.isFocused()) {
                    newQuantityTextField.requestFocus();
                    event.consume();
                } else if (newQuantityTextField.isFocused()) {
                    salePriceTextField.requestFocus();
                    event.consume();
                } else if (salePriceTextField.isFocused()) {
                    purchasePriceTextField.requestFocus();
                    event.consume();
                } else if (purchasePriceTextField.isFocused()) {
                    searchTextField.requestFocus();
                    event.consume();
                }
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String newQuantityString = newQuantityTextField.getText();
                String salePriceString = salePriceTextField.getText();
                String purchasePriceString = purchasePriceTextField.getText();
                try {
                    int newQuantity = Integer.parseInt(newQuantityString);
                    // Removed unused local variables salePrice and purchasePrice
                    Double.parseDouble(salePriceString);
                    Double.parseDouble(purchasePriceString);
                    return newQuantity;
                } catch (NumberFormatException e) {
                    showAlert("Invalid quantity or price format", Alert.AlertType.ERROR);
                }
            }
            return null;
        });

        Optional<Integer> result = dialog.showAndWait();
        result.ifPresent(newQuantity -> {
            Product selectedProduct = searchResultsTable.getSelectionModel().getSelectedItem();
            if (selectedProduct != null) {
                double salePrice = Double.parseDouble(salePriceTextField.getText());
                double purchasePrice = Double.parseDouble(purchasePriceTextField.getText());
                Product newStock = new Product(selectedProduct.getProductId(), selectedProduct.getProductName(), newQuantity, salePrice, purchasePrice);
                data.add(newStock);
                Stock stock = new Stock();
                stock.saveStockToFile(data);
            }

            // Show a notification without closing the dialog
            showAlert("Stock added successfully.", Alert.AlertType.INFORMATION);

            // Clear the fields and move focus to the next field and button
            newQuantityTextField.clear();
            salePriceTextField.clear();
            purchasePriceTextField.clear();
            newQuantityTextField.requestFocus();
        });


    }
}