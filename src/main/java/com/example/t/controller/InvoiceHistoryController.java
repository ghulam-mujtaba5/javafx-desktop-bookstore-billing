package com.example.t.controller;

import com.example.t.Invoice;
import com.example.t.NotificationUtil;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService;
import com.example.t.service.CsvSyncService;
import com.example.t.service.PdfInvoiceService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Invoice History and Search Screen.
 * Provides invoice search/filtering, line-item detail inspection,
 * OpenPDF reprint, and RFC 4180 CSV export.
 */
public class InvoiceHistoryController {

    @FXML private Label kpiTotalInvoices;
    @FXML private Label kpiTotalRevenue;

    @FXML private TextField searchField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button btnApplyDateFilter;
    @FXML private Button btnResetFilter;
    @FXML private Button btnReprintPdf;
    @FXML private Button btnExportCsv;
    @FXML private Button btnRefresh;

    @FXML private TableView<Invoice> invoiceTable;
    @FXML private TableColumn<Invoice, Integer> colInvoiceId;
    @FXML private TableColumn<Invoice, String> colDate;
    @FXML private TableColumn<Invoice, String> colCustomer;
    @FXML private TableColumn<Invoice, String> colSubtotal;
    @FXML private TableColumn<Invoice, String> colDiscount;
    @FXML private TableColumn<Invoice, String> colNetTotal;
    @FXML private Label invoiceCountLabel;

    @FXML private Label detailInvoiceNumLabel;
    @FXML private Label detailDateLabel;
    @FXML private Label detailCustomerLabel;
    @FXML private Label detailTotalLabel;

    @FXML private TableView<InvoiceItem> detailItemsTable;
    @FXML private TableColumn<InvoiceItem, String> colItemName;
    @FXML private TableColumn<InvoiceItem, String> colItemSku;
    @FXML private TableColumn<InvoiceItem, Integer> colItemQty;
    @FXML private TableColumn<InvoiceItem, String> colItemPrice;
    @FXML private TableColumn<InvoiceItem, String> colItemTotal;
    @FXML private Button btnQuickReprint;

    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfInvoiceService;

    private StoreSettings settings;
    private final ObservableList<Invoice> masterInvoices = FXCollections.observableArrayList();
    private FilteredList<Invoice> filteredInvoices;
    private final ObservableList<InvoiceItem> selectedItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (invoiceDao == null) invoiceDao = new InvoiceDao();
        if (settingsDao == null) settingsDao = new SettingsDao();
        if (pdfInvoiceService == null) pdfInvoiceService = new PdfInvoiceService();

        loadStoreSettings();
        setupMasterTable();
        setupDetailTable();
        loadInvoices();
    }

    public void setInvoiceDao(InvoiceDao invoiceDao) {
        this.invoiceDao = invoiceDao;
        loadInvoices();
    }

    public void setSettingsDao(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
        loadStoreSettings();
    }

    public void setPdfInvoiceService(PdfInvoiceService pdfInvoiceService) {
        this.pdfInvoiceService = pdfInvoiceService;
    }

    private void loadStoreSettings() {
        try {
            if (settingsDao != null) {
                settings = settingsDao.getSettings();
            }
        } catch (SQLException ignored) {}
        if (settings == null) {
            settings = StoreSettings.defaultSettings();
        }
    }

    private void setupMasterTable() {
        if (invoiceTable == null) return;

        colInvoiceId.setCellValueFactory(cellData -> cellData.getValue().invoiceIdProperty().asObject());
        colDate.setCellValueFactory(cellData -> {
            LocalDate d = cellData.getValue().getInvoiceDate();
            return new SimpleStringProperty(d != null ? d.toString() : "-");
        });
        colCustomer.setCellValueFactory(cellData -> cellData.getValue().customerNameProperty());
        colSubtotal.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getOrderTotal())));
        colDiscount.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getDiscount())));
        colNetTotal.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getNetPrice())));

        filteredInvoices = new FilteredList<>(masterInvoices, p -> true);
        SortedList<Invoice> sorted = new SortedList<>(filteredInvoices);
        sorted.comparatorProperty().bind(invoiceTable.comparatorProperty());
        invoiceTable.setItems(sorted);

        // Search field predicate
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        }

        // Selection listener to show details
        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            showInvoiceDetails(selected);
        });
    }

    private void setupDetailTable() {
        if (detailItemsTable == null) return;

        colItemName.setCellValueFactory(cellData -> cellData.getValue().productNameProperty());
        colItemSku.setCellValueFactory(cellData -> cellData.getValue().skuProperty());
        colItemQty.setCellValueFactory(cellData -> cellData.getValue().quantityProperty().asObject());
        colItemPrice.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getUnitPrice())));
        colItemTotal.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getLineTotal())));

        detailItemsTable.setItems(selectedItems);
    }

    public void loadInvoices() {
        if (invoiceDao == null) return;
        try {
            List<Invoice> list = invoiceDao.getAllInvoices();
            if (list == null || list.isEmpty()) {
                // Fallback to legacy reader if SQLite database has no records yet
                list = Invoice.readInvoicesFromFile();
            }
            masterInvoices.setAll(list);
            updateKpiAndCount();
            if (!masterInvoices.isEmpty() && invoiceTable != null) {
                invoiceTable.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            NotificationUtil.showError("Database Error", "Failed to load invoices: " + e.getMessage());
        }
    }

    private void updateKpiAndCount() {
        int total = masterInvoices.size();
        double sum = 0.0;
        for (Invoice inv : masterInvoices) {
            sum += inv.getNetPrice();
        }
        sum = BillingService.roundMoney(sum);

        if (kpiTotalInvoices != null) {
            kpiTotalInvoices.setText(String.valueOf(total));
        }
        if (kpiTotalRevenue != null) {
            kpiTotalRevenue.setText(formatMoney(sum));
        }
        if (invoiceCountLabel != null) {
            int displayed = filteredInvoices != null ? filteredInvoices.size() : total;
            invoiceCountLabel.setText("Showing " + displayed + " of " + total + " invoices");
        }
    }

    private void applyFilters() {
        String query = searchField != null ? searchField.getText() : null;
        LocalDate from = fromDatePicker != null ? fromDatePicker.getValue() : null;
        LocalDate to = toDatePicker != null ? toDatePicker.getValue() : null;

        filteredInvoices.setPredicate(inv -> {
            // Text search
            if (query != null && !query.trim().isEmpty()) {
                String clean = query.trim().toLowerCase();
                boolean matchesId = String.valueOf(inv.getInvoiceId()).contains(clean);
                boolean matchesCust = inv.getCustomerName() != null && inv.getCustomerName().toLowerCase().contains(clean);
                if (!matchesId && !matchesCust) {
                    return false;
                }
            }
            // Date filter
            if (inv.getInvoiceDate() != null) {
                if (from != null && inv.getInvoiceDate().isBefore(from)) {
                    return false;
                }
                if (to != null && inv.getInvoiceDate().isAfter(to)) {
                    return false;
                }
            }
            return true;
        });

        updateKpiAndCount();
    }

    private void showInvoiceDetails(Invoice invoice) {
        if (invoice == null) {
            if (detailInvoiceNumLabel != null) detailInvoiceNumLabel.setText("None Selected");
            if (detailDateLabel != null) detailDateLabel.setText("-");
            if (detailCustomerLabel != null) detailCustomerLabel.setText("-");
            if (detailTotalLabel != null) detailTotalLabel.setText(formatMoney(0.0));
            selectedItems.clear();
            return;
        }

        if (detailInvoiceNumLabel != null) detailInvoiceNumLabel.setText("INV-" + invoice.getInvoiceId());
        if (detailDateLabel != null) detailDateLabel.setText(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "-");
        if (detailCustomerLabel != null) detailCustomerLabel.setText(invoice.getCustomerName());
        if (detailTotalLabel != null) detailTotalLabel.setText(formatMoney(invoice.getNetPrice()));

        // Load line items from DAO or invoice products
        selectedItems.clear();
        if (invoiceDao != null && invoice.getInvoiceId() > 0) {
            try {
                List<InvoiceItem> items = invoiceDao.getInvoiceItems(invoice.getInvoiceId());
                if (items != null && !items.isEmpty()) {
                    selectedItems.setAll(items);
                    return;
                }
            } catch (SQLException ignored) {}
        }

        // Fallback to invoice product list
        if (invoice.getProducts() != null) {
            for (var p : invoice.getProducts()) {
                double total = BillingService.roundMoney(p.getPrice() * p.getQuantity());
                selectedItems.add(new InvoiceItem(
                        p.getProductId(),
                        p.getProductName(),
                        p.getSku(),
                        p.getQuantity(),
                        p.getPrice()
                ));
            }
        }
    }

    @FXML
    public void handleApplyDateFilter(ActionEvent event) {
        applyFilters();
    }

    @FXML
    public void handleResetFilter(ActionEvent event) {
        if (searchField != null) searchField.clear();
        if (fromDatePicker != null) fromDatePicker.setValue(null);
        if (toDatePicker != null) toDatePicker.setValue(null);
        if (filteredInvoices != null) filteredInvoices.setPredicate(p -> true);
        updateKpiAndCount();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadInvoices();
        NotificationUtil.showSuccess("Refreshed", "Invoice history refreshed.");
    }

    @FXML
    public void handleReprintPdf(ActionEvent event) {
        Invoice selected = invoiceTable != null ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            NotificationUtil.showWarning("No Invoice Selected", "Please select an invoice from the table to reprint.");
            return;
        }

        List<InvoiceItem> items = new ArrayList<>(selectedItems);
        try {
            Path pdfPath = pdfInvoiceService.generateInvoicePdf(selected, items, settings);
            NotificationUtil.showSuccess("PDF Exported", "Invoice receipt generated: " + pdfPath.getFileName());
        } catch (IOException e) {
            NotificationUtil.showError("PDF Export Failed", "Could not generate invoice PDF: " + e.getMessage());
        }
    }

    @FXML
    public void handleExportCsv(ActionEvent event) {
        if (masterInvoices.isEmpty()) {
            NotificationUtil.showWarning("No Data", "No invoices available to export.");
            return;
        }

        try {
            Path exportPath = DatabaseManager.getExportsDirectory().resolve("invoices_export.csv");
            CsvSyncService.exportInvoicesToFile(new ArrayList<>(masterInvoices), exportPath);
            NotificationUtil.showSuccess("CSV Exported", "Exported " + masterInvoices.size() + " invoices to: " + exportPath.getFileName());
        } catch (IOException e) {
            NotificationUtil.showError("Export Failed", "Failed to export CSV: " + e.getMessage());
        }
    }

    private String formatMoney(double amount) {
        String sym = settings != null ? settings.getCurrencySymbol() : "$";
        return String.format("%s %.2f", sym, amount);
    }

    public ObservableList<Invoice> getMasterInvoices() {
        return masterInvoices;
    }

    public ObservableList<InvoiceItem> getSelectedItems() {
        return selectedItems;
    }
}
