package com.example.t;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;

public class NotificationUtil {
    private static final Duration ANIMATION_DURATION = Duration.seconds(0.3);
    private static final Duration DISPLAY_DURATION = Duration.seconds(3);
    
    public static void showSuccess(String title, String message) {
        showNotification(title, message, "#28a745"); // Bootstrap success green
    }
    
    public static void showError(String title, String message) {
        showNotification(title, message, "#dc3545"); // Bootstrap danger red
    }
    
    public static void showInfo(String title, String message) {
        showNotification(title, message, "#17a2b8"); // Bootstrap info blue
    }
    
    public static void showWarning(String title, String message) {
        showNotification(title, message, "#ffc107"); // Bootstrap warning yellow
    }

    private static void showNotification(String title, String message, String colorHex) {
        Stage notificationStage = new Stage(StageStyle.TRANSPARENT);
        
        // Create the notification content
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: white;");
        
        // Create an animated status indicator
        Circle statusDot = new Circle(4);
        statusDot.setFill(Color.WHITE);
        Timeline pulseAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(statusDot.radiusProperty(), 4)),
            new KeyFrame(Duration.seconds(1), new KeyValue(statusDot.radiusProperty(), 6)),
            new KeyFrame(Duration.seconds(2), new KeyValue(statusDot.radiusProperty(), 4))
        );
        pulseAnimation.setCycleCount(Timeline.INDEFINITE);
        pulseAnimation.play();
        
        // Layout
        HBox contentBox = new HBox(10);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.getChildren().addAll(statusDot, titleLabel, messageLabel);
        
        StackPane container = new StackPane(contentBox);
        container.setStyle(
            "-fx-background-color: " + colorHex + ";" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 10 20 10 20;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0.0, 0.0);"
        );

        Scene scene = new Scene(container);
        scene.setFill(null);
        notificationStage.setScene(scene);

        // Position the notification at the top-right corner
        notificationStage.setAlwaysOnTop(true);
        notificationStage.show();
        
        // Calculate position (top-right of the primary screen)
        double screenX = javafx.stage.Screen.getPrimary().getVisualBounds().getMaxX() - container.getWidth() - 20;
        double screenY = javafx.stage.Screen.getPrimary().getVisualBounds().getMinY() + 40;
        
        notificationStage.setX(screenX);
        notificationStage.setY(screenY);

        // Fade in animation
        container.setOpacity(0);
        Timeline fadeIn = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(container.opacityProperty(), 0)),
            new KeyFrame(ANIMATION_DURATION, new KeyValue(container.opacityProperty(), 1))
        );
        fadeIn.play();

        // Schedule fade out
        Timeline fadeOut = new Timeline(
            new KeyFrame(DISPLAY_DURATION, new KeyValue(container.opacityProperty(), 1)),
            new KeyFrame(DISPLAY_DURATION.add(ANIMATION_DURATION), new KeyValue(container.opacityProperty(), 0))
        );
        fadeOut.setOnFinished(e -> notificationStage.close());
        fadeOut.play();
    }
}
