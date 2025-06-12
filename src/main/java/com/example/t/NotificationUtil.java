package com.example.t;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class NotificationUtil {
    public static void showToast(Stage owner, String message) {
        Popup popup = new Popup();
        Label label = new Label(message);
        label.setStyle("-fx-background-color: #323232; -fx-text-fill: white; -fx-padding: 12 24; -fx-background-radius: 8; -fx-font-size: 15px;");
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color: transparent;");
        pane.setAlignment(Pos.BOTTOM_CENTER);
        popup.getContent().add(pane);
        popup.setAutoHide(true);
        popup.show(owner);

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2.5), ae -> popup.hide()));
        timeline.play();
    }

    public static void showToast(Scene scene, String message) {
        Stage stage = (Stage) scene.getWindow();
        showToast(stage, message);
    }
}
