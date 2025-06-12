package com.example.t;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class MenuController {
    @FXML private Button btnAddStock;
    @FXML private Button btnViewStock;
    @FXML private Button btnUpdateStock;
    @FXML private Button btnCreateOrder;
    @FXML private Button btnViewOrder;
    @FXML private Button btnViewInvoices;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private StackPane contentArea;

    @FXML
    private void initialize() {


        btnAddStock.setOnAction(e -> loadScreen("/com/example/t/AddStockScreen.fxml"));
        btnViewStock.setOnAction(e -> loadScreen("/com/example/t/ViewStockScreen.fxml"));
        btnUpdateStock.setOnAction(e -> loadScreen("/com/example/t/UpdateStockScreen.fxml"));
        btnCreateOrder.setOnAction(e -> loadScreen("/com/example/t/CreateOrderScreen.fxml"));
        btnViewOrder.setOnAction(e -> loadScreen("/com/example/t/ViewOrderScreen.fxml"));
        btnViewInvoices.setOnAction(e -> loadScreen("/com/example/t/ViewInvoicesScreen.fxml"));
        btnSettings.setOnAction(e -> loadScreen("/com/example/t/SettingsScreen.fxml"));
        btnLogout.setOnAction(e -> logout());
        // Load default screen
        loadScreen("/com/example/t/ViewStockScreen.fxml");
    }

    private void loadScreen(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node screen = loader.load();
            contentArea.getChildren().setAll(screen);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logout() {
        // Confirmation dialog for logout
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to logout?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Confirm Logout");
        alert.setHeaderText(null);
        alert.initOwner(contentArea.getScene().getWindow());
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                NotificationUtil.showToast(contentArea.getScene(), "Logged out successfully.");
                contentArea.getScene().getWindow().hide();
            } else {
                NotificationUtil.showToast(contentArea.getScene(), "Logout cancelled.");
            }
        });
    }
}
