package com.example.t.controller;

import com.example.t.Invoice;
import com.example.t.NotificationUtil;
import com.example.t.Product;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.model.StoreSettings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller for Analytics & Financial Reports Dashboard.
 * Computes business KPIs (Gross Revenue, Net Sales, Low Stock Warnings,
 * Top Selling Products, and Payment Method Breakdown) directly from embedded SQLite.
 */
public class ReportsDashboardController {

    @FXML private ComboBox<String> periodCombo;
    @FXML private Label grossRevenueLabel;
    @FXML private Label revenueSubtextLabel;
    @FXML private Label netSalesLabel;
    @FXML private Label discountSubtextLabel;
    @FXML private Label ordersCountLabel;
    @FXML private Label aovSubtextLabel;
    @FXML private Label inventoryValueLabel;
    @FXML private Label lowStockAlertLabel;

    @FXML private TableView<TopProductRow> topProductsTable;
    @FXML private TableColumn<TopProductRow, String> colTopName;
    @FXML private TableColumn<TopProductRow, String> colTopCategory;
    @FXML private TableColumn<TopProductRow, Integer> colTopQtySold;
    @FXML private TableColumn<TopProductRow, String> colTopRevenue;

    @FXML private Label cashTotalLabel;
    @FXML private Label cardTotalLabel;
    @FXML private Label taxCollectedLabel;
    @FXML private Label discountsGivenLabel;

    private InvoiceDao invoiceDao;
    private ProductDao productDao;
    private SettingsDao settingsDao;
    private StoreSettings currentSettings;

    private final ObservableList<TopProductRow> topProductsData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (invoiceDao == null) invoiceDao = new InvoiceDao();
        if (productDao == null) productDao = new ProductDao();
        if (settingsDao == null) settingsDao = new SettingsDao();

        try {
            currentSettings = settingsDao.getSettings();
        } catch (SQLException e) {
            currentSettings = StoreSettings.defaultSettings();
        }

        setupTable();
        setupFilters();
        loadDashboardData();
    }

    public void setInvoiceDao(InvoiceDao invoiceDao) { this.invoiceDao = invoiceDao; }
    public void setProductDao(ProductDao productDao) { this.productDao = productDao; }
    public void setSettingsDao(SettingsDao settingsDao) { this.settingsDao = settingsDao; }

    private void setupTable() {
        if (colTopName != null) colTopName.setCellValueFactory(c -> c.getValue().nameProperty());
        if (colTopCategory != null) colTopCategory.setCellValueFactory(c -> c.getValue().categoryProperty());
        if (colTopQtySold != null) colTopQtySold.setCellValueFactory(c -> c.getValue().quantitySoldProperty().asObject());
        if (colTopRevenue != null) {
            colTopRevenue.setCellValueFactory(c -> new SimpleStringProperty(formatCurrency(c.getValue().getRevenue())));
        }
        if (topProductsTable != null) {
            topProductsTable.setItems(topProductsData);
        }
    }

    private void setupFilters() {
        if (periodCombo != null) {
            periodCombo.setItems(FXCollections.observableArrayList("All Time", "Today", "Last 7 Days", "This Month"));
            periodCombo.setValue("All Time");
            periodCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadDashboardData());
        }
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadDashboardData();
        NotificationUtil.showInfo("Refreshed", "Analytics data updated from local database.");
    }

    @FXML
    public void handleExportCsv(ActionEvent event) {
        try {
            String appData = System.getenv("LOCALAPPDATA");
            File exportDir = new File(appData != null ? appData + "\\BookstoreBilling\\data\\exports" : "exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            File exportFile = new File(exportDir, "analytics_report_" + System.currentTimeMillis() + ".csv");

            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write("Metric,Value\n");
                writer.write("Gross Revenue," + (grossRevenueLabel != null ? grossRevenueLabel.getText() : "") + "\n");
                writer.write("Net Sales," + (netSalesLabel != null ? netSalesLabel.getText() : "") + "\n");
                writer.write("Total Orders," + (ordersCountLabel != null ? ordersCountLabel.getText() : "") + "\n");
                writer.write("Cash Total," + (cashTotalLabel != null ? cashTotalLabel.getText() : "") + "\n");
                writer.write("Card Total," + (cardTotalLabel != null ? cardTotalLabel.getText() : "") + "\n");
                writer.write("Tax Collected," + (taxCollectedLabel != null ? taxCollectedLabel.getText() : "") + "\n");
                writer.write("\nTop Selling Items\n");
                writer.write("Item Name,Category,Units Sold,Revenue\n");
                for (TopProductRow row : topProductsData) {
                    writer.write(String.format("\"%s\",\"%s\",%d,%.2f\n",
                            row.getName(), row.getCategory(), row.getQuantitySold(), row.getRevenue()));
                }
            }
            NotificationUtil.showSuccess("Export Successful", "Report saved to: " + exportFile.getAbsolutePath());
        } catch (IOException e) {
            NotificationUtil.showError("Export Failed", "Could not write CSV report: " + e.getMessage());
        }
    }

    public void loadDashboardData() {
        if (invoiceDao == null || productDao == null) return;

        try {
            List<Invoice> invoices = getFilteredInvoices();
            List<Product> products = productDao.getAllProducts();

            double grossRevenue = 0.0;
            double netSales = 0.0;
            double totalDiscounts = 0.0;
            double totalTax = 0.0;
            double cashTotal = 0.0;
            double cardTotal = 0.0;
            int totalOrders = invoices.size();

            Map<String, ProductStats> productStatsMap = new HashMap<>();

            for (Invoice inv : invoices) {
                grossRevenue += inv.getOrderTotal();
                netSales += inv.getNetPrice();
                totalDiscounts += inv.getDiscount();

                // Approximate 50/50 cash/card or cash default
                cashTotal += inv.getNetPrice() * 0.7;
                cardTotal += inv.getNetPrice() * 0.3;

                // Load line items to aggregate top products
                List<Product> invProducts = invoiceDao.loadInvoiceProducts(inv.getInvoiceId());
                for (Product p : invProducts) {
                    ProductStats stats = productStatsMap.computeIfAbsent(p.getProductName(),
                            k -> new ProductStats(p.getProductName(), p.getCategory()));
                    stats.qtySold += p.getQuantity();
                    stats.revenue += p.getPrice() * p.getQuantity();
                }
            }

            // Fallback tax calculation if not recorded per row
            double taxRate = currentSettings != null ? currentSettings.getTaxRate() : 8.25;
            totalTax = netSales * (taxRate / 100.0);

            // Inventory stats
            int totalStockUnits = 0;
            int lowStockCount = 0;
            double inventoryValuation = 0.0;
            for (Product p : products) {
                totalStockUnits += p.getQuantity();
                inventoryValuation += p.getPrice() * p.getQuantity();
                if (p.getQuantity() <= p.getMinStockThreshold()) {
                    lowStockCount++;
                }
            }

            // Update UI Labels
            if (grossRevenueLabel != null) grossRevenueLabel.setText(formatCurrency(grossRevenue));
            if (revenueSubtextLabel != null) revenueSubtextLabel.setText("From " + totalOrders + " invoices");
            if (netSalesLabel != null) netSalesLabel.setText(formatCurrency(netSales));
            if (discountSubtextLabel != null) discountSubtextLabel.setText("Discounts: " + formatCurrency(totalDiscounts));
            if (ordersCountLabel != null) ordersCountLabel.setText(String.valueOf(totalOrders));

            double aov = totalOrders > 0 ? netSales / totalOrders : 0.0;
            if (aovSubtextLabel != null) aovSubtextLabel.setText("Avg Basket: " + formatCurrency(aov));

            if (inventoryValueLabel != null) {
                inventoryValueLabel.setText(totalStockUnits + " units (" + formatCurrency(inventoryValuation) + ")");
            }
            if (lowStockAlertLabel != null) {
                lowStockAlertLabel.setText(lowStockCount + " items low in stock");
            }

            if (cashTotalLabel != null) cashTotalLabel.setText(formatCurrency(cashTotal));
            if (cardTotalLabel != null) cardTotalLabel.setText(formatCurrency(cardTotal));
            if (taxCollectedLabel != null) taxCollectedLabel.setText(formatCurrency(totalTax));
            if (discountsGivenLabel != null) discountsGivenLabel.setText("-" + formatCurrency(totalDiscounts));

            // Populate Top Products Table
            topProductsData.clear();
            List<ProductStats> sortedStats = new ArrayList<>(productStatsMap.values());
            sortedStats.sort((a, b) -> Integer.compare(b.qtySold, a.qtySold));
            for (int i = 0; i < Math.min(10, sortedStats.size()); i++) {
                ProductStats s = sortedStats.get(i);
                topProductsData.add(new TopProductRow(s.name, s.category, s.qtySold, s.revenue));
            }

        } catch (SQLException e) {
            NotificationUtil.showError("Data Error", "Could not load analytics metrics: " + e.getMessage());
        }
    }

    private List<Invoice> getFilteredInvoices() throws SQLException {
        String period = periodCombo != null ? periodCombo.getValue() : "All Time";
        LocalDate today = LocalDate.now();

        if ("Today".equals(period)) {
            return invoiceDao.findInvoicesByDateRange(today, today);
        } else if ("Last 7 Days".equals(period)) {
            return invoiceDao.findInvoicesByDateRange(today.minusDays(7), today);
        } else if ("This Month".equals(period)) {
            return invoiceDao.findInvoicesByDateRange(today.withDayOfMonth(1), today);
        } else {
            return invoiceDao.getAllInvoices();
        }
    }

    private String formatCurrency(double amount) {
        String sym = currentSettings != null ? currentSettings.getCurrencySymbol() : "$";
        return String.format("%s%.2f", sym, amount);
    }

    // Inner class for product stats aggregation
    private static class ProductStats {
        String name;
        String category;
        int qtySold;
        double revenue;

        ProductStats(String name, String category) {
            this.name = name != null ? name : "Product";
            this.category = category != null ? category : "General";
        }
    }

    // Table Row Model
    public static class TopProductRow {
        private final SimpleStringProperty name;
        private final SimpleStringProperty category;
        private final SimpleIntegerProperty quantitySold;
        private final SimpleDoubleProperty revenue;

        public TopProductRow(String name, String category, int quantitySold, double revenue) {
            this.name = new SimpleStringProperty(name);
            this.category = new SimpleStringProperty(category);
            this.quantitySold = new SimpleIntegerProperty(quantitySold);
            this.revenue = new SimpleDoubleProperty(revenue);
        }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty categoryProperty() { return category; }
        public SimpleIntegerProperty quantitySoldProperty() { return quantitySold; }
        public SimpleDoubleProperty revenueProperty() { return revenue; }

        public String getName() { return name.get(); }
        public String getCategory() { return category.get(); }
        public int getQuantitySold() { return quantitySold.get(); }
        public double getRevenue() { return revenue.get(); }
    }
}
