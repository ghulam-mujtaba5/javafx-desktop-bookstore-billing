package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
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
import javafx.scene.text.Text;
import javafx.scene.Node;
import javafx.print.PageLayout;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Month;
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
        private int quantitySold;
        private double revenue;

        public ProductAnalytics(String name) {
            this.name = name;
            this.quantitySold = 0;
            this.revenue = 0;
        }

        public void addProduct(Product product) {
            quantitySold += product.getQuantity();
            revenue += product.getPrice() * product.getQuantity();
        }

        public String getName() { return name; }
        public int getQuantitySold() { return quantitySold; }
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
        batchOperationsButton.setDisable(true);
        
        orderTable.getSelectionModel().getSelectedItems().addListener(
            (ListChangeListener<Order>) c -> 
                batchOperationsButton.setDisable(c.getList().isEmpty())
        );
        
        batchOperationsButton.setOnAction(e -> showBatchOperationsDialog());
    }

    private void showBatchOperationsDialog() {
        ObservableList<Order> selectedOrders = orderTable.getSelectionModel().getSelectedItems();
        
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Batch Operations");

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: white;");

        Label header = new Label("Selected Orders: " + selectedOrders.size());
        header.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // Create operation buttons
        Button printButton = new Button("Print Selected Orders");
        printButton.setOnAction(e -> {
            dialog.close();
            printSelectedOrders();
        });

        Button exportButton = new Button("Export to CSV");
        exportButton.setOnAction(e -> {
            dialog.close();
            exportSelectedToCSV(selectedOrders);
        });

        content.getChildren().addAll(
            header,
            new Separator(),
            printButton,
            exportButton
        );

        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.show();
    }

    private void printSelectedOrders() {
        ObservableList<Order> selectedOrders = orderTable.getSelectionModel().getSelectedItems();
        if (selectedOrders.isEmpty()) {
            NotificationUtil.showWarning("No Selection", "Please select orders to print");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(orderTable.getScene().getWindow())) {
            VBox printContent = new VBox(20);
            printContent.setPadding(new Insets(20));

            // Add header
            Text header = new Text("Order Report");
            header.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");
            printContent.getChildren().add(header);

            // Add date
            Text dateText = new Text("Generated: " + LocalDate.now().format(dateFormatter));
            dateText.setStyle("-fx-font-size: 12;");
            printContent.getChildren().add(dateText);
            printContent.getChildren().add(new Separator());

            // Add each order
            for (Order order : selectedOrders) {
                VBox orderBox = new VBox(5);
                orderBox.getChildren().addAll(
                    new Text("Order #" + order.getOrderId()),
                    new Text("Customer: " + order.getCustomerName()),
                    new Text("Date: " + order.getOrderDate().format(dateFormatter)),
                    new Text("Products:")
                );

                // Add products table
                GridPane productsGrid = new GridPane();
                productsGrid.setHgap(10);
                productsGrid.setVgap(5);
                productsGrid.setPadding(new Insets(0, 0, 0, 20));

                // Add headers
                productsGrid.addRow(0,
                    new Text("Product"),
                    new Text("Quantity"),
                    new Text("Price")
                );

                // Add products
                int row = 1;
                for (Product product : order.getProducts()) {
                    productsGrid.addRow(row++,
                        new Text(product.getProductName()),
                        new Text(String.valueOf(product.getQuantity())),

                        new Text(String.format("$%.2f", product.getPrice()))
                    );
                }

                orderBox.getChildren().add(productsGrid);
                orderBox.getChildren().add(new Text(
                    String.format("Total Amount: $%.2f", order.getTotalAmount())
                ));
                orderBox.getChildren().add(new Separator());
                printContent.getChildren().add(orderBox);
            }

            // Print the content
            boolean printed = job.printPage(printContent);
            if (printed) {
                job.endJob();
                NotificationUtil.showSuccess("Print Complete", 
                    "Successfully printed " + selectedOrders.size() + " orders");
            } else {
                NotificationUtil.showError("Print Failed", 
                    "Failed to print orders");
            }
        }
    }

    private void setupPrintingAndStatistics() {
        printSelectedButton.setOnAction(e -> printSelectedOrders());
        statisticsButton.setOnAction(e -> showAdvancedStatisticsDialog());
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

    private void exportSelectedToCSV(List<Order> orders) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Selected Orders");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("selected_orders_export.csv");

        File file = fileChooser.showSaveDialog(orderTable.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                writer.write("Order ID,Date,Customer,Total Amount,Products\n");

                // Write data
                for (Order order : orders) {
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
                    "Selected orders have been exported to " + file.getName());
            } catch (IOException e) {
                NotificationUtil.showError("Export Failed", 
                    "Failed to export orders: " + e.getMessage());
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
        
        productTable.getColumns().addAll(nameCol, quantityCol, revenueCol);
        
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
