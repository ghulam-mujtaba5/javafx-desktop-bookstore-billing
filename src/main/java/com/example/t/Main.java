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
                showMenuScreen();
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
            initializeStockAndShopObjects(); // Initialize the stock and shop objects

            primaryStage.setTitle("Menu");
            primaryStage.setOnCloseRequest(event -> {
                if (shop != null) {
                    shop.saveData(); // Save shop data to file before exiting
                }
                // System.exit(0); // Remove this to prevent app from closing automatically
            });

            StackPane root = new StackPane();
            // Background image is set below

            VBox overlay = new VBox(30);
            overlay.setAlignment(Pos.CENTER);
            overlay.setMaxWidth(400);
            overlay.setStyle("-fx-background-color: rgba(255,255,255,0.85); -fx-background-radius: 18px; -fx-padding: 40 40 40 40;");

            // Date and time at the top
            HBox dateTimeBox = new HBox(20);
            dateTimeBox.setAlignment(Pos.CENTER);

            Text dateText = new Text();
            dateText.getStyleClass().add("label");
            dateText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));

            Text digitalClock = new Text();
            digitalClock.getStyleClass().add("label");
            digitalClock.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));

            dateTimeBox.getChildren().addAll(dateText, digitalClock);

            // Update the date and digital clock every second
            Thread dateTimeUpdateThread = new Thread(() -> {
                while (true) {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
                    String formattedDate = now.format(dateFormatter);
                    String formattedTime = now.format(timeFormatter);

                    javafx.application.Platform.runLater(() -> {
                        dateText.setText(formattedDate);
                        digitalClock.setText(formattedTime);
                    });

                    // Use JavaFX PauseTransition instead of Thread.sleep
                    try {
                        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1));
                        final boolean[] finished = {false};
                        pause.setOnFinished(event -> finished[0] = true);
                        pause.play();
                        while (!finished[0]) {
                            Thread.onSpinWait();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            dateTimeUpdateThread.setDaemon(true);
            dateTimeUpdateThread.start();

            // Add settings icon (bottom right floating button)
            Image settingsImage = new Image(FilePathManager.getSettingsIconPath());
            ImageView settingsIcon = new ImageView(settingsImage);
            settingsIcon.setFitWidth(32);
            settingsIcon.setFitHeight(32);
            StackPane.setAlignment(settingsIcon, Pos.BOTTOM_RIGHT);
            StackPane settingsButton = new StackPane(settingsIcon);
            settingsButton.setStyle("-fx-border-color: transparent; -fx-border-width: 1;");
            DropShadow dropShadow = new DropShadow(10, Color.GRAY);
            settingsButton.setOnMousePressed(event -> settingsButton.setEffect(dropShadow));
            settingsButton.setOnMouseReleased(event -> settingsButton.setEffect(null));
            settingsButton.setOnMouseEntered(event -> settingsButton.setEffect(dropShadow));
            settingsButton.setOnMouseExited(event -> settingsButton.setEffect(null));
            settingsButton.setOnMouseClicked(event -> openSettingsScreen());

            StackPane settingsContainer = new StackPane(settingsButton);
            settingsContainer.setAlignment(Pos.BOTTOM_RIGHT);
            settingsContainer.setPadding(new Insets(0, 30, 30, 0));

            VBox buttonsContainer = new VBox(18);
            buttonsContainer.setAlignment(Pos.CENTER);
            buttonsContainer.setFillWidth(true);

            String[] buttonNames = {"Add Stock", "View Stock", "Update Stock", "Create Order", "View Order", "View Invoices", "Logout"};
            Runnable[] actions = {
                this::openAddStockScreen,
                this::openViewStockScreen,
                this::openUpdateStockScreen,
                this::openCreateOrderScreen,
                this::openViewOrderScreen,
                this::openViewInvoicesScreen,
                () -> {
                    if (shop != null) shop.saveData();
                    openPasswordScreen();
                }
            };
            for (int i = 0; i < buttonNames.length; i++) {
                Button btn = new Button(buttonNames[i]);
                btn.setPrefWidth(220);
                btn.setPrefHeight(40);
                btn.setStyle("-fx-font-size: 16px;");
                int idx = i;
                btn.setOnAction(e -> actions[idx].run());
                buttonsContainer.getChildren().add(btn);
            }

            // Set the background image
            Image backgroundImage = new Image(FilePathManager.getBackgroundImagePath());
            BackgroundImage background = new BackgroundImage(
                    backgroundImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, new BackgroundSize(1.0, 1.0, true, true, false, false)
            );
            root.setBackground(new Background(background));

            overlay.getChildren().addAll(dateTimeBox, buttonsContainer);
            root.getChildren().add(overlay);

            double screenWidth = Screen.getPrimary().getBounds().getWidth();
            double screenHeight = Screen.getPrimary().getBounds().getHeight();

            Scene scene = new Scene(root, screenWidth, screenHeight);
            scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
            primaryStage.setMaximized(true);
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.show();
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
