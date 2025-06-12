package com.example.t;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
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
import javafx.util.Duration;

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
            passwordScreen.setOnPasswordCorrect(this::showMenuScreen);

            passwordScreen.start(primaryStage);
            primaryStage.setOnShown(event -> primaryStage.setMaximized(true));
            primaryStage.setOnCloseRequest(event -> {
                if (shop != null) {
                    shop.saveData(); // Save shop data to file before exiting
                }
                System.exit(0);
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
                System.exit(0);
            });

            VBox root = new VBox(10);
            root.setAlignment(Pos.TOP_LEFT);
            root.setPrefWidth(300); // Set the preferred width
            root.setPrefHeight(300); // Set the preferred height

            // Create a StackPane for the date components
            StackPane dateContainer = new StackPane();
            dateContainer.setAlignment(Pos.TOP_LEFT);
            dateContainer.setStyle("-fx-background-color: #212121;"); // Example: using light blue

            // Create a Text object to display the current date
            Text dateText = new Text();
            dateText.setFont(Font.font("Arial", FontWeight.BOLD, 28)); // Decrease font size to 28
            dateText.setFill(Color.WHITE);

            // Add the dateText to the dateContainer
            dateContainer.getChildren().add(dateText);

            // Create a StackPane for the digital clock components
            StackPane clockContainer = new StackPane();
            clockContainer.setAlignment(Pos.TOP_LEFT);
            clockContainer.setStyle("-fx-background-color: #212121;"); // Example: using light blue

            // Create a Text object to display the digital clock
            Text digitalClock = new Text();
            digitalClock.setFont(Font.font("Arial", FontWeight.BOLD, 28)); // Decrease font size to 28
            digitalClock.setFill(Color.WHITE);

            // Create fade-in and fade-out animations for the digital clock
            FadeTransition fadeIn = new FadeTransition(Duration.seconds(1), digitalClock);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.setCycleCount(Animation.INDEFINITE);
            fadeIn.setAutoReverse(true);

            FadeTransition fadeOut = new FadeTransition(Duration.seconds(1), digitalClock);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setCycleCount(Animation.INDEFINITE);
            fadeOut.setAutoReverse(true);

            // Update the date and digital clock every second
            Thread dateTimeUpdateThread = new Thread(() -> {
                while (true) {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a"); // Remove the "ss" pattern for seconds
                    String formattedDate = now.format(dateFormatter);
                    String formattedTime = now.format(timeFormatter);

                    // Update the date and digital clock on the JavaFX application thread
                    javafx.application.Platform.runLater(() -> {
                        dateText.setText(formattedDate);
                        digitalClock.setText(formattedTime);
                    });

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });

            dateTimeUpdateThread.setDaemon(true);
            dateTimeUpdateThread.start();


            // Add the digitalClock to the clockContainer
            clockContainer.getChildren().add(digitalClock);

            // Create an HBox for the dateContainer and clockContainer
            HBox dateTimeBox = new HBox(10);
            dateTimeBox.getChildren().addAll(dateContainer, clockContainer);

            // Add settings icon
            Image settingsImage = new Image(FilePathManager.getSettingsIconPath());
            ImageView settingsIcon = new ImageView(settingsImage);
            settingsIcon.setFitWidth(32);
            settingsIcon.setFitHeight(32);
            StackPane.setAlignment(settingsIcon, Pos.BOTTOM_RIGHT);

            // Create a border for the settings icon
            StackPane settingsButton = new StackPane(settingsIcon);
            settingsButton.setStyle("-fx-border-color: transparent; -fx-border-width: 1;");

            // Add a click effect to the settings button
            DropShadow dropShadow = new DropShadow(10, Color.GRAY);
            settingsButton.setOnMousePressed(event -> settingsButton.setEffect(dropShadow));
            settingsButton.setOnMouseReleased(event -> settingsButton.setEffect(null));
            settingsButton.setOnMouseEntered(event -> settingsButton.setEffect(dropShadow));
            settingsButton.setOnMouseExited(event -> settingsButton.setEffect(null));

            settingsButton.setOnMouseClicked(event -> openSettingsScreen()); // Open the settings screen when clicked

            StackPane settingsContainer = new StackPane(settingsButton);
            settingsContainer.setAlignment(Pos.BOTTOM_RIGHT);
            settingsContainer.setPadding(new Insets(180, 10, 8, 4)); // Adjust the paddings as needed

            // Create a VBox for the menu buttons
            VBox buttonsContainer = new VBox(10);
            buttonsContainer.setAlignment(Pos.CENTER);
            buttonsContainer.setPrefWidth(300);
            buttonsContainer.setFillWidth(true);
            // Set padding/margin from the top
            int topPadding = 260; // Adjust the value as needed
            buttonsContainer.setStyle("-fx-padding: " + topPadding + " 80 0 0;");

            Button addStockButton = new Button("Add Stock");
            addStockButton.setPrefWidth(120);
            addStockButton.setOnAction(event -> openAddStockScreen());

            Button viewStockButton = new Button("View Stock");
            viewStockButton.setPrefWidth(120);
            viewStockButton.setOnAction(event -> openViewStockScreen());

            Button updateStockButton = new Button("Update Stock"); // New button for updating stock
            updateStockButton.setPrefWidth(120);
            updateStockButton.setOnAction(event -> openUpdateStockScreen());

            Button createOrderButton = new Button("Create Order");
            createOrderButton.setPrefWidth(120);
            createOrderButton.setOnAction(event -> openCreateOrderScreen());

            Button viewOrderButton = new Button("View Order");
            viewOrderButton.setPrefWidth(120);
            viewOrderButton.setOnAction(event -> openViewOrderScreen());

            Button viewInvoicesButton = new Button("View Invoices");
            viewInvoicesButton.setPrefWidth(120);
            viewInvoicesButton.setOnAction(event -> openViewInvoicesScreen());

            Button exitButton = new Button("Logout");
            exitButton.setPrefWidth(120);
            exitButton.setOnAction(event -> {
                if (shop != null) {
                    shop.saveData(); // Save shop data to file before logging out
                }
                openPasswordScreen();
            });

            buttonsContainer.getChildren().addAll(
                    addStockButton, viewStockButton, updateStockButton, // Add the updateStockButton
                    createOrderButton, viewOrderButton, viewInvoicesButton, exitButton
            );

            // Set the background image
            Image backgroundImage = new Image(FilePathManager.getBackgroundImagePath());
            BackgroundImage background = new BackgroundImage(
                    backgroundImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, new BackgroundSize(1.0, 1.0, true, true, false, false)
            );

            StackPane.setMargin(settingsContainer, new Insets(0)); // Adjust the margins as needed
            StackPane.setAlignment(settingsContainer, Pos.BOTTOM_RIGHT);
            StackPane stackPane = new StackPane();
            stackPane.getChildren().addAll(settingsContainer);
            root.getChildren().addAll(dateTimeBox, buttonsContainer, stackPane);
            root.setBackground(new Background(background));

            double screenWidth = Screen.getPrimary().getBounds().getWidth();
            double screenHeight = Screen.getPrimary().getBounds().getHeight();

            Scene scene = new Scene(root, screenWidth, screenHeight);
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
                System.out.println("Stock or Shop object is not initialized");
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

            Stage passwordStage = new Stage();
            passwordScreen.start(passwordStage);
            primaryStage.close();
        }

        private void initializeStockAndShopObjects() {
            stock = new Stock(); // Initialize the Stock object
            shop = new Shop("Shop Name", "Address", "0300 000000"); // Initialize the Shop object with the default values
        }
    }
