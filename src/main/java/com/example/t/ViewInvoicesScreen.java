package com.example.t;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ViewInvoicesScreen {

    private TableView<Invoice> invoiceTable;
    private ObservableList<Invoice> invoices;

    public void show() {
        Stage primaryStage = new Stage();
        primaryStage.setTitle("Invoice History");

        invoiceTable = new TableView<>();
        invoiceTable.setPrefHeight(400);

        TableColumn<Invoice, Integer> invoiceIdColumn = new TableColumn<>("Invoice ID");
        invoiceIdColumn.setCellValueFactory(new PropertyValueFactory<>("invoiceId"));

        TableColumn<Invoice, Integer> orderNumColumn = new TableColumn<>("Order Number");
        orderNumColumn.setCellValueFactory(new PropertyValueFactory<>("orderNum"));

        TableColumn<Invoice, String> customerNameColumn = new TableColumn<>("Customer Name");
        customerNameColumn.setCellValueFactory(new PropertyValueFactory<>("customerName"));

        TableColumn<Invoice, LocalDate> invoiceDateColumn = new TableColumn<>("Invoice Date");
        invoiceDateColumn.setCellValueFactory(new PropertyValueFactory<>("invoiceDate"));

        TableColumn<Invoice, Double> orderTotalColumn = new TableColumn<>("Order Total");
        orderTotalColumn.setCellValueFactory(new PropertyValueFactory<>("orderTotal"));

        // Suppress type safety warning for varargs TableColumn
        @SuppressWarnings("unchecked")
        TableColumn<Invoice, ?>[] columns = new TableColumn[] {invoiceIdColumn, orderNumColumn, customerNameColumn, invoiceDateColumn, orderTotalColumn};
        invoiceTable.getColumns().addAll(columns);

        // Enable sorting by clicking on column headers
        invoiceTable.getSortOrder().add(invoiceIdColumn);
        invoiceTable.getSortOrder().add(orderNumColumn);
        invoiceTable.getSortOrder().add(customerNameColumn);
        invoiceTable.getSortOrder().add(invoiceDateColumn);
        invoiceTable.getSortOrder().add(orderTotalColumn);
        invoiceTable.setSortPolicy(param -> true);

        // Set a selection listener to open the selected invoice details
        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                openInvoiceDetails(newSelection);
            }
        });

        VBox vbox = new VBox();
        vbox.setPadding(new Insets(10));
        vbox.setSpacing(10);

        // Create a HBox to hold the search bars
        HBox searchBarsBox = new HBox();
        searchBarsBox.setSpacing(10);

        TextField invoiceIdSearchField = new TextField();
        invoiceIdSearchField.setPromptText("Search Invoice ID...");
        invoiceIdSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterInvoicesByInvoiceId(newValue);
        });

        TextField orderNumSearchField = new TextField();
        orderNumSearchField.setPromptText("Search Order Number...");
        orderNumSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterInvoicesByOrderNum(newValue);
        });

        TextField customerNameSearchField = new TextField();
        customerNameSearchField.setPromptText("Search Customer Name...");
        customerNameSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterInvoicesByCustomerName(newValue);
        });

        DatePicker invoiceDateDatePicker = new DatePicker();
        invoiceDateDatePicker.setPromptText("Select Invoice Date");
        invoiceDateDatePicker.setOnAction(event -> {
            LocalDate selectedDate = invoiceDateDatePicker.getValue();
            filterInvoicesByInvoiceDate(selectedDate);
        });

        TextField orderTotalSearchField = new TextField();
        orderTotalSearchField.setPromptText("Search Order Total...");
        orderTotalSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterInvoicesByOrderTotal(newValue);
        });

        searchBarsBox.getChildren().addAll(
                invoiceIdSearchField,
                orderNumSearchField,
                customerNameSearchField,
                invoiceDateDatePicker,
                orderTotalSearchField
        );

        vbox.getChildren().add(searchBarsBox);
        vbox.getChildren().add(invoiceTable);

        Scene scene = new Scene(vbox, 600, 500);
        scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        loadInvoices(); // Load the invoices when the stage is shown
    }

    private void loadInvoices() {
        try {
            invoices = readInvoiceHistory();
            if (invoices != null) {
                invoiceTable.setItems(invoices);
            } else {
                showAlert("Error", "Failed to load invoices. Please check the invoice history file.");
            }
        } catch (IOException e) {
            showAlert("Error", "An error occurred while loading the invoices: " + e.getMessage());
        }
    }

    private ObservableList<Invoice> readInvoiceHistory() throws IOException {
        List<Invoice> invoices = new ArrayList<>();

        // Use the FilePathManager to get the path of the invoice history file
        String invoiceHistoryFilePath = FilePathManager.getInvoiceHistoryFilePath();
        Path invoiceHistoryPath = Paths.get(invoiceHistoryFilePath);

        if (!Files.exists(invoiceHistoryPath)) {
            showAlert("Error", "Invoice history file not found.");
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(invoiceHistoryPath.toFile()))) {
            String line;
            String invoiceId = "";
            String invoiceDate = "";
            String customerName = "";
            List<Product> products = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Invoice ID: ")) {
                    invoiceId = line.substring(line.indexOf(":") + 1).trim();
                } else if (line.startsWith("Order Date: ")) {
                    invoiceDate = line.substring(line.indexOf(":") + 1).trim();
                } else if (line.startsWith("Customer Name: ")) {
                    customerName = line.substring(line.indexOf(":") + 1).trim();
                } else if (line.startsWith("Total Amount: ")) {
                    String totalAmountString = line.substring(line.indexOf(":") + 1).trim();
                    double totalAmount = parseTotalAmount(totalAmountString);

                    Invoice invoice = new Invoice(
                            Integer.parseInt(invoiceId),
                            Integer.parseInt(invoiceId), // Assuming order number is same as invoice ID
                            customerName,
                            LocalDate.parse(invoiceDate),
                            products,
                            totalAmount,
                            0.0,
                            0.0,
                            0.0
                    );
                    invoices.add(invoice);

                    // Reset values for the next invoice
                    invoiceId = "";
                    invoiceDate = "";
                    customerName = "";
                    products = new ArrayList<>();
                } else if (line.startsWith("Product Name")) {
                    // Skip the order details header line
                    reader.readLine();
                } else if (!line.isEmpty()) {
                    String[] parts = line.split("\t");
                    if (parts.length == 4) {
                        String productName = parts[0].trim();
                        int quantity = Integer.parseInt(parts[1].trim());
                        double price = Double.parseDouble(parts[2].trim());
                        double discount = Double.parseDouble(parts[3].trim());
                        Product product = new Product(
                                0, // Set dummy values for the product ID, as it's not provided in the file
                                productName,
                                quantity,
                                price,
                                discount
                        );
                        products.add(product);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "An error occurred while reading the invoice history file.");
            return null;
        }

        return FXCollections.observableArrayList(invoices);
    }

    private double parseTotalAmount(String amountString) {
        String cleanAmountString = amountString.replaceAll("[^\\d.]", ""); // Remove non-numeric characters
        try {
            return Double.parseDouble(cleanAmountString);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid total amount value: " + amountString);
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void openInvoiceDetails(Invoice invoice) {
        Stage invoiceDetailsStage = new Stage();
        invoiceDetailsStage.setTitle("Invoice Details");

        // Create a GridPane to hold the invoice details
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10);
        gridPane.setVgap(10);

        // Create labels to display the invoice details
        Label invoiceIdLabel = new Label("Invoice ID:");
        Label orderNumLabel = new Label("Order Number:");
        Label customerNameLabel = new Label("Customer Name:");
        Label invoiceDateLabel = new Label("Invoice Date:");
        Label orderTotalLabel = new Label("Order Total:");

        // Create labels to display the values of the invoice details
        Label invoiceIdValueLabel = new Label(String.valueOf(invoice.getInvoiceId()));
        Label orderNumValueLabel = new Label(String.valueOf(invoice.getOrderNum()));
        Label customerNameValueLabel = new Label(invoice.getCustomerName());
        Label invoiceDateValueLabel = new Label(invoice.getInvoiceDate().toString());
        Label orderTotalValueLabel = new Label(String.valueOf(invoice.getOrderTotal()));

        // Set label styles
        invoiceIdLabel.getStyleClass().add("label");
        orderNumLabel.getStyleClass().add("label");
        customerNameLabel.getStyleClass().add("label");
        invoiceDateLabel.getStyleClass().add("label");
        orderTotalLabel.getStyleClass().add("label");

        // Add labels to the gridPane
        gridPane.addRow(0, invoiceIdLabel, invoiceIdValueLabel);
        gridPane.addRow(1, orderNumLabel, orderNumValueLabel);
        gridPane.addRow(2, customerNameLabel, customerNameValueLabel);
        gridPane.addRow(3, invoiceDateLabel, invoiceDateValueLabel);
        gridPane.addRow(4, orderTotalLabel, orderTotalValueLabel);

        // Set column constraints to align labels to the right
        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setHalignment(HPos.RIGHT);
        gridPane.getColumnConstraints().add(columnConstraints);

        // Create a button to close the invoice details window
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> invoiceDetailsStage.close());

        // Add the close button to the gridPane
        gridPane.add(closeButton, 0, 5, 2, 1);
        GridPane.setHalignment(closeButton, HPos.CENTER);

//        // Set the icon for the invoice details window using the FilePathManager
//        String invoiceDetailsIconPath = FilePathManager.getInvoiceDetailsIconPath();
//        Image iconImage = new Image(invoiceDetailsIconPath);
//        invoiceDetailsStage.getIcons().add(iconImage);

        // Create a scene with the gridPane and set it on the invoice details stage
        Scene invoiceDetailsScene = new Scene(gridPane, 400, 300);
        invoiceDetailsScene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
        invoiceDetailsStage.setScene(invoiceDetailsScene);

        // Show the invoice details stage
        invoiceDetailsStage.show();
    }

    private void filterInvoicesByInvoiceId(String searchText) {
        Predicate<Invoice> invoiceIdPredicate = invoice -> String.valueOf(invoice.getInvoiceId()).contains(searchText);
        invoiceTable.setItems(invoices.filtered(invoiceIdPredicate));
    }

    private void filterInvoicesByOrderNum(String searchText) {
        Predicate<Invoice> orderNumPredicate = invoice -> String.valueOf(invoice.getOrderNum()).contains(searchText);
        invoiceTable.setItems(invoices.filtered(orderNumPredicate));
    }

    private void filterInvoicesByCustomerName(String searchText) {
        Predicate<Invoice> customerNamePredicate = invoice -> invoice.getCustomerName().toLowerCase().contains(searchText.toLowerCase());
        invoiceTable.setItems(invoices.filtered(customerNamePredicate));
    }

    private void filterInvoicesByInvoiceDate(LocalDate selectedDate) {
        Predicate<Invoice> invoiceDatePredicate = invoice -> invoice.getInvoiceDate().isEqual(selectedDate);
        invoiceTable.setItems(invoices.filtered(invoiceDatePredicate));
    }

    private void filterInvoicesByOrderTotal(String searchText) {
        Predicate<Invoice> orderTotalPredicate = invoice -> String.valueOf(invoice.getOrderTotal()).contains(searchText);
        invoiceTable.setItems(invoices.filtered(orderTotalPredicate));
    }
}
