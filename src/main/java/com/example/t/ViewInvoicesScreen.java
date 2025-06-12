package com.example.t;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.print.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Node;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class ViewInvoicesScreen {
    @FXML private TableView<Invoice> invoiceTable;
    @FXML private TableColumn<Invoice, Integer> colInvoiceId;
    @FXML private TableColumn<Invoice, String> colCustomerName;
    @FXML private TableColumn<Invoice, String> colDate;
    @FXML private TableColumn<Invoice, Double> colTotal;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;
    @FXML private Button printButton;
    @FXML private Button statsButton;
    @FXML private Label messageLabel;
    @FXML private TextField searchField;

    private ObservableList<Invoice> masterData;
    private FilteredList<Invoice> filteredData;
    private SortedList<Invoice> sortedData;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    @FXML
    public void initialize() {
        try {
            setupTableColumns();
            loadData();
            setupSearch();
            setupButtons();
            setupKeyboardShortcuts();
            updatePlaceholderText();
        } catch (Exception e) {
            e.printStackTrace();
            NotificationUtil.showError("Initialization Error", 
                "Failed to initialize invoice screen: " + e.getMessage());
        }
    }

    private void setupTableColumns() {
        try {
            colInvoiceId.setCellValueFactory(cellData -> cellData.getValue().invoiceIdProperty().asObject());
            colCustomerName.setCellValueFactory(cellData -> cellData.getValue().customerNameProperty());
            colDate.setCellValueFactory(cellData -> cellData.getValue().invoiceDateProperty().asString());
            colTotal.setCellValueFactory(cellData -> cellData.getValue().orderTotalProperty().asObject());

            colInvoiceId.setSortType(TableColumn.SortType.DESCENDING);

            // Format currency
            colTotal.setCellFactory(column -> new TableCell<Invoice, Double>() {
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

            // Enable multi-selection
            invoiceTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            // Add double-click handler
            invoiceTable.setRowFactory(tv -> {
                TableRow<Invoice> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty()) {
                        showInvoiceDetails(row.getItem());
                    }
                });
                return row;
            });
        } catch (Exception e) {
            e.printStackTrace();
            NotificationUtil.showError("Setup Error", 
                "Failed to setup table columns: " + e.getMessage());
        }
    }

    private void loadData() {
        try {
            List<Invoice> invoiceList = Invoice.readInvoicesFromFile();
            masterData = FXCollections.observableArrayList(invoiceList);
            filteredData = new FilteredList<>(masterData, p -> true);
            sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(invoiceTable.comparatorProperty());
            invoiceTable.setItems(sortedData);
            invoiceTable.getSortOrder().add(colInvoiceId);
        } catch (Exception e) {
            e.printStackTrace();
            NotificationUtil.showError("Data Load Error", 
                "Failed to load invoice data: " + e.getMessage());
        }
    }

    private void setupButtons() {
        refreshButton.setOnAction(e -> refreshInvoices());
        exportButton.setOnAction(e -> exportToCSV());
        printButton.setOnAction(e -> printSelectedInvoices());
        statsButton.setOnAction(e -> showStatistics());

        // Enable/disable print and export based on selection
        invoiceTable.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<Invoice>) c -> {
                boolean hasSelection = !c.getList().isEmpty();
                printButton.setDisable(!hasSelection);
                exportButton.setDisable(!hasSelection);
            }
        );
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            Scene scene = invoiceTable.getScene();
            scene.setOnKeyPressed(event -> {
                if (event.isControlDown()) {
                    switch (event.getCode()) {
                        case F:
                            searchField.requestFocus();
                            break;
                        case R:
                            refreshInvoices();
                            break;
                        case P:
                            if (!printButton.isDisabled()) {
                                printSelectedInvoices();
                            }
                            break;
                        case E:
                            if (!exportButton.isDisabled()) {
                                exportToCSV();
                            }
                            break;
                        default:
                            break;
                    }
                }
            });
        });
    }

    private void setupSearch() {
        searchField.setPromptText("Search invoices...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(invoice -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                
                String searchText = newValue.toLowerCase();
                return String.valueOf(invoice.getInvoiceId()).contains(searchText) ||
                       invoice.getCustomerName().toLowerCase().contains(searchText) ||
                       invoice.getInvoiceDate().format(dateFormatter).toLowerCase().contains(searchText);
            });
            updatePlaceholderText();
        });
    }

    private void refreshInvoices() {
        loadData();
        NotificationUtil.showSuccess("Refresh Complete", "Invoices have been refreshed");
    }

    private void showInvoiceDetails(Invoice invoice) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice Details - #" + invoice.getInvoiceId());

        VBox content = createInvoiceDetailContent(invoice);
        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.show();
    }

    private VBox createInvoiceDetailContent(Invoice invoice) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white;");

        // Header
        Text headerText = new Text("Invoice #" + invoice.getInvoiceId());
        headerText.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // Invoice info
        GridPane info = new GridPane();
        info.setHgap(20);
        info.setVgap(10);
        info.add(new Text("Customer:"), 0, 0);
        info.add(new Text(invoice.getCustomerName()), 1, 0);
        info.add(new Text("Date:"), 0, 1);
        info.add(new Text(invoice.getInvoiceDate().format(dateFormatter)), 1, 1);

        // Products table
        TableView<Product> productsTable = new TableView<>();
        
        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("productName"));
        
        TableColumn<Product, Integer> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        
        TableColumn<Product, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
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

        productsTable.getColumns().addAll(nameCol, qtyCol, priceCol);
        productsTable.setItems(FXCollections.observableArrayList(invoice.getProducts()));

        // Total
        HBox totalBox = new HBox(10);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        Text totalLabel = new Text("Total Amount:");
        totalLabel.setStyle("-fx-font-weight: bold;");
        Text totalValue = new Text(String.format("$%.2f", invoice.getOrderTotal()));
        totalValue.setStyle("-fx-font-weight: bold;");
        totalBox.getChildren().addAll(totalLabel, totalValue);

        // Actions
        HBox actions = new HBox(10);
        Button printButton = new Button("Print");
        printButton.setOnAction(e -> printInvoice(invoice));
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> ((Stage) content.getScene().getWindow()).close());
        actions.getChildren().addAll(printButton, closeButton);

        content.getChildren().addAll(
            headerText,
            info,
            new Separator(),
            productsTable,
            totalBox,
            new Separator(),
            actions
        );

        return content;
    }

    private void printInvoice(Invoice invoice) {
        invoiceTable.getSelectionModel().clearSelection();
        invoiceTable.getSelectionModel().select(invoice);
        printSelectedInvoices();
    }

    private void printSelectedInvoices() {
        List<Invoice> selectedInvoices = new ArrayList<>(invoiceTable.getSelectionModel().getSelectedItems());
        if (selectedInvoices.isEmpty()) {
            NotificationUtil.showWarning("No Selection", "Please select invoices to print.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(invoiceTable.getScene().getWindow())) {
            boolean success = true;
            Node[] pages = createPrintPages(selectedInvoices);
            
            for (Node page : pages) {
                success = job.printPage(page);
                if (!success) break;
            }
            
            if (success) {
                job.endJob();
                NotificationUtil.showSuccess("Print Complete", 
                    String.format("Successfully printed %d invoices.", selectedInvoices.size()));
            } else {
                NotificationUtil.showError("Print Failed", 
                    "Failed to print invoices. Please check your printer settings.");
            }
        }
    }

    private Node[] createPrintPages(List<Invoice> invoices) {
        return invoices.stream().map(invoice -> {
            VBox page = new VBox(10);
            page.setPadding(new Insets(20));
            page.setPrefWidth(595); // A4 width in points
            
            // Header
            Label headerLabel = new Label("INVOICE");
            headerLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
            
            Label invoiceIdLabel = new Label("Invoice #" + invoice.getInvoiceId());
            invoiceIdLabel.setStyle("-fx-font-size: 18px;");
            
            Label dateLabel = new Label("Date: " + invoice.getInvoiceDate().format(dateFormatter));
            Label customerLabel = new Label("Customer: " + invoice.getCustomerName());
            
            // Products table
            GridPane productsGrid = new GridPane();
            productsGrid.setHgap(20);
            productsGrid.setVgap(5);
            productsGrid.setPadding(new Insets(10));
            productsGrid.setStyle("-fx-border-color: black; -fx-border-width: 1px;");
            
            // Table headers
            Label nameHeader = new Label("Product");
            Label qtyHeader = new Label("Quantity");
            Label priceHeader = new Label("Price");
            Label totalHeader = new Label("Total");
            
            nameHeader.setStyle("-fx-font-weight: bold;");
            qtyHeader.setStyle("-fx-font-weight: bold;");
            priceHeader.setStyle("-fx-font-weight: bold;");
            totalHeader.setStyle("-fx-font-weight: bold;");
            
            productsGrid.add(nameHeader, 0, 0);
            productsGrid.add(qtyHeader, 1, 0);
            productsGrid.add(priceHeader, 2, 0);
            productsGrid.add(totalHeader, 3, 0);
            
            // Product rows
            int row = 1;
            for (Product product : invoice.getProducts()) {
                productsGrid.add(new Label(product.getProductName()), 0, row);
                productsGrid.add(new Label(String.valueOf(product.getQuantity())), 1, row);
                productsGrid.add(new Label(String.format("$%.2f", product.getPrice())), 2, row);
                productsGrid.add(
                    new Label(String.format("$%.2f", product.getPrice() * product.getQuantity())), 
                    3, row
                );
                row++;
            }
            
            // Total
            HBox totalBox = new HBox(10);
            totalBox.setAlignment(Pos.CENTER_RIGHT);
            totalBox.getChildren().addAll(
                new Label("Total Amount: "),
                new Label(String.format("$%.2f", invoice.getOrderTotal()))
            );
            totalBox.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");
            
            // Footer
            Label footerLabel = new Label("Thank you for your business!");
            footerLabel.setStyle("-fx-font-style: italic;");
            
            page.getChildren().addAll(
                headerLabel,
                invoiceIdLabel,
                dateLabel,
                customerLabel,
                new Separator(),
                productsGrid,
                totalBox,
                new Separator(),
                footerLabel
            );
            
            return page;
        }).toArray(Node[]::new);
    }

    private void exportToCSV() {
        List<Invoice> selectedInvoices = new ArrayList<>(invoiceTable.getSelectionModel().getSelectedItems());
        if (selectedInvoices.isEmpty()) {
            NotificationUtil.showWarning("No Selection", "Please select invoices to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Invoices");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("invoices_export_" + 
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv");

        File file = fileChooser.showSaveDialog(invoiceTable.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                writer.write("Invoice ID,Customer Name,Date,Total Amount,Items\n");
                
                // Write data
                for (Invoice invoice : selectedInvoices) {
                    writer.write(String.format("%d,%s,%s,%.2f,\"%s\"\n",
                        invoice.getInvoiceId(),
                        invoice.getCustomerName().replace(",", ";"),
                        invoice.getInvoiceDate().format(dateFormatter),
                        invoice.getOrderTotal(),
                        invoice.getProducts().stream()
                            .<String>map(p -> String.format("%s x%d", p.getProductName(), p.getQuantity()))
                            .collect(Collectors.joining("; "))
                    ));
                }
                NotificationUtil.showSuccess("Export Complete", 
                    String.format("Successfully exported %d invoices to CSV.", selectedInvoices.size()));
            } catch (IOException ex) {
                NotificationUtil.showError("Export Failed", 
                    "Failed to export invoices: " + ex.getMessage());
            }
        }
    }

    private void showStatistics() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice Statistics");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        List<Invoice> invoices = invoiceTable.getItems();
        
        // Calculate statistics
        double totalRevenue = invoices.stream()
            .mapToDouble(Invoice::getOrderTotal)
            .sum();
            
        double averageInvoice = invoices.stream()
            .mapToDouble(Invoice::getOrderTotal)
            .average()
            .orElse(0.0);
            
        Map<String, Long> customerFrequency = invoices.stream()
            .collect(Collectors.groupingBy(
                Invoice::getCustomerName,
                Collectors.counting()
            ));
            
        String topCustomer = customerFrequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("N/A");

        // Create statistics view
        Label titleLabel = new Label("Invoice Statistics");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(10);
        statsGrid.setVgap(5);
        statsGrid.setPadding(new Insets(10));

        statsGrid.addRow(0, new Label("Total Revenue:"), 
            new Label(String.format("$%.2f", totalRevenue)));
        statsGrid.addRow(1, new Label("Average Invoice Amount:"), 
            new Label(String.format("$%.2f", averageInvoice)));
        statsGrid.addRow(2, new Label("Total Invoices:"), 
            new Label(String.valueOf(invoices.size())));
        statsGrid.addRow(3, new Label("Most Frequent Customer:"), 
            new Label(topCustomer));

        // Monthly trend
        Map<String, Double> monthlyRevenue = invoices.stream()
            .collect(Collectors.groupingBy(
                invoice -> invoice.getInvoiceDate().getMonth().toString(),
                Collectors.summingDouble(Invoice::getOrderTotal)
            ));

        VBox trendsBox = new VBox(5);
        trendsBox.getChildren().add(new Label("Monthly Revenue:"));
        monthlyRevenue.forEach((month, revenue) -> 
            trendsBox.getChildren().add(
                new Label(String.format("%s: $%.2f", month, revenue))
            ));

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> dialog.close());
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().add(closeButton);

        content.getChildren().addAll(
            titleLabel,
            new Separator(),
            statsGrid,
            new Separator(),
            trendsBox,
            buttonBox
        );

        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void updatePlaceholderText() {
        if (filteredData.isEmpty()) {
            if (searchField.getText().isEmpty()) {
                invoiceTable.setPlaceholder(new Label("No invoices available"));
            } else {
                invoiceTable.setPlaceholder(new Label("No invoices match your search"));
            }
        }
    }

    @FXML
    private void viewInvoiceDetails() {
        Invoice selected = invoiceTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showInvoiceDetails(selected);
        } else {
            NotificationUtil.showWarning("No Selection", "Please select an invoice to view details.");
        }
    }
}
