package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ViewOrderScreen {
    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, Integer> colOrderId;
    @FXML private TableColumn<Order, String> colCustomerName;
    @FXML private TableColumn<Order, String> colDate;
    @FXML private TableColumn<Order, Double> colTotal;
    @FXML private Button refreshButton;
    @FXML private Label messageLabel;
    @FXML private TextField searchField;

    private ObservableList<Order> masterData;
    private FilteredList<Order> filteredData;
    private SortedList<Order> sortedData;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
        setupSearch();
        setupKeyboardShortcuts();
        setupContextMenu();
        refreshButton.setOnAction(e -> refreshOrders());
        updatePlaceholderText();
    }

    private void setupTableColumns() {
        colOrderId.setCellValueFactory(cellData -> cellData.getValue().orderIdProperty().asObject());
        colCustomerName.setCellValueFactory(cellData -> cellData.getValue().customerNameProperty());
        colDate.setCellValueFactory(cellData -> cellData.getValue().orderDateProperty().asString());
        colTotal.setCellValueFactory(cellData -> cellData.getValue().totalAmountProperty().asObject());

        // Enable sorting for all columns
        colOrderId.setSortType(TableColumn.SortType.DESCENDING);
        
        // Format the date column
        colDate.setCellFactory(column -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(dateFormatter.format(getTableRow().getItem().getOrderDate()));
                }
            }
        });

        // Format the total amount column
        colTotal.setCellFactory(column -> new TableCell<Order, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", amount));
                }
            }
        });

        // Add row double-click handler
        orderTable.setRowFactory(tv -> {
            TableRow<Order> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showOrderDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void loadData() {
        masterData = Order.readOrdersFromFile();
        filteredData = new FilteredList<>(masterData, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(orderTable.comparatorProperty());
        orderTable.setItems(sortedData);
        
        // Default sort by Order ID descending
        orderTable.getSortOrder().add(colOrderId);
    }

    private void setupSearch() {
        searchField.setPromptText("Search orders...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(order -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String searchText = newValue.toLowerCase();
                return String.valueOf(order.getOrderId()).contains(searchText) ||
                       order.getCustomerName().toLowerCase().contains(searchText) ||
                       dateFormatter.format(order.getOrderDate()).contains(searchText);
            });
            updatePlaceholderText();
        });
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = orderTable.getScene();
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> searchField.requestFocus()
            );
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                this::refreshOrders
            );
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                this::exportToCSV
            );
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem detailsItem = new MenuItem("View Details");
        detailsItem.setOnAction(e -> {
            Order selectedOrder = orderTable.getSelectionModel().getSelectedItem();
            if (selectedOrder != null) {
                showOrderDetails(selectedOrder);
            }
        });

        MenuItem exportItem = new MenuItem("Export to CSV");
        exportItem.setOnAction(e -> exportToCSV());

        contextMenu.getItems().addAll(detailsItem, exportItem);
        orderTable.setContextMenu(contextMenu);
    }

    private void showOrderDetails(Order order) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Order Details - #" + order.getOrderId());

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // Create a table for order items
        TableView<Product> productsTable = new TableView<>();
        
        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(data -> data.getValue().productNameProperty());
        
        TableColumn<Product, Integer> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(data -> data.getValue().quantityProperty().asObject());
        
        TableColumn<Product, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        priceCol.setCellFactory(col -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", price));
                }
            }
        });

        productsTable.getColumns().add(nameCol);
        productsTable.getColumns().add(qtyCol);
        productsTable.getColumns().add(priceCol);
        productsTable.setItems(order.getProducts());

        // Add order information
        content.getChildren().addAll(
            new Label("Order ID: " + order.getOrderId()),
            new Label("Customer: " + order.getCustomerName()),
            new Label("Date: " + dateFormatter.format(order.getOrderDate())),
            new Label("Products:"),
            productsTable,
            new Label(String.format("Total Amount: $%.2f", order.getTotalAmount()))
        );

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> dialog.close());
        content.getChildren().add(closeButton);

        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.show();
    }

    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Orders");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("orders_export.csv");

        File file = fileChooser.showSaveDialog(orderTable.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                writer.write("Order ID,Date,Customer,Total Amount,Products\n");

                // Write data
                for (Order order : sortedData) {
                    StringBuilder products = new StringBuilder();
                    for (Product product : order.getProducts()) {
                        products.append(product.getProductName())
                                .append(" (").append(product.getQuantity()).append(") ");
                    }
                    
                    writer.write(String.format("%d,%s,\"%s\",$%.2f,\"%s\"\n",
                        order.getOrderId(),
                        dateFormatter.format(order.getOrderDate()),
                        order.getCustomerName(),
                        order.getTotalAmount(),
                        products.toString().trim()
                    ));
                }
                
                NotificationUtil.showSuccess("Export Complete", 
                    "Orders have been exported to " + file.getName());
            } catch (IOException e) {
                NotificationUtil.showError("Export Failed", 
                    "Failed to export orders: " + e.getMessage());
            }
        }
    }

    private void refreshOrders() {
        loadData();
        NotificationUtil.showInfo("Refresh Complete", "Orders list has been refreshed");
    }

    private void updatePlaceholderText() {
        if (filteredData.isEmpty()) {
            if (searchField.getText().isEmpty()) {
                orderTable.setPlaceholder(new Label("No orders available"));
            } else {
                orderTable.setPlaceholder(new Label("No orders match your search"));
            }
        }
    }
}
