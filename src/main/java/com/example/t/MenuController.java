package com.example.t;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for application navigation menus (Menu.fxml and menu_screen.fxml).
 * Provides event handlers for opening the respective application screens.
 */
public class MenuController {

    @FXML private Button addStockButton;
    @FXML private Button viewStockButton;
    @FXML private Button createOrderButton;
    @FXML private Button viewOrderButton;
    @FXML private Button viewInvoicesButton;
    @FXML private Button exitButton;
    @FXML private Button closeButton;
    @FXML private VBox centerBox;

    @FXML
    public void initialize() {
        Logger.info("MenuController initialized.");
    }

    @FXML
    public void addStockButtonClicked(ActionEvent event) {
        try {
            Stock stock = new Stock();
            AddStockScreen addStockScreen = new AddStockScreen(stock);
            addStockScreen.show();
        } catch (Exception e) {
            Logger.error("Failed to open AddStockScreen", e);
            NotificationUtil.showError("Error", "Could not open Add Stock screen: " + e.getMessage());
        }
    }

    @FXML
    public void viewStockButtonClicked(ActionEvent event) {
        try {
            ViewStockScreen viewStockScreen = new ViewStockScreen();
            viewStockScreen.show();
        } catch (Exception e) {
            Logger.error("Failed to open ViewStockScreen", e);
            NotificationUtil.showError("Error", "Could not open View Stock screen: " + e.getMessage());
        }
    }

    @FXML
    public void createOrderButtonClicked(ActionEvent event) {
        try {
            Stock stock = new Stock();
            Shop shop = new Shop("Shop Name", "Address", "0300 000000");
            CreateOrderScreen createOrderScreen = new CreateOrderScreen(stock, shop);
            createOrderScreen.show();
        } catch (Exception e) {
            Logger.error("Failed to open CreateOrderScreen", e);
            NotificationUtil.showError("Error", "Could not open Create Order screen: " + e.getMessage());
        }
    }

    @FXML
    public void viewOrderButtonClicked(ActionEvent event) {
        try {
            ViewOrderScreen viewOrderScreen = new ViewOrderScreen();
            viewOrderScreen.show();
        } catch (Exception e) {
            Logger.error("Failed to open ViewOrderScreen", e);
            NotificationUtil.showError("Error", "Could not open View Order screen: " + e.getMessage());
        }
    }

    @FXML
    public void viewInvoicesButtonClicked(ActionEvent event) {
        try {
            ViewInvoicesScreen viewInvoicesScreen = new ViewInvoicesScreen();
            viewInvoicesScreen.show();
        } catch (Exception e) {
            Logger.error("Failed to open ViewInvoicesScreen", e);
            NotificationUtil.showError("Error", "Could not open View Invoices screen: " + e.getMessage());
        }
    }

    @FXML
    public void exitButtonClicked(ActionEvent event) {
        if (event != null && event.getSource() instanceof Node node) {
            Stage stage = (Stage) node.getScene().getWindow();
            if (stage != null) {
                stage.close();
                return;
            }
        }
        Platform.exit();
    }
}
