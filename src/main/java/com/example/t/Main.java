package com.example.t;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


    public class Main extends Application {
        private Stage primaryStage;
        private Shop shop;
        // private Stock stock;
        private Password password;

        public static void main(String[] args) {
            launch(args);
        }

        @Override
        public void start(Stage primaryStage) {
            this.primaryStage = primaryStage;
            setupPrimaryStage();
            
            password = new Password(); // Initialize the password object

            PasswordScreen passwordScreen = new PasswordScreen(password);
            passwordScreen.setOnPasswordCorrect(() -> {
                System.out.println("Password correct, loading menu...");
                showMenuScreen();
                System.out.println("Menu loaded!");
                primaryStage.show();
            });

            passwordScreen.start(primaryStage);
        }

        private void setupPrimaryStage() {
            primaryStage.setTitle("Bookstore Billing System");
            primaryStage.initStyle(StageStyle.UNIFIED);
            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(768);
            primaryStage.setMaximized(true);

            // Add window icons
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/com/example/t/icon.ico")));
            } catch (Exception e) {
                System.err.println("Could not load application icon");
            }

            primaryStage.setOnCloseRequest(event -> {
                if (shop != null) {
                    shop.saveData(); // Save shop data to file before exiting
                }
            });
        }

        private void showMenuScreen() {
            initializeStockAndShopObjects();
            try {
                System.out.println("Loading Menu.fxml...");
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/example/t/Menu.fxml"));
                javafx.scene.Parent root = loader.load();
                javafx.scene.Scene scene = new javafx.scene.Scene(root);
                
                // Apply both themes
                scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme-new.css").toExternalForm());
                scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-windows-theme.css").toExternalForm());
                
                primaryStage.setScene(scene);
                primaryStage.setTitle("Bookstore Billing - Menu");
                primaryStage.show();
                System.out.println("Menu.fxml loaded and shown.");
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Error Loading Menu");
                    alert.setHeaderText("Could not load the main menu screen");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }

        // Removed unused methods

        private void initializeStockAndShopObjects() {
            // stock = new Stock(); // Removed unused Stock object
            shop = new Shop("Shop Name", "Address", "0300 000000"); // Initialize the Shop object with the default values
        }
    }
