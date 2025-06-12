
package com.example.t;

import javafx.application.Application;
// import javafx.scene.control.Alert;
// import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
// import java.util.Optional;


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

        // Removed unused methods

        private void initializeStockAndShopObjects() {
            stock = new Stock(); // Initialize the Stock object
            shop = new Shop("Shop Name", "Address", "0300 000000"); // Initialize the Shop object with the default values
        }
    }
