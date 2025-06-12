package com.example.t;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.fxml.FXML;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
        setupSearch();
        setupButtons();
        setupKeyboardShortcuts();
        updatePlaceholderText();
    }

    private void setupTableColumns() {
        colInvoiceId.setCellValueFactory(new PropertyValueFactory<>("invoiceId"));
        colCustomerName.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("invoiceDate"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("orderTotal"));

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

        // Make table multi-selectable
        invoiceTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void loadData() {
        List<Invoice> invoiceList = Invoice.readInvoicesFromFile();
        masterData = FXCollections.observableArrayList(invoiceList);
        filteredData = new FilteredList<>(masterData, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(invoiceTable.comparatorProperty());
        invoiceTable.setItems(sortedData);
        
        invoiceTable.getSortOrder().add(colInvoiceId);
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(invoice -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }                String searchText = newValue.toLowerCase();
                return String.valueOf(invoice.getInvoiceId()).contains(searchText) ||
                       invoice.getCustomerName().toLowerCase().contains(searchText) ||
                       invoice.getInvoiceDate().toString().toLowerCase().contains(searchText);
            });
            updatePlaceholderText();
        });

        searchField.setTooltip(new Tooltip("Press Esc to clear search\nEnter text to filter by ID, Customer, or Date"));

        // Add context menu for search options
        ContextMenu searchMenu = new ContextMenu();
        searchMenu.getItems().addAll(
            createSearchMenuItem("Today's Invoices", () -> searchField.setText(LocalDate.now().format(dateFormatter))),
            createSearchMenuItem("High Value (>$1000)", () -> filterByAmount(1000)),
            createSearchMenuItem("Clear Filter", () -> searchField.clear())
        );
        searchField.setContextMenu(searchMenu);
    }

    private MenuItem createSearchMenuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private void filterByAmount(double threshold) {
        filteredData.setPredicate(invoice -> invoice.getOrderTotal() > threshold);
        updatePlaceholderText();
    }

    private void setupButtons() {
        refreshButton.setOnAction(e -> refreshInvoices());
        exportButton.setOnAction(e -> showExportMenu());
        printButton.setOnAction(e -> printSelectedInvoices());
        statsButton.setOnAction(e -> showStatistics());

        // Context menu for export button
        ContextMenu exportMenu = new ContextMenu();
        exportMenu.getItems().addAll(
            createMenuItem("Export Selected to CSV", () -> exportSelectedInvoicesToCSV()),
            createMenuItem("Export All to CSV", () -> exportInvoicesToCSV()),
            new SeparatorMenuItem(),
            createMenuItem("Export Selected to PDF", () -> exportSelectedInvoicesToPDF()),
            createMenuItem("Export All to PDF", () -> exportAllInvoicesToPDF())
        );
        exportButton.setContextMenu(exportMenu);

        // Double-click to view/print invoice
        invoiceTable.setRowFactory(tv -> {
            TableRow<Invoice> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewInvoiceDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private MenuItem createMenuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private void setupKeyboardShortcuts() {
        invoiceTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F5) {
                refreshInvoices();
            } else if (event.getCode() == KeyCode.P && event.isControlDown()) {
                if (event.isShiftDown()) {
                    printSelectedInvoices();
                } else {
                    printSelectedInvoice();
                }
            } else if (event.getCode() == KeyCode.S && event.isControlDown()) {
                showStatistics();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
            } else if (event.getCode() == KeyCode.A && event.isControlDown()) {
                invoiceTable.getSelectionModel().selectAll();
            }
        });

        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                invoiceTable.requestFocus();
                if (!invoiceTable.getItems().isEmpty()) {
                    invoiceTable.getSelectionModel().select(0);
                }
            }
        });
    }

    private void viewInvoiceDetails(Invoice invoice) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Invoice Details - #" + invoice.getInvoiceId());
        dialog.setHeaderText("Customer: " + invoice.getCustomerName());

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
            createHeaderText("Invoice #" + invoice.getInvoiceId()),
            createInfoText("Date: " + invoice.getInvoiceDate()),
            createInfoText("Customer: " + invoice.getCustomerName()),
            createInfoText("Total Amount: $" + String.format("%.2f", invoice.getOrderTotal()))
        );

        // Add products table if available
        if (invoice.getProducts() != null && !invoice.getProducts().isEmpty()) {
            TableView<Product> productsTable = new TableView<>();
            TableColumn<Product, String> nameCol = new TableColumn<>("Product");
            TableColumn<Product, Integer> qtyCol = new TableColumn<>("Quantity");
            TableColumn<Product, Double> priceCol = new TableColumn<>("Price");
            
            nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
            qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));            productsTable.getColumns().addAll(nameCol, qtyCol, priceCol);
            
            productsTable.setItems(FXCollections.observableArrayList(invoice.getProducts()));
            content.getChildren().add(productsTable);
        }

        ButtonType printButton = new ButtonType("Print", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(printButton, ButtonType.CLOSE);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("invoice-dialog");

        dialog.setResultConverter(buttonType -> {
            if (buttonType == printButton) {
                printInvoice(invoice);
            }
            return null;
        });

        dialog.show();
    }

    private void printSelectedInvoices() {
        ObservableList<Invoice> selectedInvoices = invoiceTable.getSelectionModel().getSelectedItems();
        if (selectedInvoices.isEmpty()) {
            messageLabel.setText("Please select invoices to print");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(invoiceTable.getScene().getWindow())) {
            int successCount = 0;
            for (Invoice invoice : selectedInvoices) {
                VBox content = createPrintableInvoice(invoice);
                if (job.printPage(content)) {
                    successCount++;
                }
            }
            job.endJob();
            messageLabel.setText(String.format("Printed %d of %d invoices successfully", 
                successCount, selectedInvoices.size()));
        }
    }

    private VBox createPrintableInvoice(Invoice invoice) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
            createHeaderText("INVOICE #" + invoice.getInvoiceId()),
            createInfoText("Date: " + invoice.getInvoiceDate()),
            createInfoText("Customer: " + invoice.getCustomerName()),
            createInfoText("Total Amount: $" + String.format("%.2f", invoice.getOrderTotal()))
        );
        return content;
    }

    private void showExportMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
            createMenuItem("Export Selected to CSV", () -> exportSelectedInvoicesToCSV()),
            createMenuItem("Export All to CSV", () -> exportInvoicesToCSV()),
            new SeparatorMenuItem(),
            createMenuItem("Export Selected to PDF", () -> exportSelectedInvoicesToPDF()),
            createMenuItem("Export All to PDF", () -> exportAllInvoicesToPDF())
        );
        menu.show(exportButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void exportSelectedInvoicesToCSV() {
        ObservableList<Invoice> selectedInvoices = invoiceTable.getSelectionModel().getSelectedItems();
        if (selectedInvoices.isEmpty()) {
            messageLabel.setText("Please select invoices to export");
            return;
        }
        exportToCSV(selectedInvoices);
    }

    private void exportAllInvoicesToPDF() {
        messageLabel.setText("PDF export not implemented yet");
    }

    private void exportSelectedInvoicesToPDF() {
        messageLabel.setText("PDF export not implemented yet");
    }

    private void showStatistics() {
        // Calculate statistics
        DoubleSummaryStatistics stats = masterData.stream()
            .mapToDouble(Invoice::getOrderTotal)
            .summaryStatistics();        // Group by date
        Map<String, Double> dailyStats = masterData.stream()
            .collect(Collectors.groupingBy(
                invoice -> invoice.getInvoiceDate().toString(),
                Collectors.summingDouble(Invoice::getOrderTotal)
            ));

        // Create dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Invoice Statistics");
        dialog.setHeaderText("Sales Overview");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Overall stats
        content.getChildren().addAll(
            createHeaderText("Overall Statistics"),
            createInfoText(String.format("Total Sales: $%.2f", stats.getSum())),
            createInfoText(String.format("Average Sale: $%.2f", stats.getAverage())),
            createInfoText(String.format("Highest Sale: $%.2f", stats.getMax())),
            createInfoText(String.format("Lowest Sale: $%.2f", stats.getMin())),
            createInfoText(String.format("Total Invoices: %d", stats.getCount()))
        );

        // Daily stats in a table
        TableView<Map.Entry<String, Double>> dailyTable = new TableView<>();
        
        TableColumn<Map.Entry<String, Double>, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getKey()));
        
        TableColumn<Map.Entry<String, Double>, Number> totalCol = new TableColumn<>("Total Sales");
        totalCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getValue()));
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", value.doubleValue()));
                }
            }
        });        dailyTable.getColumns().addAll(dateCol, totalCol);
        
        dailyTable.setItems(FXCollections.observableArrayList(dailyStats.entrySet()));
        content.getChildren().add(dailyTable);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setContent(content);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.getStyleClass().add("statistics-dialog");

        dialog.show();
    }

    private Text createHeaderText(String text) {
        Text t = new Text(text);
        t.getStyleClass().add("header-text");
        return t;
    }

    private Text createInfoText(String text) {
        Text t = new Text(text);
        t.getStyleClass().add("info-text");
        return t;
    }

    private void refreshInvoices() {
        List<Invoice> invoiceList = Invoice.readInvoicesFromFile();
        masterData.setAll(invoiceList);
        updatePlaceholderText();
        messageLabel.setText("Invoice list refreshed successfully");
    }

    private void exportInvoicesToCSV() {
        exportToCSV(masterData);
    }

    private void exportToCSV(List<Invoice> invoices) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("invoices_export.csv");

        File file = fileChooser.showSaveDialog(invoiceTable.getScene().getWindow());
        
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Invoice ID,Customer Name,Date,Total Amount\n");
                for (Invoice invoice : invoices) {
                    writer.write(String.format("%d,%s,%s,%.2f\n",
                        invoice.getInvoiceId(),
                        invoice.getCustomerName().replace(",", ";"),
                        invoice.getInvoiceDate(),
                        invoice.getOrderTotal()
                    ));
                }
                messageLabel.setText("Invoices exported successfully to " + file.getName());
            } catch (IOException e) {
                messageLabel.setText("Error exporting invoices: " + e.getMessage());
            }
        }
    }

    private void updatePlaceholderText() {
        Platform.runLater(() -> {
            if (filteredData.isEmpty()) {
                if (!searchField.getText().isEmpty()) {
                    invoiceTable.setPlaceholder(new Label("No invoices found matching your search"));
                } else {
                    invoiceTable.setPlaceholder(new Label("No invoices found. Generate an invoice to see it here!"));
                }
            }
        });
    }

    private void printSelectedInvoice() {
        Invoice selected = invoiceTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            printInvoice(selected);
        } else {
            messageLabel.setText("Please select an invoice to print");
        }
    }

    private void printInvoice(Invoice invoice) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            boolean proceeded = job.showPrintDialog(invoiceTable.getScene().getWindow());
            if (proceeded) {
                VBox content = createPrintableInvoice(invoice);
                boolean printed = job.printPage(content);
                if (printed) {
                    job.endJob();
                    messageLabel.setText("Invoice printed successfully");
                } else {
                    messageLabel.setText("Printing failed");
                }
            }
        }
    }
}
