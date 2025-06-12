package com.example.t;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;

import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CreateOrderScreen {

    private Stock stock;
    private List<Product> selectedProducts;
    private Shop shop;

    public CreateOrderScreen(Stock stock, Shop shop) {
        this.stock = stock;
        this.selectedProducts = new ArrayList<>();
        this.shop = shop;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Create Order");

        GridPane grid = new GridPane();
        Image backgroundImage = new Image("D:\\t\\src\\main\\background1.jpg");  // Replace "your_image_path.jpg" with the actual path to your image
        BackgroundImage background = new BackgroundImage(
                backgroundImage,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, true)
        );
        grid.setBackground(new Background(background));
        grid.setAlignment(Pos.CENTER_LEFT); // Align grid to the left side
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 0, 20, 100));

        Label titleLabel = new Label("Create Order");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        GridPane.setConstraints(titleLabel, 0, 0, 2, 1);

        Label productLabel = new Label("Search Product:");
        GridPane.setConstraints(productLabel, 0, 1);

        TextField searchField = new TextField();
        GridPane.setConstraints(searchField, 1, 1);

        TableView<Product> productTable = new TableView<>();
        TableColumn<Product, String> productNameColumn = new TableColumn<>("Product Name");
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        TableColumn<Product, Integer> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        TableColumn<Product, Double> priceColumn = new TableColumn<>("Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        TableColumn<Product, Double> discountColumn = new TableColumn<>("Discount");
        discountColumn.setCellValueFactory(new PropertyValueFactory<>("discount"));
        productTable.getColumns().addAll(productNameColumn, quantityColumn, priceColumn, discountColumn);
        GridPane.setConstraints(productTable, 0, 2, 2, 1);

        Label quantityLabel = new Label("Quantity:");
        GridPane.setConstraints(quantityLabel, 0, 3);

        TextField quantityField = new TextField();
        GridPane.setConstraints(quantityField, 1, 3);

        Label discountLabel = new Label("Discount (%):");
        GridPane.setConstraints(discountLabel, 0, 4);

        TextField discountField = new TextField();
        GridPane.setConstraints(discountField, 1, 4);

        Button addButton = new Button("Add to Order");
        GridPane.setConstraints(addButton, 1, 5);

        TableView<Product> orderTable = new TableView<>();
        TableColumn<Product, String> selectedProductNameColumn = new TableColumn<>("Product Name");
        selectedProductNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        TableColumn<Product, Integer> selectedQuantityColumn = new TableColumn<>("Quantity");
        selectedQuantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        TableColumn<Product, Double> selectedPriceColumn = new TableColumn<>("Price");
        selectedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        TableColumn<Product, Double> selectedDiscountColumn = new TableColumn<>("Discount");
        selectedDiscountColumn.setCellValueFactory(new PropertyValueFactory<>("discount"));
        orderTable.getColumns().addAll(selectedProductNameColumn, selectedQuantityColumn, selectedPriceColumn, selectedDiscountColumn);
        GridPane.setConstraints(orderTable, 0, 6, 2, 1);

        // Set cell factories for editable columns
        priceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        discountColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        selectedPriceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        selectedDiscountColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));

        // Set edit commit handlers for editable columns
        discountColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            product.setDiscount(event.getNewValue());
            orderTable.refresh(); // Update the table view
        });

        selectedDiscountColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            product.setDiscount(event.getNewValue());
            orderTable.refresh(); // Update the table view
        });

        selectedPriceColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            product.setPrice(event.getNewValue());
        });

        selectedDiscountColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            product.setDiscount(event.getNewValue());
        });

        Button generateInvoiceButton = new Button("Generate Invoice");
        GridPane.setConstraints(generateInvoiceButton, 0, 7, 2, 1);

        Button removeButton = new Button("Remove");
        GridPane.setConstraints(removeButton, 1, 8);

        grid.getChildren().addAll(titleLabel, productLabel, searchField, productTable, quantityLabel, quantityField,
                discountLabel, discountField, addButton, orderTable, generateInvoiceButton, removeButton);

        productTable.setItems(FXCollections.observableArrayList(stock.getStock()));

        // Inside the addButton.setOnAction() method
        addButton.setOnAction(event -> {
            Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
            int quantity = Integer.parseInt(quantityField.getText());
            double discount = Double.parseDouble(discountField.getText());

            if (selectedProduct != null && quantity > 0) {
                double discountedPrice = selectedProduct.getPrice() * (1 - discount / 100);
                Product orderProduct = new Product(
                        selectedProduct.getProductId(),
                        selectedProduct.getProductName(),
                        quantity,
                        discountedPrice, // Use discounted price
                        discount // Set discount value
                );
                selectedProducts.add(orderProduct);
                orderTable.getItems().add(orderProduct);
                // Set formatted discount value
                orderProduct.setDiscount(Double.parseDouble(String.format("%.2f", discount)));
                quantityField.clear();
                discountField.clear();
                searchField.clear();
                searchField.requestFocus();
            }
        });

        removeButton.setOnAction(event -> {
            Product selectedProduct = orderTable.getSelectionModel().getSelectedItem();
            if (selectedProduct != null) {
                selectedProducts.remove(selectedProduct);
                orderTable.getItems().remove(selectedProduct);
            }
        });

        generateInvoiceButton.setOnAction(event -> {
            if (!selectedProducts.isEmpty()) {
                double totalAmount = 0.0;

                for (Product product : selectedProducts) {
                    double discountedPrice = product.getPrice() * (1 - product.getDiscount() / 100);
                    totalAmount += discountedPrice * product.getQuantity();
                }

                TextInputDialog nameDialog = new TextInputDialog();
                nameDialog.setTitle("Customer Name");
                nameDialog.setHeaderText("Enter Customer Name");
                nameDialog.setContentText("Name:");

                Optional<String> result = nameDialog.showAndWait();
                if (result.isPresent()) {
                    String customerName = result.get();

                    if (customerName.isEmpty()) {
                        customerName = "Walk-in Customer"; // Use default name if no name is entered
                    }

                    int orderId = generateOrderId();
                    Order order = new Order(orderId, LocalDate.now(), customerName, selectedProducts);
                    order.calculateTotalAmount();

                    boolean orderAccepted = order.processOrder(stock.getStock());

                    if (orderAccepted) {
                        saveInvoiceAsObject(order);
                        updateStockQuantity(order); // Update stock quantity
                        showInvoice(order);
                    } else {
                        showAlert("Order Rejected", "The order could not be processed due to insufficient stock.");
                        return;
                    }

                    showAlert("Order Generated", "Order has been generated successfully.\nOrder ID: " + order.getOrderId()
                            + "\nTotal Amount: $" + order.getTotalAmount());

                    stage.close();
                } else {
                    showAlert("Invalid Input", "Customer name cannot be empty.");
                }
            } else {
                showAlert("Empty Order", "No products selected for the order.");
            }
        });

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String searchTerm = newValue.toLowerCase();
            List<Product> filteredProducts = stock.getStock().stream()
                    .filter(product -> product.getProductName().toLowerCase().contains(searchTerm))
                    .collect(Collectors.toList());
            productTable.setItems(FXCollections.observableArrayList(filteredProducts));
        });
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.ENTER) {
                productTable.requestFocus();
                productTable.getSelectionModel().selectFirst();
            }
        });

        // Allow adding product to order table by pressing Enter key
        quantityField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                discountField.requestFocus();
            }
        });
        productTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
                if (selectedProduct != null) {
                    quantityField.requestFocus();
                    addButton.fire();
                }
            }
        });

        // Allow generating invoice by pressing Enter key
        discountField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addButton.fire();
                orderTable.refresh();
            }
        });

        Scene scene = new Scene(grid);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private void updateStockQuantity(Order order) {
        List<Product> stockList = stock.getStock();

        for (Product orderedProduct : order.getProducts()) {
            for (Product stockProduct : stockList) {
                if (stockProduct.getProductId() == orderedProduct.getProductId()) {
                    int updatedQuantity = stockProduct.getQuantity() - orderedProduct.getQuantity();
                    stockProduct.setQuantity(updatedQuantity);
                    break;
                }
            }
        }

        stock.saveStockToFile(); // Save the updated stock to the file
    }


    private void saveInvoiceAsObject(Order order) {
        try {
            File file = new File("D:\\t\\src\\main\\invoice_history.txt");
            if (!file.exists()) {
                file.createNewFile();
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                writer.println("Invoice ID: " + order.getOrderId());
                writer.println("Order Date: " + order.getOrderDate());
                writer.println("Customer Name: " + order.getCustomerName());
                writer.println("Total Amount: Rs" + order.getTotalAmount());
                writer.println("Order Details:");
                writer.println("Product Name\tQuantity\tPrice\tDiscount %");

                for (Product product : order.getProducts()) {
                    writer.println(product.getProductName() + "\t\t" + product.getQuantity()
                            + "\t\t" + product.getPrice() + "\t\t" + product.getDiscount());
                }

                writer.println(); // Add a new line after each invoice
            }

            showAlert("Invoice Saved", "Invoice has been saved successfully as a text file.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showInvoice(Order order) {
        Stage invoiceStage = new Stage();
        invoiceStage.setTitle("Invoice");

        invoiceStage.setOnCloseRequest(event -> {
            event.consume(); // Consume the close request event
            CreateOrderScreen createOrderScreen = new CreateOrderScreen(stock, shop);
            createOrderScreen.show();
            invoiceStage.close(); // Only close the invoice stage
        });

        VBox invoiceBox = new VBox();
        invoiceBox.setSpacing(10);
        invoiceBox.setPadding(new Insets(20));

        Label titleLabel = new Label("Invoice");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label shopNameLabel = new Label(shop.getShopName());
        shopNameLabel.setStyle("-fx-font-size: 16px;");
        Label contactLabel = new Label("Mobile Number: " + shop.getMobileNumber());
        Label addressLabel = new Label("Address: " + shop.getAddress());

        Label orderDetailsLabel = new Label("Order Details");
        orderDetailsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label orderIdLabel = new Label("Order ID: " + order.getOrderId());
        Label orderDateLabel = new Label("Order Date: " + order.getOrderDate().toString());
        Label customerNameLabel = new Label("Customer Name: " + order.getCustomerName());

        TableView<Product> productTable = new TableView<>();
        TableColumn<Product, String> productNameColumn = new TableColumn<>("Product Name");
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        TableColumn<Product, Integer> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        TableColumn<Product, Double> priceColumn = new TableColumn<>("Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        TableColumn<Product, Double> discountColumn = new TableColumn<>("Discount %");
        discountColumn.setCellValueFactory(new PropertyValueFactory<>("discount"));
        productTable.getColumns().addAll(productNameColumn, quantityColumn, priceColumn, discountColumn);
        productTable.setItems(FXCollections.observableArrayList(order.getProducts()));
        productTable.setEditable(true);

        priceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        priceColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            product.setPrice(event.getNewValue());
        });

        discountColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        discountColumn.setOnEditCommit(event -> {
            Product product = event.getRowValue();
            product.setDiscount(event.getNewValue());
        });

        Label totalAmountLabel = new Label("Total Amount: Rs" + order.getTotalAmount());
        totalAmountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        invoiceBox.getChildren().addAll(
                titleLabel,
                shopNameLabel,
                contactLabel,
                addressLabel,
                new Separator(),
                orderDetailsLabel,
                orderIdLabel,
                orderDateLabel,
                customerNameLabel,
                new Separator(),
                productTable,
                totalAmountLabel
        );

        Scene invoiceScene = new Scene(invoiceBox);
        invoiceStage.setScene(invoiceScene);
        invoiceStage.setMaximized(true);
        invoiceStage.show();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private int generateOrderId() {
        // You can implement your own logic to generate unique order IDs
        return (int) (Math.random() * 1000) + 1;
    }
}
