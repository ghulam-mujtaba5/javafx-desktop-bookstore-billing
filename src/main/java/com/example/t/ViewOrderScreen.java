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

    @FXML
    public void initialize() {
        // Setup table columns
        setupTableColumns();
        
        // Load initial data
        loadData();
        
        // Setup search functionality
        setupSearch();
        
        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
        
        // Setup refresh button
        refreshButton.setOnAction(e -> refreshOrders());
        
        // Set initial placeholder
        updatePlaceholderText();
    }

    private void setupTableColumns() {
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colCustomerName.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("orderDate"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));

        // Enable sorting for all columns
        colOrderId.setSortType(TableColumn.SortType.DESCENDING);
        
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
    }

    private void loadData() {
        List<Order> orderList = Order.readOrdersFromFile();
        masterData = FXCollections.observableArrayList(orderList);
        filteredData = new FilteredList<>(masterData, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(orderTable.comparatorProperty());
        orderTable.setItems(sortedData);
        
        // Default sort by Order ID descending
        orderTable.getSortOrder().add(colOrderId);
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(order -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String searchText = newValue.toLowerCase();
                return String.valueOf(order.getOrderId()).contains(searchText) ||
                       order.getCustomerName().toLowerCase().contains(searchText);
            });
            updatePlaceholderText();
        });

        // Add a clear button to the search field
        searchField.setTooltip(new Tooltip("Press Esc to clear search"));
    }

    private void setupKeyboardShortcuts() {
        orderTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F5) {
                refreshOrders();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
            }
        });

        // Set focus traversal
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                orderTable.requestFocus();
                if (!orderTable.getItems().isEmpty()) {
                    orderTable.getSelectionModel().select(0);
                }
            }
        });

        // Double-click to view order details
        orderTable.setRowFactory(tv -> {
            TableRow<Order> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewOrderDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void viewOrderDetails(Order order) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Order Details - #" + order.getOrderId());
        dialog.setHeaderText("Customer: " + order.getCustomerName());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("modern-dialog");

        // Create content
        TableView<Product> productsTable = new TableView<>();
        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        TableColumn<Product, Integer> qtyCol = new TableColumn<>("Quantity");
        TableColumn<Product, Double> priceCol = new TableColumn<>("Price");
        
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        
        productsTable.getColumns().addAll(nameCol, qtyCol, priceCol);
        productsTable.setItems(FXCollections.observableArrayList(order.getProducts()));

        dialogPane.setContent(productsTable);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        dialog.show();
    }

    private void refreshOrders() {
        List<Order> orderList = Order.readOrdersFromFile();
        masterData.setAll(orderList);
        updatePlaceholderText();
        messageLabel.setText("Order list refreshed successfully");
    }

    private void updatePlaceholderText() {
        Platform.runLater(() -> {
            if (filteredData.isEmpty()) {
                if (!searchField.getText().isEmpty()) {
                    orderTable.setPlaceholder(new Label("No orders found matching your search"));
                } else {
                    orderTable.setPlaceholder(new Label("No orders found. Create a new order to get started!"));
                }
            }
        });
    }
}
