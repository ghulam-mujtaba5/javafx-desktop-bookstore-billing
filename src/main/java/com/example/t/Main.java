package com.example.t;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.example.t.controller.MainShellController;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.StoreSettings;
import com.example.t.util.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Main application entry point for Modern Offline JavaFX Desktop Bookstore & Stationery Billing.
 * Initializes SQLite DatabaseManager, applies AtlantaFX Windows 11 Fluent theme,
 * and loads the unified MainShell single-stage navigation window.
 */
public class Main extends Application {

    private Stage primaryStage;
    private MainShellController shellController;
    private Shop shop;
    private Stock stock;
    private Password password;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. Initialize embedded SQLite database, schema migrations & sample catalog seeding
        try {
            DatabaseManager.initializeDatabase();
        } catch (Exception e) {
            System.err.println("Database initialization warning: " + e.getMessage());
        }

        // 2. Apply AtlantaFX modern Fluent theme from saved settings
        SettingsDao settingsDao = new SettingsDao();
        String initialTheme = ThemeManager.THEME_LIGHT;
        try {
            StoreSettings s = settingsDao.getSettings();
            if (s != null && s.getUiTheme() != null) {
                initialTheme = s.getUiTheme();
            }
        } catch (Throwable t) {
            System.err.println("Could not load saved theme preference: " + t.getMessage());
        }
        ThemeManager.applyTheme(null, initialTheme, null);

        // 3. Load Master Navigation Shell as primary single-window stage
        loadMainShell();

        primaryStage.setOnCloseRequest(event -> {
            if (shop != null) {
                shop.saveData();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    /**
     * Loads the unified Master Navigation Shell into the primary stage.
     */
    public void loadMainShell() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
            Parent root = loader.load();
            shellController = loader.getController();

            Scene scene = new Scene(root, 1280, 800);

            // Attach modern-fluent.css stylesheet
            URL cssUrl = getClass().getResource("/com/example/t/css/modern-fluent.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            // Apply saved theme to Scene
            ThemeManager.applyTheme(scene, ThemeManager.getCurrentTheme(), null);

            // Bind keyboard shortcuts (F5, F12, F14, F15)
            if (shellController != null) {
                shellController.setupKeyShortcuts(scene);
            }

            // Responsive font scaling across 1366x768, 1080p, and 2K/4K displays
            scene.widthProperty().addListener((obs, oldW, newW) -> updateResponsiveStyles(root, newW.doubleValue(), scene.getHeight()));
            scene.heightProperty().addListener((obs, oldH, newH) -> updateResponsiveStyles(root, scene.getWidth(), newH.doubleValue()));
            updateResponsiveStyles(root, 1280, 800);

            primaryStage.setTitle("Bookstore & Stationery Billing & Inventory v1.2.0");
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(640);
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();

        } catch (IOException e) {
            System.err.println("Failed to load MainShell.fxml: " + e.getMessage());
            e.printStackTrace();
            // Fallback to legacy menu screen if FXML fails
            showLegacyMenuScreen();
        }
    }

    private void updateResponsiveStyles(Parent root, double width, double height) {
        if (root == null) return;
        double baseFontSize;
        if (width < 1380 || height < 760) {
            baseFontSize = 12.0; // Compact laptop / 1366x768 screen
            root.getStyleClass().removeAll("screen-fhd", "screen-4k");
            if (!root.getStyleClass().contains("screen-compact")) {
                root.getStyleClass().add("screen-compact");
            }
        } else if (width <= 2100) {
            baseFontSize = 13.5; // Standard FHD 1080p desktop
            root.getStyleClass().removeAll("screen-compact", "screen-4k");
            if (!root.getStyleClass().contains("screen-fhd")) {
                root.getStyleClass().add("screen-fhd");
            }
        } else {
            baseFontSize = 16.0; // Large 2K/4K UHD monitor
            root.getStyleClass().removeAll("screen-compact", "screen-fhd");
            if (!root.getStyleClass().contains("screen-4k")) {
                root.getStyleClass().add("screen-4k");
            }
        }
        root.setStyle("-fx-font-size: " + baseFontSize + "px;");
    }

    // --- Legacy Screen Compatibility Methods ---

    public void showMenuScreen() {
        if (primaryStage != null) {
            loadMainShell();
        }
    }

    private void showLegacyMenuScreen() {
        initializeStockAndShopObjects();
        primaryStage.setTitle("Bookstore POS (Fallback)");
        primaryStage.show();
    }

    public void openSettingsScreen() {
        if (shellController != null) {
            shellController.navigateTo(MainShellController.ViewType.STORE_SETTINGS);
        } else {
            SettingsScreen settingsScreen = new SettingsScreen(password != null ? password : new Password());
            settingsScreen.openSettingsScreen(primaryStage, shop != null ? shop : new Shop("Shop Name", "Address", "0300 000000"));
        }
    }

    public void openAddStockScreen() {
        if (shellController != null) {
            shellController.navigateTo(MainShellController.ViewType.STOCK_MANAGEMENT);
        } else {
            AddStockScreen addStockScreen = new AddStockScreen(stock != null ? stock : new Stock());
            addStockScreen.show();
        }
    }

    public void openViewStockScreen() {
        if (shellController != null) {
            shellController.navigateTo(MainShellController.ViewType.STOCK_MANAGEMENT);
        } else {
            ViewStockScreen viewStockScreen = new ViewStockScreen();
            viewStockScreen.show();
        }
    }

    public void openUpdateStockScreen() {
        if (shellController != null) {
            shellController.navigateTo(MainShellController.ViewType.STOCK_MANAGEMENT);
        } else {
            UpdateStockScreen updateStockScreen = new UpdateStockScreen();
            updateStockScreen.show();
        }
    }

    public void openCreateOrderScreen() {
        if (shellController != null) {
            shellController.navigateTo(MainShellController.ViewType.POS_BILLING);
        } else {
            initializeStockAndShopObjects();
            CreateOrderScreen createOrderScreen = new CreateOrderScreen(stock, shop);
            createOrderScreen.show();
        }
    }

    public void openViewOrderScreen() {
        ViewOrderScreen viewOrderScreen = new ViewOrderScreen();
        viewOrderScreen.show();
    }

    public void openViewInvoicesScreen() {
        if (shellController != null) {
            shellController.navigateTo(MainShellController.ViewType.INVOICE_HISTORY);
        } else {
            ViewInvoicesScreen viewInvoicesScreen = new ViewInvoicesScreen();
            viewInvoicesScreen.show();
        }
    }

    public void openPasswordScreen() {
        PasswordScreen passwordScreen = new PasswordScreen(password != null ? password : new Password());
        passwordScreen.setOnPasswordCorrect(this::showMenuScreen);
        Stage passwordStage = new Stage();
        passwordScreen.start(passwordStage);
        if (primaryStage != null) {
            primaryStage.close();
        }
    }

    private void initializeStockAndShopObjects() {
        if (stock == null) stock = new Stock();
        if (shop == null) shop = new Shop("Shop Name", "Address", "0300 000000");
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public MainShellController getShellController() {
        return shellController;
    }
}
