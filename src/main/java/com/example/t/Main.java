package com.example.t;

// Removed unused imports
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
// Removed unused import

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;


    public class Main extends Application {
        private Stage primaryStage;
        private Shop shop;
        private Stock stock;
        private Password password;

        public static void main(String[] args) {
            launch(args);
        }

        @Override
        public void start(Stage primaryStage) {
            this.primaryStage = primaryStage;

            password = new Password(); // Initialize the password object

            PasswordScreen passwordScreen = new PasswordScreen(password);
            passwordScreen.setOnPasswordCorrect(() -> {
                System.out.println("Password correct, loading menu...");
                showMenuScreen();
                System.out.println("Menu loaded!");
                primaryStage.show();
            });

            passwordScreen.start(primaryStage);
            primaryStage.setOnShown(event -> primaryStage.setMaximized(true));
            primaryStage.setOnCloseRequest(event -> {
                if (shop != null) {
                    shop.saveData(); // Save shop data to file before exiting
                }
                // System.exit(0); // Remove this to prevent app from closing automatically
            });
            primaryStage.requestFocus();
        }

        private void showMenuScreen() {
            initializeStockAndShopObjects();
            try {
                System.out.println("Loading Menu.fxml...");
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/example/t/Menu.fxml"));
                javafx.scene.Parent root = loader.load();
                javafx.scene.Scene scene = new javafx.scene.Scene(root);
                scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-windows-theme.css").toExternalForm());
                primaryStage.setScene(scene);
                primaryStage.setTitle("Bookstore Billing - Menu");
                primaryStage.setMaximized(true);
                primaryStage.show();
                System.out.println("Menu.fxml loaded and shown.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        private void openSettingsScreen() {
            // Create a confirmation dialog for opening the settings screen
            Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationDialog.setTitle("Confirmation");
            confirmationDialog.setHeaderText(null);
            confirmationDialog.setContentText("Are you sure you want to change the settings?");

            // Show the confirmation dialog and proceed if the user chooses OK
            Optional<ButtonType> result = confirmationDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                SettingsScreen settingsScreen = new SettingsScreen(password);
                settingsScreen.openSettingsScreen(primaryStage, shop);

                // Add event handler to remove blur effect when the settings screen is closed
                settingsScreen.setOnHidden(event -> {
                    primaryStage.getScene().getRoot().setEffect(null); // Remove the blur effect
                    primaryStage.show(); // Show the main screen
                    if (shop != null) {
                        shop.loadData(); // Reload shop data from file after closing the settings screen
                    }
                });

                settingsScreen.show();
            }
        }
        private void openAddStockScreen() {
            AddStockScreen addStockScreen = new AddStockScreen(stock);
            addStockScreen.show();
        }

        private void openViewStockScreen() {
            ViewStockScreen viewStockScreen = new ViewStockScreen();
            viewStockScreen.show();
        }

        private void openUpdateStockScreen() {
            UpdateStockScreen updateStockScreen = new UpdateStockScreen(); // Create an instance of the UpdateStockScreen
            updateStockScreen.show(); // Show the UpdateStockScreen
        }

        private void openCreateOrderScreen() {
            if (stock != null && shop != null) {  // Check if both stock and shop objects are initialized
                CreateOrderScreen createOrderScreen = new CreateOrderScreen(stock, shop);
                createOrderScreen.show();
            } else {
                Logger.error("Stock or Shop object is not initialized");
            }
        }

        private void openViewOrderScreen() {
            ViewOrderScreen viewOrderScreen = new ViewOrderScreen();
            viewOrderScreen.show();
        }

        private void openViewInvoicesScreen() {
            ViewInvoicesScreen viewInvoicesScreen = new ViewInvoicesScreen();
            viewInvoicesScreen.show();
        }

        private void openPasswordScreen() {
            PasswordScreen passwordScreen = new PasswordScreen(password);
            passwordScreen.setOnPasswordCorrect(this::showMenuScreen);

            // Use the same primaryStage, just reset the scene
            passwordScreen.start(primaryStage);
        }

        private void initializeStockAndShopObjects() {
            stock = new Stock(); // Initialize the Stock object
            shop = new Shop("Shop Name", "Address", "0300 000000"); // Initialize the Shop object with the default values
        }
    }
