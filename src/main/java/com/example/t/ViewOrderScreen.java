package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.application.Platform;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;  // Added missing import
import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

public class ViewOrderScreen {
    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, Integer> colOrderId;
    @FXML private TableColumn<Order, String> colCustomerName;
    @FXML private TableColumn<Order, String> colDate;
    @FXML private TableColumn<Order, Double> colTotal;
    @FXML private Button refreshButton;
    @FXML private Button batchOperationsButton;
    @FXML private Button printSelectedButton;
    @FXML private Button statisticsButton;
    @FXML private Label messageLabel;
    @FXML private TextField searchField;

    private ObservableList<Order> masterData;
    private FilteredList<Order> filteredData;
    private SortedList<Order> sortedData;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    // Product analytics class
    private static final class ProductAnalytics {
        private final String name;
        private double revenue;

        public ProductAnalytics(String name) {
            this.name = name;
            this.revenue = 0;
        }

        public void addProduct(Product product) {
            revenue += product.getPrice() * product.getQuantity();
        }

        public String getName() { return name; }
        public double getRevenue() { return revenue; }
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
        setupSearch();
        setupKeyboardShortcuts();
        setupContextMenu();
        setupBatchOperations();
        setupPrintingAndStatistics();
        refreshButton.setOnAction(e -> refreshOrders());
        updatePlaceholderText();
        
        // Enable multiple selection
        orderTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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

    private void setupBatchOperations() {
        batchOperationsButton.setOnAction(e -> showBatchOperationsMenu());
    }

    private void showBatchOperationsMenu() {
        ContextMenu menu = new ContextMenu();
        
        MenuItem printSelected = new MenuItem("Print Selected Orders");
        printSelected.setOnAction(e -> printSelectedOrders());
        printSelected.setDisable(orderTable.getSelectionModel().getSelectedItems().isEmpty());

        MenuItem exportSelected = new MenuItem("Export Selected to CSV");
        exportSelected.setOnAction(e -> exportSelectedToCSV());
        exportSelected.setDisable(orderTable.getSelectionModel().getSelectedItems().isEmpty());

        MenuItem generateReport = new MenuItem("Generate Summary Report");
        generateReport.setOnAction(e -> generateSummaryReport());

        menu.getItems().addAll(printSelected, exportSelected, generateReport);
        menu.show(batchOperationsButton, Side.BOTTOM, 0, 0);
    }

    private void showStatisticsDialog() {
        showStatisticsDialog(masterData);
    }

    private void showStatisticsDialog(List<Order> orders) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Order Statistics");
        
        TabPane tabPane = new TabPane();
        
        // Summary Tab
        Tab summaryTab = new Tab("Summary");
        VBox summaryContent = new VBox(10);
        summaryContent.setPadding(new Insets(15));
        
        double totalRevenue = orders.stream()
            .mapToDouble(Order::getTotalAmount)
            .sum();
            
        int totalOrders = orders.size();
        double avgOrderValue = totalRevenue / totalOrders;
        
        Map<String, Long> customerFrequency = orders.stream()
            .collect(Collectors.groupingBy(
                Order::getCustomerName,
                Collectors.counting()
            ));
        String topCustomer = customerFrequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("N/A");
            
        VBox statsBox = new VBox(10);
        statsBox.getChildren().addAll(
            new Label(String.format("Total Revenue: $%.2f", totalRevenue)),
            new Label(String.format("Total Orders: %d", totalOrders)),
            new Label(String.format("Average Order Value: $%.2f", avgOrderValue)),
            new Label("Top Customer: " + topCustomer)
        );
        
        // Revenue by Day Chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> revenueChart = new BarChart<>(xAxis, yAxis);
        revenueChart.setTitle("Revenue by Day");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<LocalDate, Double> dailyRevenue = orders.stream()
            .collect(Collectors.groupingBy(
                Order::getOrderDate,
                Collectors.summingDouble(Order::getTotalAmount)
            ));
            
        dailyRevenue.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> 
                series.getData().add(
                    new XYChart.Data<>(

                        entry.getKey().format(dateFormatter),
                        entry.getValue()
                    )
                )
            );
        
        revenueChart.getData().add(series);
        revenueChart.setMaxHeight(300);
        
        summaryContent.getChildren().addAll(statsBox, revenueChart);
        summaryTab.setContent(summaryContent);
        
        // Product Analysis Tab
        Tab productsTab = new Tab("Product Analysis");
        VBox productsContent = new VBox(10);
        productsContent.setPadding(new Insets(15));
        
        // Get product statistics
        Map<String, Integer> productQuantities = new HashMap<>();
        Map<String, Double> productRevenue = new HashMap<>();
        
        orders.stream()
            .flatMap(order -> order.getProducts().stream())
            .forEach(product -> {
                String name = product.getProductName();
                productQuantities.merge(name, product.getQuantity(), Integer::sum);
                productRevenue.merge(
                    name,
                    product.getPrice() * product.getQuantity(),
                    Double::sum
                );
            });
            
        // Create product revenue pie chart
        PieChart productRevenueChart = new PieChart();
        productRevenueChart.setTitle("Revenue by Product");
        
        productRevenue.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(5) // Top 5 products
            .forEach(entry -> 
                productRevenueChart.getData().add(
                    new PieChart.Data(
                        entry.getKey(),
                        entry.getValue()
                    )
                )
            );
        
        productsContent.getChildren().add(productRevenueChart);
        productsTab.setContent(productsContent);
        
        // Add tabs
        tabPane.getTabs().addAll(summaryTab, productsTab);
        
        Scene scene = new Scene(tabPane, 800, 600);
        dialog.setScene(scene);
        dialog.show();
    }

    private void exportSelectedToCSV() {
        List<Order> selectedOrders = new ArrayList<>(orderTable.getSelectionModel().getSelectedItems());
        if (selectedOrders.isEmpty()) {
            NotificationUtil.showWarning("No Orders Selected", "Please select orders to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Orders to CSV");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("orders_export_" + 
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv");

        File file = fileChooser.showSaveDialog(orderTable.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                writer.write("Order ID,Customer Name,Date,Total Amount,Products\n");
                
                // Write data
                for (Order order : selectedOrders) {
                    writer.write(String.format("%d,%s,%s,%.2f,\"%s\"\n",
                        order.getOrderId(),
                        order.getCustomerName().replace(",", ";"),
                        order.getOrderDate().format(dateFormatter),
                        order.getTotalAmount(),
                        order.getProducts().stream()
                            .<String>map(p -> String.format("%s x%d", p.getProductName(), p.getQuantity()))
                            .collect(Collectors.joining("; "))
                    ));
                }
                NotificationUtil.showSuccess("Export Complete", 
                    String.format("Successfully exported %d orders to CSV.", selectedOrders.size()));
            } catch (IOException ex) {
                NotificationUtil.showError("Export Failed", 
                    "Failed to export orders: " + ex.getMessage());
            }
        }
    }

    private void generateSummaryReport() {
        List<Order> orders = new ArrayList<>(orderTable.getItems());
        if (orders.isEmpty()) {
            NotificationUtil.showWarning("No Data", "There are no orders to generate a report from.");
            return;
        }

        Stage reportStage = new Stage();
        reportStage.initModality(Modality.APPLICATION_MODAL);
        reportStage.setTitle("Sales Summary Report");

        VBox reportContent = new VBox(10);
        reportContent.setPadding(new Insets(20));

        // Add summary statistics
        Label titleLabel = new Label("Sales Summary Report");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        LocalDate now = LocalDate.now();
        Label dateLabel = new Label("Generated on: " + now.format(dateFormatter));

        // Basic statistics
        double totalRevenue = orders.stream()
            .mapToDouble(Order::getTotalAmount)
            .sum();
        int totalOrders = orders.size();
        double averageOrderValue = totalRevenue / totalOrders;

        VBox statsBox = new VBox(5);
        statsBox.getChildren().addAll(
            new Label(String.format("Total Revenue: $%.2f", totalRevenue)),
            new Label(String.format("Total Orders: %d", totalOrders)),
            new Label(String.format("Average Order Value: $%.2f", averageOrderValue))
        );

        // Add charts
        LineChart<String, Number> revenueChart = createRevenueChart(orders);
        revenueChart.setTitle("Revenue Trend");
        VBox.setVgrow(revenueChart, Priority.ALWAYS);

        PieChart productChart = createProductDistributionChart(orders);
        productChart.setTitle("Product Distribution");
        VBox.setVgrow(productChart, Priority.ALWAYS);

        // Add export and print buttons
        Button exportButton = new Button("Export Report");
        exportButton.setOnAction(e -> exportReport(reportContent));
        
        Button printButton = new Button("Print Report");
        printButton.setOnAction(e -> printReport(reportContent));

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(exportButton, printButton);

        reportContent.getChildren().addAll(
            titleLabel, dateLabel, new Separator(),
            statsBox, new Separator(),
            revenueChart, productChart,
            buttonBox
        );

        Scene scene = new Scene(reportContent, 800, 900);
        reportStage.setScene(scene);
        reportStage.show();
    }

    private LineChart<String, Number> createRevenueChart(List<Order> orders) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);

        xAxis.setLabel("Date");
        yAxis.setLabel("Revenue ($)");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Revenue");

        Map<LocalDate, Double> dailyRevenue = orders.stream()
            .collect(Collectors.groupingBy(
                Order::getOrderDate,
                Collectors.summingDouble(Order::getTotalAmount)
            ));

        dailyRevenue.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> series.getData().add(
                new XYChart.Data<>(entry.getKey().format(dateFormatter), entry.getValue())
            ));

        lineChart.getData().add(series);
        return lineChart;
    }

    private PieChart createProductDistributionChart(List<Order> orders) {
        Map<String, Integer> productQuantities = new HashMap<>();
        
        orders.stream()
            .flatMap(order -> order.getProducts().stream())
            .forEach(product -> {
                productQuantities.merge(product.getProductName(), product.getQuantity(), Integer::sum);
            });

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        productQuantities.forEach((name, quantity) -> 
            pieChartData.add(new PieChart.Data(name + " (" + quantity + ")", quantity))
        );

        PieChart chart = new PieChart(pieChartData);
        chart.setLegendVisible(true);
        chart.setLabelsVisible(true);
        
        return chart;
    }

    private void setupPrintingAndStatistics() {
        statisticsButton.setOnAction(e -> {
            List<Order> selectedOrders = orderTable.getSelectionModel().getSelectedItems();
            if (selectedOrders.isEmpty()) {
                selectedOrders = new ArrayList<>(orderTable.getItems());
            }
            showAdvancedStatisticsDialog(selectedOrders);
        });

        // Set up print button
        printSelectedButton.setOnAction(e -> printSelectedOrders());
    }

    private void printSelectedOrders() {
        List<Order> selectedOrders = new ArrayList<>(orderTable.getSelectionModel().getSelectedItems());
        if (selectedOrders.isEmpty()) {
            NotificationUtil.showWarning("No Orders Selected", "Please select orders to print.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(orderTable.getScene().getWindow())) {
            boolean success = true;
            Node[] pages = createPrintPages(selectedOrders);
            
            for (Node page : pages) {
                success = job.printPage(page);
                if (!success) break;
            }
            
            if (success) {
                job.endJob();
                NotificationUtil.showSuccess("Print Complete", 
                    String.format("Successfully printed %d orders.", selectedOrders.size()));
            } else {
                NotificationUtil.showError("Print Failed", 
                    "Failed to print orders. Please check your printer settings.");
            }
        }
    }

    private Node[] createPrintPages(List<Order> orders) {
        return orders.stream().map(order -> {
            VBox page = new VBox(10);
            page.setPadding(new Insets(20));
            page.setPrefWidth(595); // A4 width in points
            
            // Header
            Label headerLabel = new Label("Order Details");
            headerLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
            
            Label orderIdLabel = new Label("Order #" + order.getOrderId());
            orderIdLabel.setStyle("-fx-font-size: 18px;");
            
            Label dateLabel = new Label("Date: " + order.getOrderDate().format(dateFormatter));
            Label customerLabel = new Label("Customer: " + order.getCustomerName());
            
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
            for (Product product : order.getProducts()) {
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
                new Label(String.format("$%.2f", order.getTotalAmount()))
            );
            totalBox.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");
            
            // Footer
            Label footerLabel = new Label("Thank you for your business!");
            footerLabel.setStyle("-fx-font-style: italic;");
            
            page.getChildren().addAll(
                headerLabel,
                orderIdLabel,
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

    private void exportReport(Node reportContent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Report");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("sales_report_" + 
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf");

        File file = fileChooser.showSaveDialog(orderTable.getScene().getWindow());
        if (file != null) {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job != null) {
                boolean success = job.printPage(reportContent);
                if (success) {
                    job.endJob();
                    NotificationUtil.showSuccess("Export Complete", 
                        "Successfully exported report to PDF.");
                } else {
                    NotificationUtil.showError("Export Failed", 
                        "Failed to export report to PDF.");
                }
            }
        }
    }

    private void printReport(Node reportContent) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(orderTable.getScene().getWindow())) {
            boolean success = job.printPage(reportContent);
            if (success) {
                job.endJob();
                NotificationUtil.showSuccess("Print Complete", 
                    "Successfully printed the report.");
            } else {
                NotificationUtil.showError("Print Failed", 
                    "Failed to print the report.");
            }
        }
    }

    private void showAdvancedStatisticsDialog(List<Order> orders) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Advanced Order Analytics");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        
        // Create tabs for different analytics
        TabPane tabPane = new TabPane();
        
        // Summary tab with KPIs
        Tab summaryTab = new Tab("Summary", createSummaryContent(orders));
        summaryTab.setClosable(false);
        
        // Trends tab with charts
        Tab trendsTab = new Tab("Trends", createTrendsContent(orders));
        trendsTab.setClosable(false);
        
        // Products analysis
        Tab productsTab = new Tab("Products", createProductsContent(orders));
        productsTab.setClosable(false);
        
        tabPane.getTabs().addAll(summaryTab, trendsTab, productsTab);
        content.getChildren().add(tabPane);
        
        Scene scene = new Scene(content, 1000, 700);
        dialog.setScene(scene);
        dialog.show();
    }

    private Node createSummaryContent(List<Order> orders) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        
        // KPI cards
        double totalRevenue = orders.stream()
            .mapToDouble(Order::getTotalAmount)
            .sum();
            
        double avgOrderValue = orders.stream()
            .mapToDouble(Order::getTotalAmount)
            .average()
            .orElse(0.0);
            
        int uniqueCustomers = (int) orders.stream()
            .map(Order::getCustomerName)
            .distinct()
            .count();
        
        GridPane kpiGrid = new GridPane();
        kpiGrid.setHgap(15);
        kpiGrid.setVgap(15);
        
        // Add KPI cards
        kpiGrid.add(createKpiCard("Total Revenue", totalRevenue), 0, 0);
        kpiGrid.add(createKpiCard("Total Orders", orders.size()), 1, 0);
        kpiGrid.add(createKpiCard("Avg Order Value", avgOrderValue), 2, 0);
        kpiGrid.add(createKpiCard("Unique Customers", uniqueCustomers), 3, 0);
        
        // Revenue trend chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> revenueChart = new LineChart<>(xAxis, yAxis);
        revenueChart.setTitle("Revenue Trend");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Revenue");
        
        // Group by date and calculate revenue
        Map<LocalDate, Double> dailyRevenue = orders.stream()
            .collect(Collectors.groupingBy(
                Order::getOrderDate,
                Collectors.summingDouble(Order::getTotalAmount)
            ));
        
        dailyRevenue.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> series.getData().add(
                new XYChart.Data<>(
                    entry.getKey().format(dateFormatter),
                    entry.getValue()
                )
            ));
        
        revenueChart.getData().add(series);
        revenueChart.setCreateSymbols(false);
        VBox.setVgrow(revenueChart, Priority.ALWAYS);
        
        content.getChildren().addAll(kpiGrid, revenueChart);
        return content;
    }

    private Node createTrendsContent(List<Order> orders) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        
        // Weekly pattern chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> weeklyChart = new BarChart<>(xAxis, yAxis);
        weeklyChart.setTitle("Orders by Day of Week");
        
        XYChart.Series<String, Number> weeklySeries = new XYChart.Series<>();
        weeklySeries.setName("Number of Orders");
        
        Map<DayOfWeek, Long> ordersByDay = orders.stream()
            .collect(Collectors.groupingBy(
                order -> order.getOrderDate().getDayOfWeek(),
                Collectors.counting()
            ));
        
        Arrays.stream(DayOfWeek.values())
            .forEach(day -> weeklySeries.getData().add(
                new XYChart.Data<>(
                    day.toString(),
                    ordersByDay.getOrDefault(day, 0L)
                )
            ));
        
        weeklyChart.getData().add(weeklySeries);
        VBox.setVgrow(weeklyChart, Priority.ALWAYS);
        
        content.getChildren().add(weeklyChart);
        return content;
    }

    private Node createProductsContent(List<Order> orders) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        
        // Product analytics table
        TableView<ProductAnalytics> productTable = new TableView<>();
        
        TableColumn<ProductAnalytics, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        
        TableColumn<ProductAnalytics, Integer> quantityCol = new TableColumn<>("Units Sold");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));
        
        TableColumn<ProductAnalytics, Double> revenueCol = new TableColumn<>("Revenue");
        revenueCol.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        revenueCol.setCellFactory(col -> new TableCell<ProductAnalytics, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", value));
                }
            }
        });

        // Fix type safety warning for table columns
        @SuppressWarnings("unchecked")
        TableColumn<ProductAnalytics, ?>[] columns = new TableColumn[]{nameCol, quantityCol, revenueCol};
        productTable.getColumns().addAll(columns);
        
        // Calculate product analytics
        Map<String, ProductAnalytics> analytics = new HashMap<>();
        orders.stream()
            .flatMap(order -> order.getProducts().stream())
            .forEach(product -> {
                analytics.computeIfAbsent(
                    product.getProductName(),
                    name -> new ProductAnalytics(name)
                ).addProduct(product);
            });
        
        productTable.setItems(FXCollections.observableArrayList(analytics.values()));
        VBox.setVgrow(productTable, Priority.ALWAYS);
        
        // Product revenue pie chart
        PieChart revenueChart = new PieChart();
        revenueChart.setTitle("Revenue by Product");
        
        analytics.values().stream()
            .sorted(Comparator.comparingDouble(ProductAnalytics::getRevenue).reversed())
            .limit(5) // Show top 5 products
            .forEach(pa -> revenueChart.getData().add(
                new PieChart.Data(pa.getName(), pa.getRevenue())
            ));
        
        content.getChildren().addAll(productTable, revenueChart);
        return content;
    }

    private VBox createKpiCard(String title, Object value) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setStyle(
            "-fx-background-color: white; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 0); " +
            "-fx-background-radius: 5;"
        );
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #666;");
        
        Label valueLabel = new Label(formatValue(value));
        valueLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");
        
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private String formatValue(Object value) {
        if (value instanceof Double) {
            return String.format("$%.2f", (Double) value);
        } else if (value instanceof Integer) {
            return String.format("%d", (Integer) value);
        }
        return value.toString();
    }
}
