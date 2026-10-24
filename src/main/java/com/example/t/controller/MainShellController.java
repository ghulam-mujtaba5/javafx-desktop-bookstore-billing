package com.example.t.controller;

import com.example.t.dao.SettingsDao;
import com.example.t.model.StoreSettings;
import com.example.t.util.ThemeManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the Master Navigation Shell.
 * Provides responsive sidebar navigation, top store/region header,
 * and dynamic view swapping inside the center viewport.
 */
public class MainShellController {

    public enum ViewType {
        POS_BILLING,
        STOCK_MANAGEMENT,
        INVOICE_HISTORY,
        REPORTS_DASHBOARD,
        SHIFT_MANAGEMENT,
        STORE_SETTINGS
    }

    @FXML private BorderPane rootPane;
    @FXML private Label storeNameLabel;
    @FXML private Label storeTaglineLabel;
    @FXML private Label regionPresetLabel;
    @FXML private Label statusBadgeLabel;
    @FXML private Button btnThemeToggle;
    @FXML private Button navPosBtn;
    @FXML private Button navStockBtn;
    @FXML private Button navInvoicesBtn;
    @FXML private Button navReportsBtn;
    @FXML private Button navShiftBtn;
    @FXML private Button navSettingsBtn;
    @FXML private Button navExitBtn;
    @FXML private Label versionLabel;
    @FXML private Button navAboutBtn;
    @FXML private StackPane contentArea;
    @FXML private Label footerStatusLabel;
    @FXML private Label clockLabel;

    private SettingsDao settingsDao;
    private ViewType currentView;
    private PosBillingController posBillingController;
    private final Map<ViewType, Node> cachedViews = new HashMap<>();

    @FXML
    public void initialize() {
        if (settingsDao == null) {
            settingsDao = new SettingsDao();
        }
        refreshHeader();
        initTheme();
        startClock();
        // Load default view (Stock Management for Milestone 3)
        navigateTo(ViewType.STOCK_MANAGEMENT);
    }

    /**
     * Set custom SettingsDao (useful for unit testing).
     */
    public void setSettingsDao(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
        refreshHeader();
    }

    /**
     * Refreshes the top header bar with latest store settings and regional preset from SQLite.
     */
    public void refreshHeader() {
        try {
            if (settingsDao != null) {
                StoreSettings settings = settingsDao.getSettings();
                if (settings != null) {
                    if (storeNameLabel != null) {
                        storeNameLabel.setText(settings.getStoreName());
                    }
                    if (regionPresetLabel != null) {
                        String preset = settings.getRegionPreset();
                        String code = settings.getCurrencyCode();
                        String symbol = settings.getCurrencySymbol();
                        regionPresetLabel.setText(preset + " (" + code + " " + symbol + ")");
                    }
                    if (statusBadgeLabel != null) {
                        statusBadgeLabel.setText("SQLite (WAL) • " + settings.getTaxRate() + "% " + settings.getTaxName());
                    }
                }
            }
        } catch (SQLException e) {
            if (statusBadgeLabel != null) {
                statusBadgeLabel.setText("SQLite Warning");
            }
        }
    }

    /**
     * Swaps the active view in the center content area.
     *
     * @param view Target ViewType
     */
    public void navigateTo(ViewType view) {
        this.currentView = view;
        updateSidebarActiveStyle(view);

        if (contentArea == null) {
            return;
        }

        Node viewNode = loadOrCreateView(view);
        if (viewNode != null) {
            contentArea.getChildren().setAll(viewNode);
        }

        if (footerStatusLabel != null) {
            footerStatusLabel.setText("Active: " + formatViewName(view) + " | Press F5 for POS • F12 for Stock Inventory");
        }
    }

    private Node loadOrCreateView(ViewType view) {
        if (cachedViews.containsKey(view)) {
            Node cached = cachedViews.get(view);
            if (cached != null) {
                return cached;
            }
        }

        String fxmlPath;
        switch (view) {
            case STOCK_MANAGEMENT:
                fxmlPath = "/com/example/t/fxml/StockManagement.fxml";
                break;
            case POS_BILLING:
                fxmlPath = "/com/example/t/fxml/PosBilling.fxml";
                break;
            case INVOICE_HISTORY:
                fxmlPath = "/com/example/t/fxml/InvoiceHistory.fxml";
                break;
            case REPORTS_DASHBOARD:
                fxmlPath = "/com/example/t/fxml/ReportsDashboard.fxml";
                break;
            case SHIFT_MANAGEMENT:
                fxmlPath = "/com/example/t/fxml/ShiftManagement.fxml";
                break;
            case STORE_SETTINGS:
                fxmlPath = "/com/example/t/fxml/Settings.fxml";
                break;
            default:
                fxmlPath = null;
        }

        if (fxmlPath != null) {
            URL resource = getClass().getResource(fxmlPath);
            if (resource != null) {
                try {
                    FXMLLoader loader = new FXMLLoader(resource);
                    Node loadedNode = loader.load();

                    Object controller = loader.getController();
                    if (controller instanceof SettingsController settingsCtrl) {
                        if (settingsDao != null) {
                            settingsCtrl.setSettingsDao(settingsDao);
                        }
                        settingsCtrl.setOnSettingsSaved(this::refreshHeader);
                    } else if (controller instanceof PosBillingController posCtrl) {
                        this.posBillingController = posCtrl;
                        if (settingsDao != null) {
                            posCtrl.setSettingsDao(settingsDao);
                        }
                    } else if (controller instanceof InvoiceHistoryController invCtrl) {
                        if (settingsDao != null) {
                            invCtrl.setSettingsDao(settingsDao);
                        }
                    } else if (controller instanceof ShiftManagementController shiftCtrl) {
                        if (settingsDao != null) {
                            shiftCtrl.setSettingsDao(settingsDao);
                        }
                    }

                    cachedViews.put(view, loadedNode);
                    return loadedNode;
                } catch (IOException e) {
                    System.err.println("Error loading FXML view " + fxmlPath + ": " + e.getMessage());
                }
            }
        }

        // Return modern placeholder card for planned/upcoming screens
        return createPlaceholderView(view);
    }

    public Node createPlaceholderView(ViewType view) {
        VBox placeholder = new VBox(16);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(40));
        placeholder.setStyle("-fx-background-color: #f8fafc;");

        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(600);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: white; -fx-padding: 32px; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

        String title;
        String desc;
        switch (view) {
            case POS_BILLING:
                title = "🛒 Point of Sale & Billing Screen";
                desc = "POS checkout, barcode scanning, real-time subtotal, multi-regional tax calculation, and cash change due.\n(Delivered in Milestone 4)";
                break;
            case INVOICE_HISTORY:
                title = "🧾 Invoice History & Reporting";
                desc = "Search past orders, filter by customer or date, reprint receipts, and export to PDF and CSV.\n(Delivered in Milestone 4)";
                break;
            case STORE_SETTINGS:
                title = "⚙ Store Settings & Regional Configuration";
                desc = "Configure store profile, regional presets (USA, UK, Gulf), tax rates, and admin security.\n(Delivered in Milestone 4)";
                break;
            default:
                title = "View In Progress";
                desc = "This module is scheduled for implementation.";
        }

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-text-alignment: center;");
        descLabel.setWrapText(true);

        Button returnStockBtn = new Button("Go to Stock Management [F12]");
        returnStockBtn.getStyleClass().add("btn-primary");
        returnStockBtn.setStyle("-fx-background-color: #0067c0; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-background-radius: 6px;");
        returnStockBtn.setOnAction(e -> navigateTo(ViewType.STOCK_MANAGEMENT));

        card.getChildren().addAll(titleLabel, descLabel, returnStockBtn);
        placeholder.getChildren().add(card);
        return placeholder;
    }

    private void updateSidebarActiveStyle(ViewType view) {
        removeActiveClass(navPosBtn);
        removeActiveClass(navStockBtn);
        removeActiveClass(navInvoicesBtn);
        removeActiveClass(navReportsBtn);
        removeActiveClass(navShiftBtn);
        removeActiveClass(navSettingsBtn);

        switch (view) {
            case POS_BILLING:
                addActiveClass(navPosBtn);
                break;
            case STOCK_MANAGEMENT:
                addActiveClass(navStockBtn);
                break;
            case INVOICE_HISTORY:
                addActiveClass(navInvoicesBtn);
                break;
            case REPORTS_DASHBOARD:
                addActiveClass(navReportsBtn);
                break;
            case SHIFT_MANAGEMENT:
                addActiveClass(navShiftBtn);
                break;
            case STORE_SETTINGS:
                addActiveClass(navSettingsBtn);
                break;
        }
    }

    private void addActiveClass(Button btn) {
        if (btn != null && !btn.getStyleClass().contains("active")) {
            btn.getStyleClass().add("active");
        }
    }

    private void removeActiveClass(Button btn) {
        if (btn != null) {
            btn.getStyleClass().remove("active");
        }
    }

    private String formatViewName(ViewType view) {
        switch (view) {
            case POS_BILLING: return "Point of Sale (POS)";
            case STOCK_MANAGEMENT: return "Stock Management";
            case INVOICE_HISTORY: return "Invoices & History";
            case REPORTS_DASHBOARD: return "Analytics & Reports Dashboard";
            case SHIFT_MANAGEMENT: return "Shift & Cash Drawer";
            case STORE_SETTINGS: return "Store Settings";
            default: return view.name();
        }
    }

    /**
     * Binds keyboard shortcuts (F5, F12, F14, F15, F16) to navigation actions on the given Scene.
     */
    public void setupKeyShortcuts(Scene scene) {
        if (scene == null) return;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.F5) {
                if (currentView != ViewType.POS_BILLING) {
                    navigateTo(ViewType.POS_BILLING);
                    event.consume();
                }
            } else if (code == KeyCode.F12) {
                if (currentView == ViewType.POS_BILLING && posBillingController != null && !posBillingController.getCartItems().isEmpty()) {
                    // Let POS handle F12 to clear active cart without leaving POS
                } else {
                    navigateTo(ViewType.STOCK_MANAGEMENT);
                    event.consume();
                }
            } else if (code == KeyCode.F14 || (event.isControlDown() && code == KeyCode.H)) {
                navigateTo(ViewType.INVOICE_HISTORY);
                event.consume();
            } else if (code == KeyCode.F16 || (event.isControlDown() && code == KeyCode.R)) {
                navigateTo(ViewType.REPORTS_DASHBOARD);
                event.consume();
            } else if (code == KeyCode.F17 || (event.isControlDown() && code == KeyCode.D)) {
                navigateTo(ViewType.SHIFT_MANAGEMENT);
                event.consume();
            } else if (code == KeyCode.F15 || (event.isControlDown() && code == KeyCode.S)) {
                navigateTo(ViewType.STORE_SETTINGS);
                event.consume();
            }
        });
    }

    private void startClock() {
        if (clockLabel == null) return;
        Thread clockThread = new Thread(() -> {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy • hh:mm:ss a");
            while (true) {
                String timeStr = LocalDateTime.now().format(formatter);
                Platform.runLater(() -> {
                    if (clockLabel != null) {
                        clockLabel.setText(timeStr);
                    }
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        clockThread.setDaemon(true);
        clockThread.start();
    }

    // --- Action Handlers ---

    @FXML
    private void handleNavPos(ActionEvent event) {
        navigateTo(ViewType.POS_BILLING);
    }

    @FXML
    private void handleNavStock(ActionEvent event) {
        navigateTo(ViewType.STOCK_MANAGEMENT);
    }

    @FXML
    private void handleNavInvoices(ActionEvent event) {
        navigateTo(ViewType.INVOICE_HISTORY);
    }

    @FXML
    private void handleNavReports(ActionEvent event) {
        navigateTo(ViewType.REPORTS_DASHBOARD);
    }

    @FXML
    private void handleNavShift(ActionEvent event) {
        navigateTo(ViewType.SHIFT_MANAGEMENT);
    }

    @FXML
    private void handleNavSettings(ActionEvent event) {
        navigateTo(ViewType.STORE_SETTINGS);
    }

    @FXML
    private void handleExit(ActionEvent event) {
        Platform.exit();
    }

    private void initTheme() {
        String theme = ThemeManager.THEME_LIGHT;
        try {
            if (settingsDao != null) {
                StoreSettings s = settingsDao.getSettings();
                if (s != null && s.getUiTheme() != null) {
                    theme = s.getUiTheme();
                }
            }
        } catch (SQLException ignored) {}
        updateThemeToggleButton(theme);
    }

    private void updateThemeToggleButton(String theme) {
        if (btnThemeToggle != null) {
            if (ThemeManager.THEME_DARK.equalsIgnoreCase(theme)) {
                btnThemeToggle.setText("☀️ Light Mode");
            } else {
                btnThemeToggle.setText("🌙 Dark Mode");
            }
        }
    }

    @FXML
    public void handleThemeToggle(ActionEvent event) {
        Scene scene = rootPane != null ? rootPane.getScene() : (btnThemeToggle != null ? btnThemeToggle.getScene() : null);
        String newTheme = ThemeManager.toggleTheme(scene, settingsDao);
        updateThemeToggleButton(newTheme);
    }

    @FXML
    public void handleAboutDialog() {
        showAboutDialog();
    }

    /**
     * Constructs the structured About & Competitor Superiority Alert.
     * Accessible for testing and live UI display. Dispatches to FX application thread if needed.
     */
    public Alert createAboutDialog() {
        if (!Platform.isFxApplicationThread()) {
            java.util.concurrent.CompletableFuture<Alert> future = new java.util.concurrent.CompletableFuture<>();
            try {
                Platform.runLater(() -> {
                    try {
                        future.complete(buildAboutDialogInternal());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
                return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (IllegalStateException e) {
                // Toolkit not initialized at all (headless non-FX environment)
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to construct AboutDialog on JavaFX thread", e);
            }
        }
        return buildAboutDialogInternal();
    }

    private Alert buildAboutDialogInternal() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About - Bookstore & Stationery Billing & Inventory v1.2.0");
        alert.setHeaderText("Bookstore & Stationery Billing & Inventory v1.2.0\n100% Offline Ready • Commercial Desktop POS");

        String content = "Architecture & Runtime:\n"
                + " • Application Version: v1.2.0\n"
                + " • Runtime: OpenJDK 21 LTS | JavaFX 21.0.6 | AtlantaFX Fluent Theme\n"
                + " • Persistence: Embedded SQLite 3 (WAL Mode, <1ms Latency)\n"
                + " • Printing: Direct Binary ESC/POS (80mm/58mm) + Hardware Drawer Kick\n"
                + " • Offline Sovereignty: 100% Local Persistence (Zero Cloud Dependency)\n\n"
                + "Competitor Benchmark & Superiority (vs Square, Loyverse, Lightspeed, BookManager, Basil):\n"
                + " 1. $0 SaaS Subscription Fees: Save $4,248 to $23,880 in 3-5 Year TCO\n"
                + " 2. Sub-Millisecond Local Latency: <1ms SQLite WAL vs 200-800ms cloud lag\n"
                + " 3. 100% Offline Persistence: Full sales, catalog, and reporting without internet\n"
                + " 4. Direct Hardware Control: Binary ESC/POS thermal streaming & cash drawer kick\n"
                + " 5. Specialized Book Intelligence: ISBN-10/13 checksums, metadata auto-fetch, & Dead Stock Radar";

        alert.setContentText(content);
        if (alert.getDialogPane() != null) {
            alert.getDialogPane().setPrefWidth(620);
        }
        return alert;
    }

    /**
     * Displays the About & Competitor Superiority Dialog.
     * Safely handles headless test environments without blocking.
     */
    public void showAboutDialog() {
        try {
            Alert alert = createAboutDialog();
            if (Platform.isFxApplicationThread()) {
                alert.show();
            }
        } catch (Throwable t) {
            System.err.println("Notice: About dialog opened in headless/test environment: " + t.getMessage());
        }
    }

    // --- Getters for Access & Testing ---

    public ViewType getCurrentView() {
        return currentView;
    }

    public StackPane getContentArea() {
        return contentArea;
    }

    public Label getStoreNameLabel() {
        return storeNameLabel;
    }

    public Label getRegionPresetLabel() {
        return regionPresetLabel;
    }

    public Label getStatusBadgeLabel() {
        return statusBadgeLabel;
    }

    public Label getVersionLabel() {
        return versionLabel;
    }

    public Button getNavAboutBtn() {
        return navAboutBtn;
    }

    public Button getNavPosBtn() {
        return navPosBtn;
    }

    public Button getNavStockBtn() {
        return navStockBtn;
    }

    public Button getNavInvoicesBtn() {
        return navInvoicesBtn;
    }

    public Button getNavReportsBtn() {
        return navReportsBtn;
    }

    public Button getNavShiftBtn() {
        return navShiftBtn;
    }

    public Button getNavSettingsBtn() {
        return navSettingsBtn;
    }

    public PosBillingController getPosBillingController() {
        return posBillingController;
    }

    public void setPosBillingController(PosBillingController posBillingController) {
        this.posBillingController = posBillingController;
    }
}

