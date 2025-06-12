package com.example.t;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class PasswordScreen extends Application {

    private final Password password;
    private Runnable onPasswordCorrect;

    public PasswordScreen(Password password) {
        this.password = password;
    }

    public void setOnPasswordCorrect(Runnable onPasswordCorrect) {
        this.onPasswordCorrect = onPasswordCorrect;
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);

        Label titleLabel = new Label("Enter Password");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        Button enterButton = new Button("Enter");

        enterButton.setOnAction(event -> {
            String enteredPassword = passwordField.getText();
            if (password.isCorrect(enteredPassword)) {
                showAlert("Success", "Password correct. Welcome!");
                if (onPasswordCorrect != null) {
                    onPasswordCorrect.run();
                }
                // primaryStage.hide(); // REMOVE this line to keep the main window open
            } else {
                showAlert("Invalid password", "The entered password is incorrect. Please try again.");
                passwordField.clear();
            }
        });

        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String enteredPassword = passwordField.getText();
                if (password.isCorrect(enteredPassword)) {
                    showAlert("Success", "Password correct. Welcome!");
                    if (onPasswordCorrect != null) {
                        onPasswordCorrect.run();
                    }
                    // primaryStage.hide(); // REMOVE this line to keep the main window open
                } else {
                    showAlert("Invalid password", "The entered password is incorrect. Please try again.");
                    passwordField.clear();
                }
            }
        });

        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.TOP_LEFT);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        gridPane.add(titleLabel, 0, 0, 2, 1);
        gridPane.add(passwordLabel, 0, 2);
        gridPane.add(passwordField, 1, 2);
        gridPane.add(enterButton, 1, 3);

        StackPane root = new StackPane(gridPane);

        Image backgroundImage = new Image(FilePathManager.getBackgroundImagePath());
        BackgroundImage background = new BackgroundImage(backgroundImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, new BackgroundSize(1.0, 1.0, true, true, false, false));
        root.setBackground(new Background(background));

        double screenWidth = Screen.getPrimary().getBounds().getWidth() - 200;
        double screenHeight = Screen.getPrimary().getBounds().getHeight() - 200;

        Scene scene = new Scene(root, screenWidth, screenHeight);
        scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
        primaryStage.setScene(scene);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                primaryStage.setWidth(300);
                primaryStage.setHeight(200);
                primaryStage.centerOnScreen();
            }
        });

        primaryStage.setOnCloseRequest(event -> {
            // You can handle any additional actions before closing the application here
        });

        primaryStage.show();
    }

    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
