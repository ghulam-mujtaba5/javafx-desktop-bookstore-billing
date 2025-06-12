package com.example.t;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.application.Platform;
import java.io.IOException;

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
        // Set up button actions
        btnAddStock.setOnAction(e -> loadScreen("AddStockScreen.fxml", "Add Stock"));
        btnViewStock.setOnAction(e -> loadScreen("ViewStockScreen.fxml", "View Stock"));
        btnUpdateStock.setOnAction(e -> loadScreen("UpdateStockScreen.fxml", "Update Stock"));
        btnCreateOrder.setOnAction(e -> loadScreen("CreateOrderScreen.fxml", "Create Order"));
        btnViewOrder.setOnAction(e -> loadScreen("ViewOrderScreen.fxml", "View Orders"));
        btnViewInvoices.setOnAction(e -> loadScreen("ViewInvoicesScreen.fxml", "View Invoices"));
        btnSettings.setOnAction(e -> loadScreen("SettingsScreen.fxml", "Settings"));
        btnLogout.setOnAction(e -> logout());

        // Load default screen
        Platform.runLater(() -> loadScreen("ViewStockScreen.fxml", "View Stock"));
    }

    private void loadScreen(String fxmlName, String title) {
        try {
            String fxmlPath = "/com/example/t/" + fxmlName;
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node screen = loader.load();
            
            contentArea.getChildren().setAll(screen);
            
            // Update window title
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setTitle("Bookstore Management - " + title);
            
        } catch (IOException e) {
            e.printStackTrace();
            NotificationUtil.showError("Error", "Failed to load " + title + " screen: " + e.getMessage());
        }
    }

    private void logout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Logout");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to logout?");
        alert.initOwner(contentArea.getScene().getWindow());
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Save any pending changes or cleanup
                    FileHandler.getInstance().saveAllChanges();
                    
                    NotificationUtil.showSuccess("Logout", "Successfully logged out");
                    
                    // Close the main window
                    Platform.runLater(() -> {
                        Stage stage = (Stage) contentArea.getScene().getWindow();
                        stage.close();
                        
                        // Show login screen
                        try {
                            Stage loginStage = new Stage();
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/Password_Screen.fxml"));
                            Scene scene = new Scene(loader.load());
                            loginStage.setScene(scene);
                            loginStage.setTitle("Bookstore Management - Login");
                            loginStage.show();
                        } catch (IOException e) {
                            e.printStackTrace();
                            NotificationUtil.showError("Error", "Failed to load login screen");
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    NotificationUtil.showError("Error", "Failed to logout properly: " + e.getMessage());
                }
            }
        });
    }
}
