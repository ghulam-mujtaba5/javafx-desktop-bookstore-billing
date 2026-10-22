package com.example.t.controller;

import com.example.t.Invoice;
import com.example.t.NotificationUtil;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.SettingsDao;
import com.example.t.dao.ZReportDao;
import com.example.t.model.StoreSettings;
import com.example.t.model.ZReport;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Cash Drawer & Shift Management.
 * Manages daily cash floats, Pay-In / Pay-Out drawer adjustments,
 * mid-shift X-Report live readings, and fiscal end-of-day Z-Report closures.
 */
public class ShiftManagementController {

    @FXML private Label shiftStatusLabel;
    @FXML private Label shiftStartLabel;
    @FXML private Label openingFloatLabel;
    @FXML private Label cashierLabel;
    @FXML private Label cashSalesLabel;
    @FXML private Label cashOrdersLabel;
    @FXML private Label adjustmentsLabel;
    @FXML private Label adjustmentsCountLabel;
    @FXML private Label expectedCashLabel;
    @FXML private Label auditDetailsLabel;

    @FXML private TableView<ZReport> zReportsTable;
    @FXML private TableColumn<ZReport, Integer> colSeq;
    @FXML private TableColumn<ZReport, String> colTimestamp;
    @FXML private TableColumn<ZReport, String> colOpeningFloat;
    @FXML private TableColumn<ZReport, String> colCashSales;
    @FXML private TableColumn<ZReport, String> colCardSales;
    @FXML private TableColumn<ZReport, String> colNetSales;
    @FXML private TableColumn<ZReport, String> colExpectedCash;
    @FXML private TableColumn<ZReport, String> colActualCash;
    @FXML private TableColumn<ZReport, String> colOverShort;
    @FXML private TableColumn<ZReport, String> colClosedBy;
    @FXML private TableColumn<ZReport, String> colNotes;

    @FXML private Button btnOpenShift;
    @FXML private Button btnPayIn;
    @FXML private Button btnPayOut;
    @FXML private Button btnXReport;
    @FXML private Button btnCloseShift;

    private ZReportDao zReportDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private StoreSettings currentSettings;

    private boolean isShiftOpen = true;
    private double openingFloat = 100.0;
    private Timestamp shiftStartTime;
    private double totalPayIn = 0.0;
    private double totalPayOut = 0.0;
    private int payInCount = 0;
    private int payOutCount = 0;
    private String currentCashier = "admin";
    private final StringBuilder auditLog = new StringBuilder();
    private final ObservableList<ZReport> historicalReports = FXCollections.observableArrayList();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter FULL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        if (zReportDao == null) zReportDao = new ZReportDao();
        if (invoiceDao == null) invoiceDao = new InvoiceDao();
        if (settingsDao == null) settingsDao = new SettingsDao();

        shiftStartTime = Timestamp.valueOf(LocalDateTime.now().withHour(9).withMinute(0).withSecond(0));
        auditLog.append(String.format("Shift initialized at %s with opening float $%.2f.\n",
                LocalDateTime.now().format(TIME_FMT), openingFloat));

        loadSettings();
        setupTableColumns();
        loadHistoricalZReports();
        recalculateCurrentShift();
    }

    public void setZReportDao(ZReportDao zReportDao) {
        this.zReportDao = zReportDao;
        loadHistoricalZReports();
    }

    public void setInvoiceDao(InvoiceDao invoiceDao) {
        this.invoiceDao = invoiceDao;
        recalculateCurrentShift();
    }

    public void setSettingsDao(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
        loadSettings();
        recalculateCurrentShift();
    }

    private void loadSettings() {
        if (settingsDao != null) {
            try {
                currentSettings = settingsDao.getSettings();
            } catch (SQLException ignored) {
            }
        }
        if (currentSettings == null) {
            currentSettings = new StoreSettings();
        }
    }

    private void setupTableColumns() {
        if (zReportsTable == null) return;

        colSeq.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getZSequenceNumber()).asObject());
        colTimestamp.setCellValueFactory(cell -> {
            Timestamp ts = cell.getValue().getReportTimestamp();
            return new SimpleStringProperty(ts != null ? ts.toLocalDateTime().format(FULL_FMT) : "");
        });
        colOpeningFloat.setCellValueFactory(cell -> new SimpleStringProperty(formatCurrency(cell.getValue().getOpeningFloat())));
        colCashSales.setCellValueFactory(cell -> new SimpleStringProperty(formatCurrency(cell.getValue().getTotalCashSales())));
        colCardSales.setCellValueFactory(cell -> new SimpleStringProperty(formatCurrency(cell.getValue().getTotalCardSales())));
        colNetSales.setCellValueFactory(cell -> new SimpleStringProperty(formatCurrency(cell.getValue().getNetSales())));
        colExpectedCash.setCellValueFactory(cell -> new SimpleStringProperty(formatCurrency(cell.getValue().getExpectedDrawerCash())));
        colActualCash.setCellValueFactory(cell -> new SimpleStringProperty(formatCurrency(cell.getValue().getActualDrawerCash())));
        colOverShort.setCellValueFactory(cell -> {
            double diff = cell.getValue().getCashOverShort();
            String prefix = diff >= 0 ? "+" : "";
            return new SimpleStringProperty(prefix + formatCurrency(diff));
        });
        colClosedBy.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getClosedByUser()));
        colNotes.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNotes() != null ? cell.getValue().getNotes() : ""));

        zReportsTable.setItems(historicalReports);
    }

    public void loadHistoricalZReports() {
        if (zReportDao == null) return;
        try {
            List<ZReport> reports = zReportDao.getAllZReports();
            historicalReports.setAll(reports);
        } catch (SQLException e) {
            NotificationUtil.showError("Database Error", "Failed to load Z-Reports: " + e.getMessage());
        }
    }

    public void recalculateCurrentShift() {
        double cashSales = 0.0;
        int cashInvoices = 0;

        if (invoiceDao != null) {
            try {
                List<Invoice> allInvoices = invoiceDao.getAllInvoices();
                for (Invoice inv : allInvoices) {
                    if (shiftStartTime != null && inv.getInvoiceDate() != null) {
                        if (inv.getInvoiceDate().isBefore(shiftStartTime.toLocalDateTime().toLocalDate())) {
                            continue;
                        }
                    }
                    String method = inv.getPaymentMethod() != null ? inv.getPaymentMethod().toUpperCase() : "CASH";
                    if ("CASH".equals(method)) {
                        cashSales += inv.getNetPrice();
                        cashInvoices++;
                    } else if ("SPLIT".equals(method)) {
                        cashSales += inv.getNetPrice() * 0.5;
                        cashInvoices++;
                    }
                }
            } catch (SQLException ignored) {
            }
        }

        double expectedCash = openingFloat + cashSales + totalPayIn - totalPayOut;

        if (shiftStatusLabel != null) {
            shiftStatusLabel.setText(isShiftOpen ? "OPEN" : "CLOSED");
            shiftStatusLabel.setStyle(isShiftOpen ? "-fx-text-fill: #16a34a;" : "-fx-text-fill: #dc2626;");
        }
        if (shiftStartLabel != null && shiftStartTime != null) {
            shiftStartLabel.setText("Started: " + shiftStartTime.toLocalDateTime().format(TIME_FMT));
        }
        if (openingFloatLabel != null) {
            openingFloatLabel.setText(formatCurrency(openingFloat));
        }
        if (cashierLabel != null) {
            cashierLabel.setText("Cashier: " + currentCashier);
        }
        if (cashSalesLabel != null) {
            cashSalesLabel.setText(formatCurrency(cashSales));
        }
        if (cashOrdersLabel != null) {
            cashOrdersLabel.setText(cashInvoices + " Cash Invoices");
        }
        if (adjustmentsLabel != null) {
            adjustmentsLabel.setText(String.format("+%s / -%s", formatCurrency(totalPayIn), formatCurrency(totalPayOut)));
        }
        if (adjustmentsCountLabel != null) {
            adjustmentsCountLabel.setText(String.format("%d Pay-Ins, %d Pay-Outs", payInCount, payOutCount));
        }
        if (expectedCashLabel != null) {
            expectedCashLabel.setText(formatCurrency(expectedCash));
        }
        if (auditDetailsLabel != null) {
            auditDetailsLabel.setText(auditLog.toString());
        }

        // Toggle action buttons based on shift status
        if (btnOpenShift != null) btnOpenShift.setDisable(isShiftOpen);
        if (btnPayIn != null) btnPayIn.setDisable(!isShiftOpen);
        if (btnPayOut != null) btnPayOut.setDisable(!isShiftOpen);
        if (btnXReport != null) btnXReport.setDisable(!isShiftOpen);
        if (btnCloseShift != null) btnCloseShift.setDisable(!isShiftOpen);
    }

    @FXML
    public void handleOpenShift(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog("100.00");
        dialog.setTitle("Open New Shift");
        dialog.setHeaderText("Set Starting Cash Float");
        dialog.setContentText("Float Amount ($):");

        dialog.showAndWait().ifPresent(val -> {
            try {
                double amt = Double.parseDouble(val.trim());
                if (amt < 0) {
                    NotificationUtil.showWarning("Invalid Float", "Starting float cannot be negative.");
                    return;
                }
                this.openingFloat = amt;
                this.isShiftOpen = true;
                this.shiftStartTime = Timestamp.valueOf(LocalDateTime.now());
                this.totalPayIn = 0.0;
                this.totalPayOut = 0.0;
                this.payInCount = 0;
                this.payOutCount = 0;
                this.auditLog.setLength(0);
                this.auditLog.append(String.format("New shift opened at %s with %s starting float.\n",
                        LocalDateTime.now().format(TIME_FMT), formatCurrency(amt)));

                recalculateCurrentShift();
                NotificationUtil.showSuccess("Shift Opened", "Cash register shift is now OPEN with " + formatCurrency(amt) + " starting float.");
            } catch (NumberFormatException ex) {
                NotificationUtil.showError("Invalid Input", "Please enter a valid numeric amount.");
            }
        });
    }

    @FXML
    public void handlePayIn(ActionEvent event) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Cash Pay-In (Add Cash)");
        dialog.setHeaderText("Add Cash Float / Change to Drawer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField amtField = new TextField("20.00");
        TextField reasonField = new TextField("Extra change float");
        grid.add(new Label("Amount:"), 0, 0);
        grid.add(amtField, 1, 0);
        grid.add(new Label("Reason / Note:"), 0, 1);
        grid.add(reasonField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    return Double.parseDouble(amtField.getText().trim());
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(amt -> {
            if (amt != null && amt > 0) {
                totalPayIn += amt;
                payInCount++;
                String note = reasonField.getText().trim();
                auditLog.append(String.format("[%s] Pay-In +%s: %s\n",
                        LocalDateTime.now().format(TIME_FMT), formatCurrency(amt), note));
                recalculateCurrentShift();
                NotificationUtil.showSuccess("Pay-In Recorded", "Added " + formatCurrency(amt) + " to drawer.");
            }
        });
    }

    @FXML
    public void handlePayOut(ActionEvent event) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Cash Pay-Out (Withdraw Cash)");
        dialog.setHeaderText("Petty Cash or Supplier Expense");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField amtField = new TextField("15.00");
        TextField reasonField = new TextField("Petty cash / courier delivery");
        grid.add(new Label("Amount:"), 0, 0);
        grid.add(amtField, 1, 0);
        grid.add(new Label("Reason / Note:"), 0, 1);
        grid.add(reasonField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    return Double.parseDouble(amtField.getText().trim());
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(amt -> {
            if (amt != null && amt > 0) {
                totalPayOut += amt;
                payOutCount++;
                String note = reasonField.getText().trim();
                auditLog.append(String.format("[%s] Pay-Out -%s: %s\n",
                        LocalDateTime.now().format(TIME_FMT), formatCurrency(amt), note));
                recalculateCurrentShift();
                NotificationUtil.showSuccess("Pay-Out Recorded", "Withdrew " + formatCurrency(amt) + " from drawer.");
            }
        });
    }

    @FXML
    public void handleXReport(ActionEvent event) {
        double cashSales = 0.0;
        double cardSales = 0.0;
        double grossSales = 0.0;
        double netSales = 0.0;
        int orders = 0;

        if (invoiceDao != null) {
            try {
                List<Invoice> allInvoices = invoiceDao.getAllInvoices();
                for (Invoice inv : allInvoices) {
                    if (shiftStartTime != null && inv.getInvoiceDate() != null) {
                        if (inv.getInvoiceDate().isBefore(shiftStartTime.toLocalDateTime().toLocalDate())) {
                            continue;
                        }
                    }
                    orders++;
                    grossSales += inv.getOrderTotal();
                    netSales += inv.getNetPrice();
                    String method = inv.getPaymentMethod() != null ? inv.getPaymentMethod().toUpperCase() : "CASH";
                    if ("CASH".equals(method)) {
                        cashSales += inv.getNetPrice();
                    } else if ("CREDIT CARD".equals(method) || "DEBIT CARD".equals(method)) {
                        cardSales += inv.getNetPrice();
                    } else {
                        cashSales += inv.getNetPrice() * 0.5;
                        cardSales += inv.getNetPrice() * 0.5;
                    }
                }
            } catch (SQLException ignored) {}
        }

        double expectedCash = openingFloat + cashSales + totalPayIn - totalPayOut;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Mid-Shift X-Report (Non-Resetting Reading)");
        alert.setHeaderText("📊 Current Shift Financial Snapshot");

        String content = String.format(
                "Store: %s\n" +
                "Reading Time: %s\n" +
                "Shift Started: %s\n" +
                "Cashier: %s\n\n" +
                "----------------------------------------\n" +
                "Opening Float:       %s\n" +
                "Total Cash Sales:    %s\n" +
                "Total Card Sales:    %s\n" +
                "Gross Sales:         %s\n" +
                "Net Sales:           %s\n" +
                "Total Invoices:      %d\n" +
                "Pay-Ins (+):         %s (%d entries)\n" +
                "Pay-Outs (-):        %s (%d entries)\n" +
                "----------------------------------------\n" +
                "EXPECTED CASH IN DRAWER: %s\n",
                currentSettings != null ? currentSettings.getStoreName() : "GM Bookstore",
                LocalDateTime.now().format(FULL_FMT),
                shiftStartTime != null ? shiftStartTime.toLocalDateTime().format(TIME_FMT) : "09:00 AM",
                currentCashier,
                formatCurrency(openingFloat),
                formatCurrency(cashSales),
                formatCurrency(cardSales),
                formatCurrency(grossSales),
                formatCurrency(netSales),
                orders,
                formatCurrency(totalPayIn), payInCount,
                formatCurrency(totalPayOut), payOutCount,
                formatCurrency(expectedCash)
        );

        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void handleCloseShift(ActionEvent event) {
        double cashSales = 0.0;
        double cardSales = 0.0;
        double grossSales = 0.0;
        double netSales = 0.0;
        double totalDiscount = 0.0;
        double totalTax = 0.0;
        int orders = 0;

        if (invoiceDao != null) {
            try {
                List<Invoice> allInvoices = invoiceDao.getAllInvoices();
                for (Invoice inv : allInvoices) {
                    if (shiftStartTime != null && inv.getInvoiceDate() != null) {
                        if (inv.getInvoiceDate().isBefore(shiftStartTime.toLocalDateTime().toLocalDate())) {
                            continue;
                        }
                    }
                    orders++;
                    grossSales += inv.getOrderTotal();
                    netSales += inv.getNetPrice();
                    totalDiscount += inv.getDiscount();
                    totalTax += inv.getTaxAmount();
                    String method = inv.getPaymentMethod() != null ? inv.getPaymentMethod().toUpperCase() : "CASH";
                    if ("CASH".equals(method)) {
                        cashSales += inv.getNetPrice();
                    } else if ("CREDIT CARD".equals(method) || "DEBIT CARD".equals(method)) {
                        cardSales += inv.getNetPrice();
                    } else {
                        cashSales += inv.getNetPrice() * 0.5;
                        cardSales += inv.getNetPrice() * 0.5;
                    }
                }
            } catch (SQLException ignored) {}
        }

        double expectedCash = openingFloat + cashSales + totalPayIn - totalPayOut;

        TextInputDialog dialog = new TextInputDialog(String.format("%.2f", expectedCash));
        dialog.setTitle("End-of-Day Shift Closure (Z-Report)");
        dialog.setHeaderText("Enter Physical Cash Counted in Drawer\n(Expected: " + formatCurrency(expectedCash) + ")");
        dialog.setContentText("Actual Physical Cash ($):");

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) return;

        try {
            double actualCash = Double.parseDouble(result.get().trim());
            double overShort = actualCash - expectedCash;

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            int seq = zReportDao != null ? zReportDao.getNextSequenceNumber() : 1;
            double prevCumulative = zReportDao != null ? zReportDao.getLatestCumulativeGrandTotal() : 0.0;
            double cumulative = prevCumulative + netSales;

            String notes = String.format("PayIns: %s, PayOuts: %s. %s",
                    formatCurrency(totalPayIn), formatCurrency(totalPayOut),
                    overShort == 0 ? "Exact drawer balance." : (overShort > 0 ? "Cash OVER by " + formatCurrency(overShort) : "Cash SHORT by " + formatCurrency(Math.abs(overShort))));

            ZReport report = new ZReport(
                    seq,
                    shiftStartTime != null ? shiftStartTime : now,
                    now,
                    openingFloat,
                    cashSales,
                    cardSales,
                    0.0,
                    grossSales,
                    totalDiscount,
                    netSales,
                    totalTax,
                    netSales,
                    orders,
                    expectedCash,
                    actualCash,
                    overShort,
                    cumulative,
                    currentCashier,
                    notes
            );
            report.setReportTimestamp(now);

            if (zReportDao != null) {
                zReportDao.saveZReport(report);
            }

            this.isShiftOpen = false;
            this.auditLog.append(String.format("Shift CLOSED at %s. Z-Report #%d generated. Over/Short: %s\n",
                    now.toLocalDateTime().format(TIME_FMT), seq, formatCurrency(overShort)));

            loadHistoricalZReports();
            recalculateCurrentShift();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Fiscal Shift Closed");
            alert.setHeaderText("🔒 Z-Report #" + seq + " Generated Successfully");
            alert.setContentText(String.format(
                    "Fiscal Closure Complete!\n\n" +
                    "Z-Sequence Number:  #%d\n" +
                    "Closing Time:       %s\n" +
                    "Expected Cash:      %s\n" +
                    "Actual Counted:     %s\n" +
                    "Variance:           %s\n" +
                    "Cumulative Total:   %s\n",
                    seq, now.toLocalDateTime().format(FULL_FMT),
                    formatCurrency(expectedCash), formatCurrency(actualCash),
                    (overShort >= 0 ? "+" : "") + formatCurrency(overShort),
                    formatCurrency(cumulative)
            ));
            alert.showAndWait();

        } catch (NumberFormatException ex) {
            NotificationUtil.showError("Invalid Amount", "Please enter a valid numeric cash amount.");
        } catch (SQLException ex) {
            NotificationUtil.showError("Save Failed", "Failed to save Z-Report to database: " + ex.getMessage());
        }
    }

    @FXML
    public void handleRefreshAudit(ActionEvent event) {
        recalculateCurrentShift();
        NotificationUtil.showSuccess("Refreshed", "Live drawer calculations updated.");
    }

    @FXML
    public void handleExportZReportsCsv(ActionEvent event) {
        if (historicalReports.isEmpty()) {
            NotificationUtil.showWarning("No Data", "No Z-Reports available to export.");
            return;
        }

        File exportFile = new File(System.getProperty("user.home"), "Z_Reports_Audit_" + System.currentTimeMillis() + ".csv");
        try (FileWriter writer = new FileWriter(exportFile)) {
            writer.write("Z_Sequence,Timestamp,Period_Start,Period_End,Opening_Float,Cash_Sales,Card_Sales,Net_Sales,Expected_Cash,Actual_Cash,Over_Short,Closed_By,Notes\n");
            for (ZReport r : historicalReports) {
                writer.write(String.format("%d,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,\"%s\"\n",
                        r.getZSequenceNumber(),
                        r.getReportTimestamp() != null ? r.getReportTimestamp().toString() : "",
                        r.getPeriodStart() != null ? r.getPeriodStart().toString() : "",
                        r.getPeriodEnd() != null ? r.getPeriodEnd().toString() : "",
                        r.getOpeningFloat(),
                        r.getTotalCashSales(),
                        r.getTotalCardSales(),
                        r.getNetSales(),
                        r.getExpectedDrawerCash(),
                        r.getActualDrawerCash(),
                        r.getCashOverShort(),
                        r.getClosedByUser() != null ? r.getClosedByUser() : "",
                        r.getNotes() != null ? r.getNotes().replace("\"", "\"\"") : ""
                ));
            }
            NotificationUtil.showSuccess("Export Complete", "Z-Reports CSV saved to:\n" + exportFile.getAbsolutePath());
        } catch (IOException ex) {
            NotificationUtil.showError("Export Failed", "Failed to write CSV: " + ex.getMessage());
        }
    }

    private String formatCurrency(double amount) {
        String sym = currentSettings != null ? currentSettings.getCurrencySymbol() : "$";
        return String.format("%s %.2f", sym, amount);
    }

    public boolean isShiftOpen() {
        return isShiftOpen;
    }

    public double getOpeningFloat() {
        return openingFloat;
    }

    public double getTotalPayIn() {
        return totalPayIn;
    }

    public double getTotalPayOut() {
        return totalPayOut;
    }
}
